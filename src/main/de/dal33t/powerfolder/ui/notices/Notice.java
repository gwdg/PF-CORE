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
 * $Id: Notice.java 12401 2010-05-20 00:52:17Z harry $
 */
package de.dal33t.powerfolder.ui.notices;

import java.util.Date;
import java.util.UUID;

/**
 * This interface is the generic ui Notice that is used to either show a
 * notification popup message to the user and/or add to the actionmodel
 * Notices for action by the user.
 *
 * Notices are not used with Chat notifications.
 */
public interface Notice {

    /** uniqu id for class chain. */
    UUID getUuid();

    /** Date created. */
    Date getDate();

    /** Title to display in notification window or when viewing notices. */
    String getTitle();

    /** Short summary displayed in notification window / when viewing notices. */
    String getSummary();

    /** For Actionables, this is the object to work on. */
    Object getPayload();

    /** True if this should be shown in notification popup window. */
    boolean isNotification();

    /** True if this should be stored in the app model for actioning. */
    boolean isActionable();

    /** Notional severity level. */
    NoticeSeverity getNoticeSeverity();
}