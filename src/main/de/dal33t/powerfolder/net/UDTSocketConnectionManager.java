package de.dal33t.powerfolder.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.StringUtils;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.message.UDTMessage;
import de.dal33t.powerfolder.message.UDTMessage.Type;
import de.dal33t.powerfolder.util.Partitions;
import de.dal33t.powerfolder.util.Range;
import de.dal33t.powerfolder.util.net.NetworkUtil;
import de.dal33t.powerfolder.util.net.UDTSocket;

/**
 * Listens for incoming UDTMessages and either
 * 1) Processes them if the destination is this client or
 * 2) Sends the messages to the destination if possible
 *  
 * Establishes connections thru UDT sockets. Like with TCP every connection requires it's own port!
 *  
 * @author Dennis "Bytekeeper" Waldherr
 *
 */
public class UDTSocketConnectionManager extends PFComponent {
	private Partitions<PortSlot> ports;
	
	/**
	 * Stores received replies. ReplyMonitors are used for locking and waiting purposes.
	 * Only one Thread needs to be notified, the one waiting for the reply. 
	 */
	private ConcurrentMap<MemberInfo, ReplyMonitor> replies = new ConcurrentHashMap<MemberInfo, ReplyMonitor>();
	
	/**
	 * Constructs a manager for establishing UDT connections.
	 * @param controller
	 * @param portRange the range of possible ports
	 */
	public UDTSocketConnectionManager(Controller controller, Range portRange) {
		super(controller);
		ports = new Partitions<PortSlot>(portRange, null);
	}
	
	/**
	 * Creates and initializes an UDT connection.
	 * The means used for this are similar to that of the RelayedConnectionManager, in that a 
	 * special message is relayed to the target client.
	 * @param destination the destination member to connect to
	 * @return on successfully establishing a connection, null otherwise 
	 * @throws ConnectionException
	 */
	public ConnectionHandler initUDTConnectionHandler(MemberInfo destination) throws ConnectionException {
	    if (!UDTSocket.isSupported()) {
	        throw new ConnectionException("Missing UDT support!");
	    }
        if (getController().getMySelf().getInfo().equals(destination)) {
            throw new ConnectionException(
                "Illegal relayed loopback connection detection to myself");
        }
        
        // Use relay for UDT stuff too
        Member relay = getController().getIOProvider()
        	.getRelayedConnectionManager().getRelay();
        
        if (relay == null) {
            throw new ConnectionException(
                "Unable to open relayed connection to " + destination
                    + ". No relay found!");
        }
        PortSlot slot = selectPortFor(destination);
        if (slot == null) {
        	throw new ConnectionException("UDT port selection failed!");
        }
        UDTMessage syn = new UDTMessage(Type.SYN, getController().getMySelf().getInfo(),
        		destination, slot.port);
        try {
            try {
                ReplyMonitor repMonitor = new ReplyMonitor();
                if (replies.putIfAbsent(destination, repMonitor) != null) {
                    throw new ConnectionException("Already trying to establish connection to " + destination);
                }
                relay.sendMessage(syn);
    
    	        UDTMessage reply = waitForReply(repMonitor, destination);
    	        switch (reply.getType()) {
    	            case ACK:
    	                log().debug("UDT SYN: Trying to connect...");
    	                ConnectionHandler handler = getController()
    	                    .getIOProvider()
    	                    .getConnectionHandlerFactory()
    	                    .createUDTSocketConnectionHandler(getController(), slot.socket, reply.getSource(), reply.getPort());
    	                log().debug("UDT SYN: Successfully connected!");
    	                return handler;
    	            case NACK:
    	                throw new ConnectionException("Connection not possible: " + reply);
                    default:
                        log().debug("UDT SYN: Received invalid reply:" + reply);
                        throw new ConnectionException("Invalid reply: " + reply);
    	        }
    		} catch (TimeoutException e) {
    			log().verbose(e);
    	        throw new ConnectionException(e);
    		} catch (InterruptedException e) {
                log().verbose(e);
                throw new ConnectionException(e);
    	    } 
        } catch (ConnectionException e) {
	        // If we failed, release the slot
	        releaseSlot(slot.port);

	        // Don't wait for the GC to collect the socket, close it immediately
            if (slot.socket != null && !slot.socket.isClosed()) {
                try {
                    slot.socket.close();
                } catch (IOException e1) {
                    log().error(e1);
                }
            }
	        throw e;
		}
	}

	private UDTMessage waitForReply(ReplyMonitor monitor, MemberInfo destination) throws TimeoutException, InterruptedException {
	    synchronized (monitor) {
	        try {
    	        // Check if we already got a reply
    	        if (monitor.msg != null) {
    	            return monitor.msg;
    	        }
    	        monitor.wait(Constants.TO_UDT_CONNECTION);
    	        if (monitor.msg != null) {
    	            return monitor.msg;
    	        }
                throw new TimeoutException();
	        } finally {
	            log().debug("waitForReply remaining entries: " + replies.size());
	            // Always remove the entry
	            replies.remove(destination);
	        }
	    } 
	}

	/**
	 * Handles UDT messages.
	 * Based on the content of the message it might get relayed if the destination != mySelf or
	 * processed otherwise. 
	 * @param sender the Member who sent the message
	 * @param msg the message
	 */
	public void handleUDTMessage(final Member sender, final UDTMessage msg) {
		// Are we targeted ?
		if (msg.getDestination().getNode(getController()).isMySelf()) {
            log().debug("Received UDT message for me: " + msg);
            log().debug("Replies: " + replies.size());
		    if (!UDTSocket.isSupported()) {
	            log().warn("UDT sockets not supported on this platform.");
		        return;
		    }
			switch (msg.getType()) {
			case SYN:
			    // Check if we allow NAT traversal
			    if (ConfigurationEntry.UDT_CONNECTIONS_ENABLED.getValueBoolean(getController())) {
    				getController().getIOProvider().startIO(
    						new Runnable() {
    							public void run() {
    								Member relay = getController().getIOProvider()
    							 		.getRelayedConnectionManager().getRelay();
    								if (relay == null) {
    									log().error("Relay is null!");
    									return;
    								}
    								PortSlot slot = selectPortFor(sender.getInfo());
    								if (slot == null) {
    									log().error("UDT port selection failed.");
    									try {
    										sender.sendMessage(
    											new UDTMessage(Type.NACK, getController().getMySelf().getInfo(),
    													msg.getSource(), -1));
    									} catch (ConnectionException e) {
    										log().error(e);
    									}
    									return;
    								}
    								try {
    									relay.sendMessage(new UDTMessage(Type.ACK, getController().getMySelf().getInfo(),
    											msg.getSource(), slot.port));
    									ConnectionHandler handler = null;
    									try {
    							        	log().debug("UDT ACK: Trying to connect...");
    										handler = getController().getIOProvider().getConnectionHandlerFactory()
    											.createUDTSocketConnectionHandler(getController(), slot.socket, 
    												msg.getSource(), msg.getPort());
    			                            getController().getNodeManager().acceptConnection(
    			                            		handler);
    			            		        log().debug("UDT ACK: Successfully connected!");
    									} catch (ConnectionException e) {
    										if (handler != null) 
    											handler.shutdown();
    										throw e;
    									}
    								} catch (ConnectionException e) {
    						            // If we failed, release the slot
    						            releaseSlot(slot.port);
    						            
    								    // Don't wait for the GC to collect the socket, close it immediately
    								    if (slot.socket != null && !slot.socket.isClosed()) {
    								        try {
                                                slot.socket.close();
                                            } catch (IOException e1) {
                                                log().error(e1);
                                            }
    								    }
    									log().error(e);
    								}
    							}
    						});
			    } else {
			        UDTMessage nack = new UDTMessage(Type.NACK, getController().getMySelf().getInfo(),
			            sender.getInfo(), -1);
                    Member relay = getController().getIOProvider()
                        .getRelayedConnectionManager().getRelay();
                    if (relay == null) {
                        log().error("Relay is null!");
                        return;
                    }
                    // Try to send NACK, if it doesn't work - we don't care, it'll timeout remotely.
			        relay.sendMessagesAsynchron(nack);
			    }
				break;
			case ACK:
			case NACK:
			    ReplyMonitor repMon = replies.get(msg.getSource());
			    if (repMon == null) {
			        log().error("Received a reply for " + msg.getSource() + ", although no connection was requested!");
			        break;
			    }
				synchronized (repMon) {
				    if (repMon.msg != null) {
				        log().error("Relay message error: Received more than one SYN reply!");
				        // If that happens, let's hope the "newer" message is the "better".
				    }
				    repMon.msg = msg;
				    repMon.notify();
				}
				break;
			}
		} else {
			log().verbose("Relaying UDT message: " + msg);
			// Relay message
			Member dMember = msg.getDestination().getNode(getController());
			if (dMember == null || !dMember.isCompleteyConnected()) {
				UDTMessage failedMsg = new UDTMessage(Type.NACK, msg.getDestination(), 
						msg.getSource(), -1);
				sender.sendMessagesAsynchron(failedMsg);
				return;
			}
			dMember.sendMessagesAsynchron(msg);
		}
	}
	
	/**
	 * Returns a port to use for the given destination.
	 * Each connection requires it's own port on both sides.
	 * The returned slot contains an UDTSocket which is already bound to the selected port.
	 * @param destination
	 * @return
	 */
	public PortSlot selectPortFor(MemberInfo destination) {
		Range res = null;
		// Try to bind port now to avoid surprises later
		PortSlot slot = new PortSlot(destination);
		slot.socket = new UDTSocket();
		try {
            NetworkUtil.setupSocket(slot.socket, destination.getConnectAddress());
        } catch (IOException e1) {
            log().error(e1);
        }
        while (true) {
			synchronized (this) {
				res = ports.search(ports.getPartionedRange(), null);
			}
			if (res == null) {
				log().error("No further usable ports for UDT connections!");
				try {
					slot.socket.close();
				} catch (IOException e) {
					log().error(e);
				}
				return null;
			}
			slot.port = (int) res.getStart();
			try {
			    String cfgBindAddr = ConfigurationEntry.NET_BIND_ADDRESS.getValue(getController());
			    InetSocketAddress bindAddr;
			    if (!StringUtils.isEmpty(cfgBindAddr)) {
			        bindAddr = new InetSocketAddress(cfgBindAddr, slot.port);
			    } else {
			        bindAddr = new InetSocketAddress(slot.port);
			    }
				slot.socket.bind(bindAddr);
				break;
			} catch (IOException e) {
				log().verbose(e);
				
				ports.insert(Range.getRangeByNumbers(res.getStart(), res.getStart()),
						PortSlot.LOCKED);
			}
		}
		ports.insert(Range.getRangeByNumbers(res.getStart(), res.getStart()),
				slot);
		return slot;
	}
	
	/**
	 * Frees a slot taken by selectPortFor
	 * @param port the port slot to free
	 */
	public synchronized void releaseSlot(int port) {
		ports.insert(Range.getRangeByLength(port, 1), null);
	}

	/**
	 * Simple class representing a port, a socket and the target member.
	 */
	public static class PortSlot {
		/**  If a port is locked - it's pretty much dead (unusable for PF) */
		public static final PortSlot LOCKED = new PortSlot(); 

		private MemberInfo member;
		private UDTSocket socket;
		private int port;

		public PortSlot(MemberInfo destination) {
			member = destination;
		}
		
		private PortSlot() {
		}

		public MemberInfo getMember() {
			return member;
		}

		public UDTSocket getSocket() {
			return socket;
		}

		public int getPort() {
			return port;
		}
	}
	
	private static class ReplyMonitor {
	    public UDTMessage msg;
	}
}
