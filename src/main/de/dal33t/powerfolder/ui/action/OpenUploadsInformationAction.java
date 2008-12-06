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
* $Id: OpenUploadsInformationAction.java 5514 2008-10-25 15:23:59Z harry $
*/
package de.dal33t.powerfolder.ui.action;

import de.dal33t.powerfolder.Controller;

import java.awt.event.ActionEvent;

/**
 * Creates an Action that displays the file information for recent uploads.
 *
 * @author <a href="mailto:harry@powerfolder.com">Harry Glasgow</a>
 */
public class OpenUploadsInformationAction extends BaseAction {

    public OpenUploadsInformationAction(Controller controller) {
        super("action_open_uploads_information", controller);
    }

    public void actionPerformed(ActionEvent e) {
        getController().getUIController().openUploadsInformation();
    }
}