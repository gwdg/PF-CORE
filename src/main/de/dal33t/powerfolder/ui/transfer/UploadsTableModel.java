/* $Id: UploadsTableModel.java,v 1.5.2.1 2006/04/29 10:02:55 schaatser Exp $
 */
package de.dal33t.powerfolder.ui.transfer;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;

import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.event.TransferAdapter;
import de.dal33t.powerfolder.event.TransferManagerEvent;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.transfer.TransferManager;
import de.dal33t.powerfolder.transfer.Upload;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.UIUtil;

/**
 * A Tablemodel adapter which acts upon a transfermanager.
 * 
 * @author <A HREF="mailto:schaatser@powerfolder.com">Jan van Oosterom</A>
 * @version $Revision: 1.5.2.1 $
 */
public class UploadsTableModel extends PFComponent implements TableModel {
    public static final int UPDATE_TIME = 2000;
    private MyTimerTask task;
    private Collection<TableModelListener> listeners;
    private List<Upload> uploads;

    /**
     * Constructs a new table model for uploads.
     * 
     * @param transferManager
     *            the transfermanager
     * @param enabledPeriodicalUpdates
     *            true if periodical updates should be fired.
     */
    public UploadsTableModel(TransferManager transferManager,
        boolean enabledPeriodicalUpdates)
    {
        super(transferManager.getController());
        this.listeners = Collections
            .synchronizedCollection(new LinkedList<TableModelListener>());
        this.uploads = Collections.synchronizedList(new LinkedList<Upload>());
        // Add listener
        transferManager.addListener(new UploadTransferManagerListener());

        // Init
        init(transferManager);

        if (enabledPeriodicalUpdates) {
            task = new MyTimerTask();
            getController().scheduleAndRepeat(task, UPDATE_TIME);
        }
    }

    /**
     * Initalizes the model upon a transfer manager
     * 
     * @param tm
     */
    private void init(TransferManager tm) {
        Upload[] uls = tm.getActiveUploads();
        uploads.addAll(Arrays.asList(uls));

        uls = tm.getQueuedUploads();
        uploads.addAll(Arrays.asList(uls));
    }

    // Public exposing ********************************************************

    /**
     * @param rowIndex
     * @return the upload at the specified upload row
     */
    public Upload getUploadAtRow(int rowIndex) {
        synchronized (uploads) {
            if (rowIndex >= uploads.size() || rowIndex == -1) {
                log().error(
                    "Illegal rowIndex requested. rowIndex " + rowIndex
                        + ", uploads " + uploads.size());
                return null;
            }
            return uploads.get(rowIndex);
        }
    }

    // Application logic ******************************************************

    public void clearCompleted() {
        log().warn("Clearing completed uploads");

        List<Upload> ul2remove = new LinkedList<Upload>();
        synchronized (uploads) {
            for (Iterator it = uploads.iterator(); it.hasNext();) {
                Upload upload = (Upload) it.next();
                if (upload.isCompleted()) {
                    ul2remove.add(upload);
                }
            }
        }

        // Remove ul and Fire ui model change
        for (Iterator it = ul2remove.iterator(); it.hasNext();) {
            Upload upload = (Upload) it.next();
            int index = removeUpload(upload);
            rowRemoved(index);
        }
    }

    // Listener on TransferManager ********************************************

    /**
     * Listener on Transfer manager with new event system. TODO: Consolidate
     * removing uploads on abort/complete/broken
     * 
     * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
     */
    private class UploadTransferManagerListener extends TransferAdapter {

        public void uploadRequested(TransferManagerEvent event) {
            replaceOrAddUpload(event.getUpload());
        }

        public void uploadStarted(TransferManagerEvent event) {
            replaceOrAddUpload(event.getUpload());
        }

        public void uploadAborted(TransferManagerEvent event) {
            int index;
            synchronized (uploads) {
                index = removeUpload(event.getUpload());
            }
            if (index >= 0) {
                rowRemoved(index);
            }
        }

        public void uploadBroken(TransferManagerEvent event) {
            int index;
            synchronized (uploads) {
                index = removeUpload(event.getUpload());
            }
            if (index >= 0) {
                rowRemoved(index);
            }
        }

        public void uploadCompleted(TransferManagerEvent event) {
            int index;
            synchronized (uploads) {
                index = removeUpload(event.getUpload());
            }
            if (index >= 0) {
                rowRemoved(index);
            }
        }

        public boolean fireInEventDispathThread() {
            return false;
        }
    }

    // Model helper methods ***************************************************

    /**
     * Replaces or adds a upload to the model.
     * 
     * @param upload
     *            the upload
     */
    private void replaceOrAddUpload(Upload upload) {
        int index;
        synchronized (uploads) {
            index = uploads.indexOf(upload);
            if (index >= 0) {
                uploads.remove(index);
                uploads.add(index, upload);
            } else {
                uploads.add(upload);
            }
        }

        if (index >= 0) {
            rowsUpdated(index, index);
        } else {
            rowAdded();
        }
    }

    /**
     * Removes one upload from the model an returns its previous index
     * 
     * @param upload
     * @return the index where this upload was removed from.
     */
    private int removeUpload(Upload upload) {
        int index;
        synchronized (uploads) {
            index = uploads.indexOf(upload);
            if (index >= 0) {
                log().verbose("Remove upload from tablemodel: " + upload);
                uploads.remove(index);
            } else {
                log().error(
                    "Unable to remove upload from tablemodel, not found: "
                        + upload);
            }
        }
        return index;
    }

    // Permanent updater ******************************************************

    /**
     * Continouosly updates the ui
     */
    private class MyTimerTask extends TimerTask {
        public void run() {
            Runnable wrapper = new Runnable() {
                public void run() {
                    rowsUpdatedAll();
                }
            };
            try {
                SwingUtilities.invokeAndWait(wrapper);
            } catch (InterruptedException e) {
                log().verbose("Interrupteed while updating downloadstable", e);

            } catch (InvocationTargetException e) {
                log().error("Unable to update downloadstable", e);
            }
        }
    }

    // TableModel interface ***************************************************

    public int getColumnCount() {
        return 5;
    }

    public int getRowCount() {
        return uploads.size();
    }

    public String getColumnName(int columnIndex) {
        switch (columnIndex) {
            case 0 :
                return Translation.getTranslation("general.file");
            case 1 :
                return Translation.getTranslation("transfers.progress");
            case 2 :
                return Translation.getTranslation("general.size");
            case 3 :
                return Translation.getTranslation("general.folder");
            case 4 :
                return Translation.getTranslation("transfers.to");
        }
        return null;
    }

    public Class getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0 :
                return FileInfo.class;
            case 1 :
                return Upload.class;
            case 2 :
                return Long.class;
            case 3 :
                return FolderInfo.class;
            case 4 :
                return Member.class;
        }
        return null;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex >= uploads.size()) {
            log().error(
                "Illegal rowIndex requested. rowIndex " + rowIndex
                    + ", uploads " + uploads.size());
            return null;
        }
        Upload upload = uploads.get(rowIndex);
        switch (columnIndex) {
            case 0 :
                return upload.getFile();
            case 1 :
                return upload;
            case 2 :
                return new Long(upload.getFile().getSize());
            case 3 :
                return upload.getFile().getFolderInfo();
            case 4 :
                return upload.getPartner();
        }
        return null;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        throw new IllegalStateException(
            "Unable to set value in UploadTableModel, not editable");
    }

    public void addTableModelListener(TableModelListener l) {
        listeners.add(l);
    }

    public void removeTableModelListener(TableModelListener l) {
        listeners.remove(l);
    }

    // Helper method **********************************************************

    /**
     * Tells listeners, that a new row at the end of the table has been added
     */
    private void rowAdded() {
        TableModelEvent e = new TableModelEvent(this, getRowCount() - 1,
            getRowCount() - 1, TableModelEvent.ALL_COLUMNS,
            TableModelEvent.INSERT);
        modelChanged(e);
    }

    private synchronized void rowRemoved(int row) {
        TableModelEvent e = new TableModelEvent(this, row, row,
            TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE);
        modelChanged(e);
    }

    private void rowsUpdated(int start, int end) {
        TableModelEvent e = new TableModelEvent(this, start, end,
            TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE);
        modelChanged(e);
    }

    /**
     * fire change on whole model
     */
    private void rowsUpdatedAll() {
        rowsUpdated(0, uploads.size());
    }

    /**
     * Fires an modelevent to all listeners, that model has changed
     */
    private void modelChanged(final TableModelEvent e) {
        // log().verbose("Upload tablemodel changed");
        Runnable runner = new Runnable() {
            public void run() {
                synchronized (listeners) {
                    for (Iterator it = listeners.iterator(); it.hasNext();) {
                        TableModelListener listener = (TableModelListener) it
                            .next();
                        listener.tableChanged(e);
                    }
                }
            }
        };

        UIUtil.invokeLaterInEDT(runner);
    }
}