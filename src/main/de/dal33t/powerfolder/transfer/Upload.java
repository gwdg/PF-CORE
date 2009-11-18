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
package de.dal33t.powerfolder.transfer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.Queue;

import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.message.FileChunk;
import de.dal33t.powerfolder.message.Message;
import de.dal33t.powerfolder.message.ReplyFilePartsRecord;
import de.dal33t.powerfolder.message.RequestDownload;
import de.dal33t.powerfolder.message.RequestFilePartsRecord;
import de.dal33t.powerfolder.message.RequestPart;
import de.dal33t.powerfolder.message.StartUpload;
import de.dal33t.powerfolder.message.StopUpload;
import de.dal33t.powerfolder.net.ConnectionException;
import de.dal33t.powerfolder.util.*;
import de.dal33t.powerfolder.util.delta.FilePartsRecord;

/**
 * Simple class for a scheduled Upload
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.13 $
 */
@SuppressWarnings("serial")
public class Upload extends Transfer {

    private boolean aborted;
    private transient Queue<Message> pendingRequests = new LinkedList<Message>();

    protected transient RandomAccessFile raf;

    private String debugState;

    /**
     * Constructs a new uploads, package protected, can only be called by
     * transfermanager
     * 
     * @param manager
     * @param member
     * @param dl
     */
    Upload(TransferManager manager, Member member, RequestDownload dl) {
        super(manager, (FileInfo) ((dl == null) ? null : dl.file), member);
        if (dl == null) {
            throw new NullPointerException("Download request is null");
        }
        if (dl.file == null) {
            throw new NullPointerException("File is null");
        }
        setStartOffset(dl.startOffset);
        aborted = false;
        debugState = "initialized";
    }

    private void enqueueMessage(Message m) {
        try {
            synchronized (pendingRequests) {
                if (pendingRequests.size() >= getTransferManager()
                    .getMaxRequestsQueued() * 5)
                {
                    throw new TransferException("Too many requests queued: "
                        + pendingRequests.size() + ", maximum: "
                        + getTransferManager().getMaxRequestsQueued() * 5);
                }
                pendingRequests.add(m);
                pendingRequests.notifyAll();
            }
        } catch (TransferException e) {
            logSevere("TransferException", e);
            getTransferManager().setBroken(this,
                TransferProblem.TRANSFER_EXCEPTION, e.getMessage());
        }
    }

    public void enqueuePartRequest(RequestPart pr) {
        Reject.ifNull(pr, "Message is null");

        // If the download was aborted
        if (aborted || !isStarted()) {
            return;
        }

        // Requests for different files on the same transfer connection are not
        // supported currently
        if (!pr.getFile().isVersionDateAndSizeIdentical(getFile())
            || pr.getRange().getLength() <= 0)
        {
            logSevere("Received invalid part request!");
            getTransferManager().setBroken(this, TransferProblem.INVALID_PART);
            return;
        }
        if (pr.getRange().getLength() > getTransferManager()
            .getMaxFileChunkSize())
        {
            logWarning("Got request for a range bigger then my max filechunk size ("
                + pr.getRange() + "): " + pr.getRange().getLength());
        }
        transferState.setProgress(pr.getProgress());
        enqueueMessage(pr);
    }

    public void receivedFilePartsRecordRequest(RequestFilePartsRecord r) {
        Reject.ifNull(r, "Record is null");

        logInfo("Received request for a parts record.");
        // If the download was aborted
        if (aborted || !isStarted()) {
            return;
        }
        if (getFile().getSize() < Constants.MIN_SIZE_FOR_PARTTRANSFERS) {
            logWarning("Remote side requested invalid PartsRecordRequest!");

            getTransferManager().setBroken(this,
                TransferProblem.GENERAL_EXCEPTION,
                "Remote side requested invalid PartsRecordRequest!");
            return;
        }
        enqueueMessage(r);
    }

    public void stopUploadRequest(StopUpload su) {
        Reject.ifNull(su, "Message is null");

        synchronized (pendingRequests) {
            pendingRequests.clear();
            pendingRequests.add(su);
            pendingRequests.notifyAll();
        }
    }

    public void cancelPartRequest(RequestPart pr) {
        Reject.ifNull(pr, "Message is null");

        synchronized (pendingRequests) {
            pendingRequests.remove(pr);
            pendingRequests.notifyAll();
        }
    }

    /**
     * Starts the upload in a own thread using the give transfer manager
     */
    synchronized void start() {
        if (isStarted()) {
            logWarning("Upload already started. " + this);
            return;
        }

        debugState = "Starting";
        // Mark upload as started
        setStarted();

        Runnable uploadPerfomer = new Runnable() {
            public void run() {
                try {
                    debugState = "Opening file";
                    try {
                        raf = new RandomAccessFile(getFile().getDiskFile(
                            getController().getFolderRepository()), "r");
                    } catch (FileNotFoundException e) {
                        throw new TransferException(e);
                    }

                    // If our partner supports requests, let him request. This
                    // is required for swarming to work.
                    if (isFiner()) {
                        logFiner("Both clients support partial transfers!");
                    }
                    debugState = "Sending StartUpload";
                    try {
                        getPartner().sendMessage(new StartUpload(getFile()));
                    } catch (ConnectionException e) {
                        throw new TransferException(e);
                    }
                    debugState = "Waiting for requests";
                    if (waitForRequests()) {
                        if (isFiner()) {
                            logFiner("Checking for parts request.");
                        }

                        debugState = "Checking for FPR request.";

                        // Check if the first request is for a
                        // FilePartsRecord
                        if (checkForFilePartsRecordRequest()) {
                            debugState = "Waiting for remote matching";
                            transferState
                                .setState(TransferState.REMOTEMATCHING);
                            logFiner("Waiting for initial part requests!");
                            waitForRequests();
                        }
                        debugState = "Starting to send parts";
                        logInfo("Upload started " + this);
                        long startTime = System.currentTimeMillis();

                        // FIXME: It shouldn't be possible to loop endlessly
                        // This fixme has to solved somewhere else partly
                        // since
                        // it's like:
                        // "How long do we allow to upload to some party" -
                        // which can't be decided here.
                        while (sendPart()) {
                        }
                        long took = System.currentTimeMillis() - startTime;
                        getTransferManager().logTransfer(false, took,
                            getFile(), getPartner());
                    }
                    closeRAF();
                    if (!isBroken() && !aborted) {
                        getTransferManager().setCompleted(Upload.this);
                    }
                } catch (TransferException e) {
                    if (raf != null) {
                        closeRAF();
                    }
                    // Loggable.logWarningStatic(Upload.class, "Upload broken: "
                    // + Upload.this, e);
                    getTransferManager().setBroken(Upload.this,
                        TransferProblem.TRANSFER_EXCEPTION, e.getMessage());
                } finally {
                    debugState = "DONE";
                }
            }

            private void closeRAF() {
                try {
                    if (isFiner()) {
                        logFiner("Closing raf for "
                            + getFile().toDetailString());
                    }
                    raf.close();
                } catch (IOException e) {
                    logSevere("IOException", e);
                }
            }

            public String toString() {
                return "Upload " + getFile().toDetailString() + " to "
                    + getPartner().getNick();
            }
        };

        // Perfom upload in threadpool
        getTransferManager().perfomUpload(uploadPerfomer);
    }

    protected boolean checkForFilePartsRecordRequest() throws TransferException
    {
        RequestFilePartsRecord r = null;
        synchronized (pendingRequests) {
            if (pendingRequests.isEmpty()) {
                logWarning("Cancelled message too fast");
                return false;
            }
            if (pendingRequests.peek() instanceof RequestFilePartsRecord) {
                r = (RequestFilePartsRecord) pendingRequests.remove();
            }
        }
        if (r == null) {
            return false;
        }
        final FileInfo fi = r.getFile();
        checkLastModificationDate(fi, fi.getDiskFile(getController()
            .getFolderRepository()));
        FilePartsRecord fpr;
        try {
            transferState.setState(TransferState.FILEHASHING);
            fpr = getTransferManager().getFileRecordManager().retrieveRecord(
                fi, new ProgressListener() {
                    public void progressReached(double percentageReached) {
                        transferState.setProgress(percentageReached);
                    }

                });
            getPartner().sendMessagesAsynchron(
                new ReplyFilePartsRecord(fi, fpr));
            transferState.setState(TransferState.UPLOADING);
        } catch (FileNotFoundException e) {
            logSevere("FileNotFoundException", e);
            getTransferManager().setBroken(Upload.this,
                TransferProblem.FILE_NOT_FOUND_EXCEPTION, e.getMessage());
        } catch (IOException e) {
            logSevere("IOException", e);
            getTransferManager().setBroken(Upload.this,
                TransferProblem.IO_EXCEPTION, e.getMessage());
        }
        return true;
    }

    /**
     * Sends one requested part.
     * 
     * @return false if the upload should stop, true otherwise
     * @throws TransferException
     */
    private boolean sendPart() throws TransferException {
        if (getPartner() == null) {
            throw new NullPointerException("Upload member is null");
        }
        if (getFile() == null) {
            throw new NullPointerException("Upload file is null");
        }
        if (isAborted() || isBroken()) {
            return false;
        }
        transferState.setState(TransferState.UPLOADING);
        RequestPart pr = null;
        synchronized (pendingRequests) {
            while (pendingRequests.isEmpty() && !isBroken() && !isAborted()) {
                try {
                    pendingRequests.wait(Constants.UPLOAD_PART_REQUEST_TIMEOUT);
                } catch (InterruptedException e) {
                    logSevere("InterruptedException", e);
                    throw new TransferException(e);
                }
            }
            // If it's still empty we either got a StopUpload, or we got
            // interrupted or it got aborted in which case we just drop out.
            // Also the timeout could be the cause in which case this also is
            // the end of the upload.
            if (pendingRequests.isEmpty()) {
                return false;
            }
            if (pendingRequests.peek() instanceof StopUpload) {
                pendingRequests.remove();
                return false;
            }
            pr = (RequestPart) pendingRequests.remove();

            if (isAborted() || isBroken()) {
                return false;
            }
        }
        try {

            // TODO: Maybe cache the file
            File f = pr.getFile().getDiskFile(
                getController().getFolderRepository());

            byte[] data = new byte[(int) pr.getRange().getLength()];
            raf.seek(pr.getRange().getStart());
            int pos = 0;
            while (pos < data.length) {
                int read = raf.read(data, pos, data.length - pos);
                if (read < 0) {
                    logWarning("Requested part exceeds filesize!");
                    throw new TransferException(
                        "Requested part exceeds filesize!");
                }
                pos += read;
            }
            FileChunk chunk = new FileChunk(pr.getFile(), pr.getRange()
                .getStart(), data);
            getPartner().sendMessage(chunk);
            getCounter().chunkTransferred(chunk);
            getTransferManager().getUploadCounter().chunkTransferred(chunk);

            // FIXME: Below this check is done every 15 seconds - maybe restrict
            // this test here too
            checkLastModificationDate(pr.getFile(), f);

        } catch (FileNotFoundException e) {
            logSevere("FileNotFoundException", e);
            throw new TransferException(e);
        } catch (IOException e) {
            logSevere("IOException", e);
            throw new TransferException(e);
        } catch (ConnectionException e) {
            logWarning("Connectiopn problem while uploading. " + e.toString());
            if (isFiner()) {
                logFiner("ConnectionException", e);
            }
            throw new TransferException(e);
        }
        return true;
    }

    protected boolean waitForRequests() {
        if (isBroken() || aborted) {
            return false;
        }
        synchronized (pendingRequests) {
            if (!pendingRequests.isEmpty()) {
                return true;
            }
            try {
                while (pendingRequests.isEmpty() && !isBroken() && !isAborted())
                {
                    pendingRequests.wait();
                }
            } catch (InterruptedException e) {
                logSevere("InterruptedException", e);
            }
        }
        return !isBroken() && !aborted && !pendingRequests.isEmpty();
    }

    /**
     * Aborts this dl if currently transferrings
     */
    synchronized void abort() {
        logFiner("Upload aborted: " + this);
        aborted = true;

        stopUploads();
    }

    /**
     * Shuts down this upload if currently active
     */
    void shutdown() {
        super.shutdown();
        // "Forget" all requests from the client
        stopUploads();
    }

    private void stopUploads() {
        synchronized (pendingRequests) {
            pendingRequests.clear();
            // Notify any remaining waiter
            pendingRequests.notifyAll();
        }
    }

    public boolean isAborted() {
        return aborted;
    }

    /**
     * @return if this upload is broken
     */
    public boolean isBroken() {
        if (super.isBroken()) {
            return true;
        }

        if (!stillQueuedAtPartner()) {
            logWarning("Upload broken because not enqued @ partner: queedAtPartner: "
                + stillQueuedAtPartner()
                + ", folder: "
                + getFile().getFolder(getController().getFolderRepository())
                + ", diskfile: "
                + getFile().getDiskFile(getController().getFolderRepository())
                + ", last contime: " + getPartner().getLastConnectTime());
        }

        File diskFile = getFile().getDiskFile(
            getController().getFolderRepository());
        if (diskFile == null || !diskFile.exists()) {
            logWarning("Upload broken because diskfile is not available, folder: "
                + getFile().getFolder(getController().getFolderRepository())
                + ", diskfile: "
                + diskFile
                + ", last contime: "
                + getPartner().getLastConnectTime());
            return true;
        }

        return !stillQueuedAtPartner();
    }

    /*
     * General
     */

    public int hashCode() {
        int hash = 0;
        if (getFile() != null) {
            hash += getFile().hashCode();
        }
        if (getPartner() != null) {
            hash += getPartner().hashCode();
        }
        return hash;
    }

    public boolean equals(Object o) {
        if (o instanceof Upload) {
            Upload other = (Upload) o;
            return Util.equals(this.getFile(), other.getFile())
                && Util.equals(this.getPartner(), other.getPartner());
        }

        return false;
    }

    public String toString() {
        String msg = "State: " + debugState + ", TransferState: "
            + transferState.getState() + " " + getFile().toDetailString()
            + " to '" + getPartner().getNick() + "'";
        if (getPartner().isOnLAN()) {
            msg += " (local-net)";
        }
        return msg;
    }

    private void checkLastModificationDate(FileInfo theFile, File f)
        throws TransferException
    {
        assert theFile != null;
        assert f != null;

        boolean lastModificationDataMismatch = !DateUtil
            .equalsFileDateCrossPlattform(f.lastModified(), theFile
                .getModifiedDate().getTime());
        if (lastModificationDataMismatch) {
            Folder folder = theFile.getFolder(getController()
                .getFolderRepository());
            if (folder.scanAllowedNow()) {
                folder.scanChangedFile(theFile);
            }
            // folder.recommendScanOnNextMaintenance();
            throw new TransferException("Last modification date mismatch. '"
                + f.getAbsolutePath()
                + "': expected "
                + Convert.convertToGlobalPrecision(theFile.getModifiedDate()
                    .getTime()) + ", actual "
                + Convert.convertToGlobalPrecision(f.lastModified()));
        }
    }
}