package de.dal33t.powerfolder.event;

/** interface to implement to receive events from the FolderRepository */
public interface FolderRepositoryListener extends CoreListener {

    /**
     * Fired by the FolderRepository if a new Public folder (FolderInfo) is
     * available
     */
    public void unjoinedFolderAdded(FolderRepositoryEvent e);

    /**
     * Fired when a unjoined folder was removed
     */
    public void unjoinedFolderRemoved(FolderRepositoryEvent e);

    /**
     * Fired by the FolderRepository if a Folder is removed from the list of
     * "joined Folders"
     */
    public void folderRemoved(FolderRepositoryEvent e);

    /**
     * Fired by the FolderRepository if a Folder is added to the list of "joined
     * Folders"
     */
    public void folderCreated(FolderRepositoryEvent e);

    /**
     * Fired by the FolderRepository when the scans are started
     */
    public void scansStarted(FolderRepositoryEvent e);

    /**
     * Fired by the FolderRepository when the scans are finished
     */
    public void scansFinished(FolderRepositoryEvent e);
}
