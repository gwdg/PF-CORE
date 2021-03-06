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
 * $Id$
 */
package de.dal33t.powerfolder.light;

/**
 * A object, that holds some cost-intense strings about a fileinfo.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class FileInfoStrings {
    private String fileNameOnly;
    private String lowerCaseName;
    private String locationInFolder;

    String getFileNameOnly() {
        return fileNameOnly;
    }

    void setFileNameOnly(String fileNameOnly) {
        this.fileNameOnly = fileNameOnly;
    }

    String getLocationInFolder() {
        return locationInFolder;
    }

    void setLocationInFolder(String locationInFolder) {
        this.locationInFolder = locationInFolder;
    }

    String getLowerCaseName() {
        return lowerCaseName;
    }

    void setLowerCaseName(String lowerCaseName) {
        this.lowerCaseName = lowerCaseName;
    }
}
