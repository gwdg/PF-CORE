/* $Id: RConManager.java,v 1.10 2006/04/29 08:35:14 schaatser Exp $
 */
package de.dal33t.powerfolder;

import java.awt.Frame;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;

import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.message.Invitation;
import de.dal33t.powerfolder.ui.dialog.FolderCreatePanel;
import de.dal33t.powerfolder.util.InvitationUtil;
import de.dal33t.powerfolder.util.Logger;
import de.dal33t.powerfolder.util.Util;

/**
 * The remote command processor is responsible for binding on a socket and
 * receive and process any remote control commands. e.g. Load invitation file,
 * process powerfolder links or exit powerfolder.
 * <p>
 * Supported links:
 * <p>
 * <code>
 * Folder links:
 * PowerFolder://|folder|<foldername>|<P or S>|<folderid>|<size>|<numFiles>
 * <P or S> P = public, S = secret
 * PowerFolder://|folder|test|S|[test-AAgwZXFLgigj222]|99900000|1000
 * 
 * File links:
 * PowerFolder://|file|<foldername>|<P or S>|<folderid>|<fullpath_filename>
 * <P or S> P = public, S = secret
 * PowerFolder://|folder|test|S|[test-AAgwZXFLgigj222]|/test/New_text_docuement.txt
 * </code>
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.10 $
 */
public class RemoteCommandManager extends PFComponent implements Runnable {
    // The logger
    private static final Logger LOG = Logger
        .getLogger(RemoteCommandManager.class);
    // The default port to listen for remote commands
    private static final int DEFAULT_REMOTECOMMAND_PORT = 1338;
    // The default prefix for all rcon commands
    private static final String REMOTECOMMAND_PREFIX = "PowerFolder_RCON_COMMAND";
    // The default encoding
    private static final String ENCODING = "UTF8";
    // The prefix for pf links
    private static final String POWERFOLDER_LINK_PREFIX = "powerfolder://";

    // All possible commands
    public static final String QUIT = "QUIT";
    public static final String OPEN = "OPEN;";
    public static final String MAKEFOLDER = "MAKEFOLDER;"; 

    // Private vars
    private ServerSocket serverSocket;
    private Thread myThread;

   
    /**
     * Initalization
     * 
     * @param controller
     */
    public RemoteCommandManager(Controller controller) {
        super(controller);
    }
   
    /**
     * Checks if there is a running instance of RemoteComamndManager. Determains
     * this by opening a server socket port on the DEFAULT_REMOTECOMMAND_PORT.
     * 
     * @return true if port allready taken
     */
    public static boolean hasRunningInstance() {
        ServerSocket testSocket = null;
        try {
            // Only bind to localhost
            testSocket = new ServerSocket(DEFAULT_REMOTECOMMAND_PORT, 0,
                InetAddress.getByName("127.0.0.1"));

            // Server socket can be opend, no instance of PowerFolder running
            return false;
        } catch (UnknownHostException e) {
        } catch (IOException e) {
        } finally {
            if (testSocket != null) {
                try {
                    testSocket.close();
                } catch (IOException e) {
                    LOG.error("Unable to close already running test socket. "
                        + testSocket, e);
                }
            }
        }
        return true;
    }

    /**
     * Sends a remote command to a running instance of PowerFolder
     * 
     * @param command
     *            the command
     * @return true if succeeded, otherwise false
     */
    public static boolean sendCommand(String command) {
        try {
            LOG.debug("Sending remote command '" + command + "'");
            Socket socket = new Socket("127.0.0.1", 1338);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket
                .getOutputStream(), ENCODING));

            writer.println(REMOTECOMMAND_PREFIX + ";" + command);
            writer.flush();
            writer.close();
            socket.close();

            return true;
        } catch (IOException e) {
            LOG.error("Unable to send remote command", e);
        }
        return false;
    }

    /**
     * Starts the remote command processor
     */
    public void start() {
        try {
            // Only bind to localhost
            serverSocket = new ServerSocket(DEFAULT_REMOTECOMMAND_PORT, 0,
                InetAddress.getByName("127.0.0.1"));

            // Start thread
            myThread = new Thread(this, "Remote command Manager");
            myThread.start();
        } catch (UnknownHostException e) {
            log().warn(
                "Unable to open remote command manager on port "
                    + DEFAULT_REMOTECOMMAND_PORT, e);
        } catch (IOException e) {
            log().warn(
                "Unable to open remote command manager on port "
                    + DEFAULT_REMOTECOMMAND_PORT, e);
        }
    }

    /**
     * Shuts down the rcon manager
     */
    public void shutdown() {
        if (myThread != null) {
            myThread.interrupt();
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log().verbose("Unable to close rcon socket", e);
            }
        }
    }

    public void run() {
        log().info(
            "Listening for remote commands on port "
                + serverSocket.getLocalPort());
        while (!Thread.currentThread().isInterrupted()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                log().verbose("Rcon socket closed, stopping", e);
                break;
            }

            log().verbose("Remote command from " + socket);
            try {
                String address = socket.getInetAddress().getHostAddress();
                if (address.equals("localhost") || address.equals("127.0.0.1"))
                {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), ENCODING));
                    String line = reader.readLine();
                    if (line.startsWith(REMOTECOMMAND_PREFIX)) {
                        processCommand(line.substring(REMOTECOMMAND_PREFIX
                            .length() + 1));
                    }
                }
                socket.close();
            } catch (IOException e) {
                log().warn("Problems parsing remote command from " + socket);
            }
        }
    }

    /**
     * Processes a remote command
     * 
     * @param command
     */
    private void processCommand(String command) {
        if (StringUtils.isBlank(command)) {
            log().error("Received a empty remote command");
            return;
        }
        log().debug("Received remote command: '" + command + "'");
        if (QUIT.equalsIgnoreCase(command)) {
            getController().exit(0);
        } else if (command.startsWith(OPEN)) {
            // Open files
            String fileStr = command.substring(OPEN.length());

            // Open all files in remote command
            StringTokenizer nizer = new StringTokenizer(fileStr, ";");
            while (nizer.hasMoreTokens()) {
                String token = nizer.nextToken();
                if (token.toLowerCase().startsWith(POWERFOLDER_LINK_PREFIX)) {
                    // We got a link
                    openLink(token);
                } else {
                    // Must be a file
                    File file = new File(token);
                    openFile(file);
                }

            }
        } else if (command.startsWith(MAKEFOLDER)) {
        	String folders = command.substring(MAKEFOLDER.length());
            if (getController().isUIOpen()) {
                // Popup application
                getController().getUIController().getMainFrame()
                    .getUIComponent().setVisible(true);
                getController().getUIController().getMainFrame()
                    .getUIComponent().setExtendedState(Frame.NORMAL);
            }
        	for (String s: folders.split(";")) {
        		makeFolder(s);
        	}
        } else {
            log().warn("Remote command not recognizable '" + command + "'");
        }
    }

    /**
     * Opens a powerfolder link and executes it
     * 
     * @param link
     */
    private void openLink(String link) {
        String plainLink = link.substring(POWERFOLDER_LINK_PREFIX.length());
        log().warn("Got plain link: " + plainLink);

        // Chop off ending /
        if (plainLink.endsWith("/")) {
            plainLink = plainLink.substring(1, plainLink.length() - 1);
        }

        try {
            // Parse link
            StringTokenizer nizer = new StringTokenizer(plainLink, "|");
            // Get type
            String type = nizer.nextToken();

            if ("folder".equalsIgnoreCase(type)) {
                Invitation invitation = Invitation.fromPowerFolderLink(link);
                if (invitation != null) {
                    getController().getFolderRepository().invitationReceived(
                        invitation, false, true);
                } else {
                    log().error("Unable to parse powerfolder link: " + link);
                }

            } else if ("file".equalsIgnoreCase(type)) {
                // Decode the url form
                String name = Util.decodeFromURL(nizer.nextToken());
                boolean secret = nizer.nextToken().equalsIgnoreCase("s");
                String id = Util.decodeFromURL(nizer.nextToken());
                FolderInfo folder = new FolderInfo(name, id, secret);

                String filename = Util.decodeFromURL(nizer.nextToken());
                FileInfo fInfo = new FileInfo(folder, filename);

                // FIXME: Show warning/join panel if not on folder

                // Enqueue for download
                getController().getTransferManager().downloadNewestVersion(
                    fInfo);
            }
        } catch (NoSuchElementException e) {
            log().error("Illegal link '" + link + "'");
        }
    }

    /**
     * Opens a file and processes its content
     * 
     * @param file
     */
    private void openFile(File file) {
        if (!file.exists()) {
            log().warn("File not found " + file.getAbsolutePath());
            return;
        }

        if (file.getName().endsWith(".invitation")) {
            // Load invitation file
            Invitation invitation = InvitationUtil.load(file);
            if (invitation != null) {
                getController().getFolderRepository().invitationReceived(
                    invitation, false, true);
            }
        } else if (file.getName().endsWith(".nodes")) {
            // Load nodes file
            MemberInfo[] nodes = loadNodesFile(file);
            // Enqueue new nodes
            if (nodes != null) {
                getController().getNodeManager().queueNewNodes(nodes);
            }
        }
    }

    /**
     * "Converts" the given folder to a PowerFolder.
     * Currently only GUI is supported and a FolderCreationPanel is used.
     * @param folder the name of the folder
     */
    private void makeFolder(String folder) {
    	if (getController().isUIEnabled()) {
    		new FolderCreatePanel(getController(), folder).open();
    	} else {
    		log().warn("Remote creation of folders in non-gui mode is not supported yet.");
    	}
    }
    
    /**
     * Tries to load a list of nodes from a nodes file. Returns null if wasn't
     * able to read the file
     * 
     * @param file
     *            The file to load from
     * @return array of MemberInfo, null if failed
     */
    private MemberInfo[] loadNodesFile(File file) {
        try {
            InputStream fIn = new BufferedInputStream(new FileInputStream(file));
            ObjectInputStream oIn = new ObjectInputStream(fIn);
            // Load nodes
            List nodes = (List) oIn.readObject();

            log().warn("Loaded " + nodes.size() + " nodes");
            MemberInfo[] nodesArrary = new MemberInfo[nodes.size()];
            nodes.toArray(nodesArrary);

            return nodesArrary;
        } catch (IOException e) {
            log().error("Unable to load nodes from file '" + file + "'.", e);
        } catch (ClassCastException e) {
            log().error("Illegal format of nodes file '" + file + "'.", e);
        } catch (ClassNotFoundException e) {
            log().error("Illegal format of nodes file '" + file + "'.", e);
        }

        return null;
    }
}
