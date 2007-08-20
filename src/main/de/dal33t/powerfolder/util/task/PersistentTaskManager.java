package de.dal33t.powerfolder.util.task;

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

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFComponent;

/**
 * Loads, stores and initializes persistent Tasks.
 * While RuntimeExceptions on de-/initialization are caught and not propagated further, tasks which block in
 * those cases are net killed and can therefore prevent this manager from working properly. (In an older revision
 * they actually are killed after a certain amount of time. I removed it because those tasks represent real "faulty" 
 * implementations which need to be fixed.) 
 * 
 * @author Dennis "Bytekeeper" Waldherr </a>
 * @version $Revision$
 */
public class PersistentTaskManager extends PFComponent {
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
    public synchronized void start()
    {
        shuttingDown = false;
        pendingTasks = new Vector<PersistentTask>();
        File taskfile = getTaskFile();
        if (taskfile.exists()) {
            log().info("Loading taskfile");
            ObjectInputStream oin = null;
            try {
                 oin = new ObjectInputStream(
                    new FileInputStream(taskfile));
                tasks = (List<PersistentTask>) oin.readObject();
                oin.close();
                log().info("Loaded " + tasks.size() + " tasks.");
            } catch (FileNotFoundException e) {
                log().error(e);
            } catch (IOException e) {
                log().error(e);
            } catch (ClassNotFoundException e) {
                log().error(e);
            } catch (ClassCastException e) {
                log().error(e);
            } finally {
            	if (oin != null) {
            		try {
						oin.close();
					} catch (IOException e) {
						log().error(e);
					}
            	}
            }
        } else {
            log().info("No taskfile found - probably first start of PF.");
        }
        // If no taskfile was found or errors occurred while loading it
        if (tasks == null) {
            tasks = new LinkedList<PersistentTask>();
        }
        for (PersistentTask t : tasks.toArray(new PersistentTask[0])) {
        	try {
        		t.init(this);
        	} catch (RuntimeException e) {
        		log().error(e);
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
        	log().error("Shutdown before initialization!");
        	return;
        }
    	waitForPendingTasks();
        for (PersistentTask t : tasks) {
        	try {
        		t.shutdown();
        	} catch (RuntimeException e) {
        		log().error(e);
        	}
        }
        File taskFile = getTaskFile();
        ObjectOutputStream oout = null;
        try {
            log().info(
                "There are " + tasks.size() + " tasks not completed yet.");
             oout = new ObjectOutputStream(
                new FileOutputStream(taskFile));
            oout.writeUnshared(tasks);
        } catch (FileNotFoundException e) {
            log().error(e);
        } catch (IOException e) {
            log().error(e);
        } finally {
        	if (oout != null) {
                try {
					oout.close();
				} catch (IOException e) {
					log().error(e);
				}
        	}
            tasks = null;
        }
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
            log().error(
                "Unable to shedule task, taskmanager not initialized! Task: "
                    + task, new RuntimeException("here"));
            return;
        }
        if (!tasks.contains(task) && !shuttingDown) {
        	log().info("Adding " + task);
            tasks.add(task);
            Runnable adder = new Runnable() {
                public void run()
                {
                    task.init(PersistentTaskManager.this);
                    pendingTasks.remove(task);
                    synchronized (PersistentTaskManager.this) {
                        PersistentTaskManager.this.notify();
                    }
                }
        	}; 
            pendingTasks.add(task);
            getController().getThreadPool()
            	.execute(adder);
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
        if (oldSD == false) { // Prevent cyclic calls from task.shutdown() -> task.remove() on faulty tasks.
	        task.shutdown();
	        tasks.remove(task);
        } else {
        	log().info(task + " shouldn't call remove() in shutdown(), it will automatically be removed!");
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

    /** Assumes the caller to have locked the manager. */
    private void waitForPendingTasks() {
        while (!pendingTasks.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                log().error(e);
            }
        }
        if (!pendingTasks.isEmpty()) {
        	StringBuilder b = new StringBuilder();
        	b.append("The following tasks are blocking:");
        	for (PersistentTask t: pendingTasks)
        		b.append(' ').append(t);
        	b.append(" and will be removed!");
        	log().error(b.toString());
        	// Note: This will also remove tasks which "might" still finish initialization
        	tasks.removeAll(pendingTasks);
        	pendingTasks.clear();
        }
    }
}
