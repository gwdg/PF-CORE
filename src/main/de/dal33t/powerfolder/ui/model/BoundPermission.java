/*
 * Copyright 2004 - 2010 Christian Sprajc. All rights reserved.
 *
 * This file is part of PowerFolder.
 *
 * PowerFolder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * PowerFolder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id: MainFrame.java 11813 2010-03-20 03:20:21Z harry $
 */
package de.dal33t.powerfolder.ui.model;

import java.lang.ref.WeakReference;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.clientserver.ServerClientEvent;
import de.dal33t.powerfolder.clientserver.ServerClientListener;
import de.dal33t.powerfolder.security.Permission;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.ui.UIUtil;

/**
 * Generic helper to check if a permission is set / changes.
 * <p>
 * Does only check permission if
 * {@link ConfigurationEntry#SECURITY_PERMISSIONS_STRICT} is set to true.
 * 
 * @author sprajc
 */
public abstract class BoundPermission extends PFComponent {

    private ServerClientListener registeredListener;
    private ServerClientListener originalListener;
    private Permission permission;
    private boolean hasPermission;

    // Construction / Destruction *********************************************

    public BoundPermission(Controller controller, Permission permission) {
        super(controller);
        Reject.ifNull(permission, "Permission");
        this.permission = permission;
        // Hold original listener. Should only be GCed when the BoundPermission
        // object gets collected - NOT earlier.
        this.originalListener = new MyServerClientListener();
        this.registeredListener = new MyWeakListener(this, originalListener);
        getController().getOSClient().addListener(this.registeredListener);
        getController().schedule(new Runnable() {
            public void run() {
                checkPermission(true);
            }
        }, 0);
    }

    public void dispose() {
        if (registeredListener != null) {
            getController().getOSClient().removeListener(registeredListener);
            registeredListener = null;
            originalListener = null;
        }
    }

    // Abstract behavior ******************************************************

    /**
     * Called in EDT if the permission actual changed. Called ONCE on
     * construction to set initial value.
     * 
     * @param hasPermission
     */
    public abstract void hasPermission(boolean hasPermission);

    // Internal helper ********************************************************

    private synchronized void checkPermission(boolean initial) {
        if (!ConfigurationEntry.SECURITY_PERMISSIONS_STRICT
            .getValueBoolean(getController()))
        {
            // Not using this.
            return;
        }
        // Disable access by default. Only if actually has permission
        UIUtil.invokeLaterInEDT(new Runnable() {
            public void run() {
                hasPermission(false);
            }
        });

        // Alternative thru security manager.
        // AccountInfo aInfo = getController().getOSClient().getAccountInfo();
        // hasPermission = getController().getSecurityManager().hasPermission(
        // aInfo, permission);

        // Faster:
        hasPermission = getController().getOSClient().getAccount()
            .hasPermission(permission);

        if (initial || hasPermission) {
            // Prevent unwanted while sitting in EDT queue.
            final boolean thisHasPermission = hasPermission;
            UIUtil.invokeLaterInEDT(new Runnable() {
                public void run() {
                    hasPermission(thisHasPermission);
                }
            });
        }
    }

    private static final class MyWeakListener implements ServerClientListener {
        private BoundPermission boundPermission;
        private WeakReference<ServerClientListener> delegateRef;

        private MyWeakListener(BoundPermission boundPermission,
            ServerClientListener listener)
        {
            this.boundPermission = boundPermission;
            this.delegateRef = new WeakReference<ServerClientListener>(listener);
        }

        public void accountUpdated(ServerClientEvent event) {
            ServerClientListener deligate = delegateRef.get();
            if (deligate != null) {
                deligate.accountUpdated(event);
            } else {
                // Remove. Delegate was GCed
                boundPermission.dispose();
            }
        }

        public void login(ServerClientEvent event) {
            ServerClientListener deligate = delegateRef.get();
            if (deligate != null) {
                deligate.login(event);
            } else {
                // Remove. Delegate was GCed
                boundPermission.dispose();
            }
        }

        public void serverConnected(ServerClientEvent event) {
            ServerClientListener deligate = delegateRef.get();
            if (deligate != null) {
                deligate.serverConnected(event);
            } else {
                // Remove. Delegate was GCed
                boundPermission.dispose();
            }
        }

        public void serverDisconnected(ServerClientEvent event) {
            ServerClientListener deligate = delegateRef.get();
            if (deligate != null) {
                deligate.serverDisconnected(event);
            } else {
                // Remove. Delegate was GCed
                boundPermission.dispose();
            }
        }

        public boolean fireInEventDispatchThread() {
            ServerClientListener deligate = delegateRef.get();
            if (deligate != null) {
                return deligate.fireInEventDispatchThread();
            } else {
                // Delegate was GCed
                return false;
            }
        }

    }

    private final class MyServerClientListener implements ServerClientListener {
        public boolean fireInEventDispatchThread() {
            return false;
        }

        public void serverDisconnected(ServerClientEvent event) {
        }

        public void serverConnected(ServerClientEvent event) {
        }

        public void login(ServerClientEvent event) {
            getController().schedule(new Runnable() {
                public void run() {
                    checkPermission(false);
                }
            }, 0);

        }

        public void accountUpdated(ServerClientEvent event) {
            getController().schedule(new Runnable() {
                public void run() {
                    checkPermission(false);
                }
            }, 0);
        }
    }

}