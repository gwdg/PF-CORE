/*
 * Copyright 2004 - 2008 Christian Sprajc. All rights reserved.
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
 * $Id: FolderAdminPermission.java 4282 2008-06-16 03:25:09Z tot $
 */
package de.dal33t.powerfolder.security;

/**
 * Administration permission
 * 
 * @author Christian Sprajc
 * @version $Revision$
 */
public class AdminPermission extends SingletonPermission {
    private static final long serialVersionUID = 100L;
    public static final AdminPermission INSTANCE = new AdminPermission();

    public boolean implies(Permission impliedPermision) {
        // ADMIN, All permissions implied except SystemSettings
        if (SystemSettingsPermission.INSTANCE.equals(impliedPermision)) {
            return false;
        }
        // Also admin is NEVER owner of a folder.
        if (impliedPermision instanceof FolderOwnerPermission) {
            return false;
        }
        return true;
    }
}
