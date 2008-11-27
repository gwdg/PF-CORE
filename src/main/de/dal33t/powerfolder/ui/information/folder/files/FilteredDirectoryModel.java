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
* $Id: FilteredDirectoryModel.java 5457 2008-10-17 14:25:41Z harry $
*/
package de.dal33t.powerfolder.ui.information.folder.files;

import de.dal33t.powerfolder.light.FileInfo;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightwight model of a filtered directory. Use in reference to the original
 * Directory to get the real detail.
 */
public class FilteredDirectoryModel {

    private String displayName;
    private File file;
    private List<FileInfo> files;
    private List<FilteredDirectoryModel> subdirectories;

    /**
     * Constructor
     */
    public FilteredDirectoryModel(String displayName, File file) {
        this.displayName = displayName;
        this.file = file;
        files = new CopyOnWriteArrayList<FileInfo>();
        subdirectories = new CopyOnWriteArrayList<FilteredDirectoryModel>();
    }

    /**
     * Returns the display name for the node.
     * @return
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the file.
     * 
     * @return
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets a readonly copy of the directories files.
     * @return
     */
    public List<FileInfo> getFiles() {
        return files;
    }

    /**
     * Returns a list of subdirectory names and sub-FilteredDirectoryModels
     */
    public List<FilteredDirectoryModel> getSubdirectories() {
        return subdirectories;
    }
}
