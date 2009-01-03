/*
 * Copyright 2004 - 2008 Christian Sprajc, Dennis Waldherr. All rights reserved.
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
package de.dal33t.powerfolder.util.task;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFComponent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads, stores and initializes persistent Tasks. While RuntimeExceptions on
 * de-/initialization are caught and not propagated further, tasks which block
 * in those cases are not killed and can therefore prevent this manager from
 * working properly. (In an older revision they actually are killed after a
 * certain amount of time. I removed it because those tasks represent real
 * "faulty" implementations which need to be fixed.)
 * 
 * @author Dennis "Bytekeeper" Waldherr </a>
 * @version $Revision$
 */
public class PersistentTaskManager extends PFComponent {

    private static final Logger log = Logger.getLogger(PersistentTaskManager.class.getName());
    private List<PersistentTask> tasks;
    /**
     * Pending tasks that await initialization.
     */
    private List<PersistentTask> pendingTasks;
    private boolean shuttingDown = false;

    public PersistentTaskManager(Controller controller) {
        super(controller);
    }

    /**
     * Returns the file which represents the persistent store of tasks.
     * 
     * @return the tasklist-file
     */
    private File getTaskFile() {
        String filename = getController().getConfigName() + ".tasks";
        File taskFile = new File(Controller.getMiscFilesLocation(), filename);
        new File(taskFile.getParent()).mkdirs();
        return taskFile;
    }

    /**
     * Starts this manager.
     */
    @SuppressWarnings("unchecked")
    public synchronized void start() {
        shuttingDown = false;
        pendingTasks = new Vector<PersistentTask>();
        File taskfile = getTaskFile();
        if (taskfile.exists()) {
            logInfo("Loading taskfile");
            ObjectInputStream oin = null;
            try {
                oin = new ObjectInputStream(new FileInputStream(taskfile));
                tasks = (List<PersistentTask>) oin.readObject();
                oin.close();
                logInfo("Loaded " + tasks.size() + " tasks.");
            } catch (FileNotFoundException e) {
                logSevere("FileNotFoundException", e);
            } catch (IOException e) {
                logSevere("IOException", e);
            } catch (ClassNotFoundException e) {
                logSevere("ClassNotFoundException", e);
            } catch (ClassCastException e) {
                logSevere("ClassCastException", e);
            } finally {
                if (oin != null) {
                    try {
                        oin.close();
                    } catch (IOException e) {
                        logSevere("IOException", e);
                    }
                }
            }
        } else {
            logInfo("No taskfile found - probably first start of PF.");
        }
        // If no taskfile was found or errors occurred while loading it
        if (tasks == null) {
            tasks = new LinkedList<PersistentTask>();
        }
        for (PersistentTask t : tasks.toArray(new PersistentTask[tasks.size()])) {
            try {
                t.init(this);
            } catch (RuntimeException e) {
                logSevere("RuntimeException", e);
                tasks.remove(t);
            }
        }
    }

    /**
     * Shuts down the manager. Saves all remaining tasks - they'll continue
     * execution once the manager has been restarted (Not necessarily in this
     * session of PowerFolder)
     */
    public synchronized void shutdown() {
        shuttingDown = true;
        if (tasks == null || pendingTasks == null) {
            logSevere("Shutdown before initialization!");
            return;
        }
        waitForPendingTasks();
        for (PersistentTask t : tasks) {
            try {
                t.shutdown();
            } catch (RuntimeException e) {
                logSevere("RuntimeException", e);
            }
        }
        File taskFile = getTaskFile();
        ObjectOutputStream oout = null;
        try {
            logInfo("There are " + tasks.size() + " tasks not completed yet.");
            oout = new ObjectOutputStream(new FileOutputStream(taskFile));
            oout.writeUnshared(tasks);
        } catch (FileNotFoundException e) {
            logSevere("FileNotFoundException", e);
        } catch (IOException e) {
            logSevere("IOException", e);
        } finally {
            if (oout != null) {
                try {
                    oout.close();
                } catch (IOException e) {
                    logSevere("IOException", e);
                }
            }
            tasks = null;
        }
    }
    
    public boolean isStarted() {
        return tasks != null;
    }

    /**
     * Schedules a new task. The given task will be started as soon as possible
     * by the shared ThreadPool of the Controller class.
     * 
     * @param task
     *            the task to start
     */
    public synchronized void scheduleTask(final PersistentTask task) {
        if (tasks == null) {
            log.log(Level.SEVERE,
                "Unable to shedule task, taskmanager not initialized! Task: "
                    + task, new RuntimeException("here"));
            return;
        }
        if (!tasks.contains(task) && !shuttingDown) {
            logInfo("Adding " + task);
            tasks.add(task);
            Runnable adder = new Runnable() {
                public void run() {
                    task.init(PersistentTaskManager.this);
                    pendingTasks.remove(task);
                    synchronized (PersistentTaskManager.this) {
                        PersistentTaskManager.this.notify();
                    }
                }
            };
            pendingTasks.add(task);
            getController().getThreadPool().execute(adder);
        }
    }

    /**
     * Shuts down and removes a given task. This method will block until all
     * tasks are properly initialized before removing the given task.
     * 
     * @param task
     *            the task to remove
     */
    public synchronized void removeTask(PersistentTask task) {
        boolean oldSD = shuttingDown;
        shuttingDown = true;
        if (pendingTasks.contains(task)) {
            pendingTasks.remove(task);
        } else {
            waitForPendingTasks();
        }
        if (oldSD == false) { // Prevent cyclic calls from task.shutdown() ->
            // task.remove() on faulty tasks.
            task.shutdown();
            tasks.remove(task);
        } else {
            logInfo(task
                + " shouldn't call remove() in shutdown(), it will automatically be removed!");
        }
        shuttingDown = oldSD;
    }

    /**
     * Removes all pending tasks. This is useful for tests or to clear all tasks
     * in case some are erroneous. This method will block until all tasks are
     * properly initialized.
     */
    public synchronized void purgeAllTasks() {
        boolean oldSD = shuttingDown;
        shuttingDown = true;
        waitForPendingTasks();
        while (!tasks.isEmpty()) {
            tasks.remove(0).shutdown();
        }
        shuttingDown = oldSD;
    }

    /**
     * Returns if there are any pending tasks.
     * 
     * @return true if there are 1 or more active tasks
     */
    public synchronized boolean hasTasks() {
        return !tasks.isEmpty();
    }

    /**
     * Returns the number of active tasks
     * 
     * @return the active task count
     */
    public synchronized int activeTaskCount() {
        return tasks.size();
    }

    /**
     * Required to solve TRAC #1124. Nicer API method would be:
     * hasPendingMessagesTo(MemberInfo);
     * 
     * @return if there are pending messages to be sent.
     */
    public synchronized boolean hasSendMessageTask() {
        if (tasks == null) {
            return false;
        }
        for (PersistentTask task : tasks) {
            if (task instanceof SendMessageTask) {
                if (isFiner()) {
                    logFiner("Found pending message(s). total active tasks: "
                        + tasks.size());
                }
                return true;
            }
            // SendMessageTask sendTask = (SendMessageTask) task;
            // if (!sendTask.getTargetID().equals(node.id)) {
            // logWarning("Found pending message(s) to " + node);
            // return true;
            // }
        }
        // No pending found
        return false;
    }

    /** Assumes the caller to have locked the manager. */
    private void waitForPendingTasks() {
        while (!pendingTasks.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                logSevere("InterruptedException", e);
            }
        }
        if (!pendingTasks.isEmpty()) {
            StringBuilder b = new StringBuilder();
            b.append("The following tasks are blocking:");
            for (PersistentTask t : pendingTasks)
                b.append(' ').append(t);
            b.append(" and will be removed!");
            logSevere(b.toString());
            // Note: This will also remove tasks which "might" still finish
            // initialization
            tasks.removeAll(pendingTasks);
            pendingTasks.clear();
        }
    }
}
