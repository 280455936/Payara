/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 * Portions Copyright [2017-2019] Payara Foundation and/or affiliates
 */

package org.glassfish.api.admin;


import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The implementation of the admin command lock.
 * 
 * @author Bill Shannon
 * @author Chris Kasso
 */
@Service
@Singleton
public class AdminCommandLock {

    @Inject
    Logger logger;

    /**
     * The read/write lock.  We depend on this class being a singleton
     * and thus there being exactly one such lock object, shared by all
     * users of this class.
     */
    private static final ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();

    /**
     * A thread which can hold a Read/Write lock across command invocations.
     * Once the lock is released the thread will exit.
     */
    private SuspendCommandsLockThread suspendCommandsLockThread = null;

    private String lockOwner = null;
    private String lockMessage = null;
    private Date   lockTimeOfAcquisition = null;

    /**
     * The status of a suspend command attempt.
     */
    public enum SuspendStatus { SUCCESS,       // Suspend succeeded
                                TIMEOUT,       // Failed - suspend timed out
                                ILLEGALSTATE,  // Failed - already suspended
                                ERROR          // Failed - other error
                              }

    /**
     * Return the appropriate Lock object for the specified LockType.
     * The returned lock has not been locked.  If the LockType is
     * not SHARED or EXCLUSIVE null is returned.
     *
     * @param   type    the LockType
     * @return          the Lock object to use, or null
     */
    public Lock getLock(CommandLock.LockType type) {
        if (type == CommandLock.LockType.SHARED)
            return rwlock.readLock();
        if (type == CommandLock.LockType.EXCLUSIVE)
            return rwlock.writeLock();
        return null;    // no lock
    }

    public void dumpState(Logger logger, Level level) {
        if (logger.isLoggable(level)) {
            logger.log(level, "Current locking conditions are " + rwlock.getReadLockCount()
                         + "/"+ rwlock.getReadHoldCount() + " shared locks"
                         + "and " + rwlock.getWriteHoldCount() + " write lock");
        }
    }

    /**
     * Return the appropriate Lock object for the specified command.
     * The returned lock has not been locked.  If this command needs
     * no lock, null is returned.
     *
     * @param   command the AdminCommand object
     * @return          the Lock object to use, or null if no lock needed
     */
    public Lock getLock(AdminCommand command) {
        CommandLock alock = command.getClass().getAnnotation(CommandLock.class);
        if (alock == null || alock.value() == CommandLock.LockType.SHARED)
            return rwlock.readLock();
        if (alock.value() == CommandLock.LockType.EXCLUSIVE)
            return rwlock.writeLock();
        return null;    // no lock
    }

    /**
     * Return the appropriate Lock object for the specified command.
     * The returned lock has been locked.  If this command needs
     * no lock, null is returned.
     *
     * @param   command the AdminCommand object
     * @param   owner   the authority who requested the lock
     * @return          the Lock object to use, or null if no lock needed
     */
    public Lock getLock(AdminCommand command, String owner) throws
            AdminCommandLockTimeoutException,
            AdminCommandLockException {

        Lock lock = null;
        boolean exclusive = false;
        int timeout = 30; 

        CommandLock alock = command.getClass().getAnnotation(CommandLock.class);

        if (alock == null || alock.value() == CommandLock.LockType.SHARED)
            lock = rwlock.readLock();
        else if (alock.value() == CommandLock.LockType.EXCLUSIVE) {
            lock = rwlock.writeLock();
            exclusive = true;
        }

        if (lock == null) 
            return null; // no lock

        /*
         * If the suspendCommandsLockThread is alive then we were
         * suspended manually (via suspendCommands()) otherwise we
         * may have been locked by a command requiring the EXCLUSIVE
         * lock.
         * If we were suspended via suspendCommands() we don't block
         * waiting for the lock (but we try to acquire the lock just to be
         * safe) - otherwise we set the timeout and try to get the lock.
         */
        if (suspendCommandsLockThread != null &&
            suspendCommandsLockThread.isAlive()) {
            timeout = -1;
        } else {
            boolean badTimeOutValue = false;
            String timeout_s = System.getProperty(
                "com.sun.aas.commandLockTimeOut", "30");

            try {
                timeout = Integer.parseInt(timeout_s);
                if (timeout < 0)
                    badTimeOutValue = true;
            } catch (NumberFormatException e) {
                badTimeOutValue = true;
            }
            if (badTimeOutValue) {
                //XXX: Deal with logger injection attack.
                logger.log(Level.INFO, 
                           "Bad value com.sun.aas.commandLockTimeOut: "
                           + timeout_s + ". Using 30 seconds.");
                timeout = 30;
            }
        }
        
        boolean lockAcquired = false;
        while (!lockAcquired) {
            try {
                if (lock.tryLock(timeout, TimeUnit.SECONDS)) {
                    lockAcquired = true;
                } else {
                    /*
                     * A timeout < 0 indicates the domain was likely already
                     * locked manually but we tried to acquire the lock 
                     * anyway - just in case.
                     */
                    if (timeout >= 0)
                        throw new AdminCommandLockTimeoutException(
                            "timeout acquiring lock",
                            getLockTimeOfAcquisition(),
                            getLockOwner());
                    throw new AdminCommandLockException(
                        getLockMessage(),
                        getLockTimeOfAcquisition(),
                        getLockOwner());
                }
            } catch (java.lang.InterruptedException e) {
                logger.log(Level.FINE, "Interrupted acquiring command lock. ",
                           e);
            }
        }

        if (lockAcquired && exclusive) {
            setLockOwner(owner);
            setLockTimeOfAcquisition(new Date());
        }

        return lock;
    }

    /**
     * Sets the admin user id for the user who acquired the exclusive lock.
     *
     * @param user the admin user who acquired the lock.
     */
    private void setLockOwner(String owner) {
        lockOwner = owner;
    }

    /**
     * Get the admin user id for the user who acquired the exclusive lock.
     * This does not imply the lock is still held.
     *
     * @return  the admin user who acquired the lock
     */
    public synchronized String getLockOwner() {
        return lockOwner;
    }

    /**
     * Sets a message to be returned if the lock could not be acquired.
     * This message can be displayed to the user to indicate why the
     * domain is locked.
     *
     * @param message The message to return.
     */
    private void setLockMessage(String message) {
        lockMessage = message;
    }

    /**
     * Get the message to be returned if the lock could not be acquired.
     *
     * @return  the message indicating why the domain is locked.
     */
    public synchronized String getLockMessage() {
        return lockMessage;
    }

    /**
     * Sets the time the exclusive lock was acquired.
     *
     * @param time the time the lock was acquired
     */
    private void setLockTimeOfAcquisition(Date time) {
        lockTimeOfAcquisition = time;
    }

    /**
     * Get the time the exclusive lock was acquired.  This does not
     * imply the lock is still held.
     *
     * @return the time the lock was acquired
     */
    public synchronized Date getLockTimeOfAcquisition() {
        return lockTimeOfAcquisition;
    }

    /**
     * Indicates if commands are currently suspended.
     * @return 
     */
    public synchronized boolean isSuspended() {
        /*
         * If the suspendCommandsLockThread is alive then we are
         * already suspended or really close to it.
         */
        if (suspendCommandsLockThread != null &&
            suspendCommandsLockThread.isAlive()) {
            return true;
        }

        return false;
    }

    /**
     * Lock the DAS from accepting any commands annotated with a SHARED
     * or EXCLUSIVE CommandLock.  This method will result in the acquisition
     * of an EXCLUSIVE lock.  This method will not return until the lock
     * is acquired, it times out or an error occurs. 
     * 
     * @param   timeout         lock timeout in seconds
     * @param   lockOwner       the user who acquired the lock
     * @return                  status regarding acquisition of the lock
     */
    public synchronized SuspendStatus suspendCommands(
                  long timeout,
                  String lockOwner) {
        return suspendCommands(timeout, lockOwner, "");
    }

    /**
     * Lock the DAS from accepting any commands annotated with a SHARED
     * or EXCLUSIVE CommandLock.  This method will result in the acquisition
     * of an EXCLUSIVE lock.  This method will not return until the lock
     * is acquired, it times out or an error occurs. 
     * 
     * @param   timeout         lock timeout in seconds
     * @param   lockOwner       the user who acquired the lock
     * @param   message         message to return when a command is blocked
     * @return                  status regarding acquisition of the lock
     */
    public synchronized SuspendStatus suspendCommands(
                  long timeout,
                  String lockOwner,
                  String message) {

        /*
         * If the suspendCommandsLockThread is alive then we are
         * already suspended or really close to it.
         */
        if (suspendCommandsLockThread != null &&
            suspendCommandsLockThread.isAlive()) {
            return SuspendStatus.ILLEGALSTATE;
        }

        /*
         * Start a thread to manage the RWLock.
         */
        suspendCommandsLockThread =
            new SuspendCommandsLockThread(timeout, lockOwner,
                                          message);
        try {
            suspendCommandsLockThread.setName(
                "DAS Suspended Command Lock Thread");
            suspendCommandsLockThread.setDaemon(true);
        } catch (IllegalThreadStateException e) {
            return SuspendStatus.ERROR;
        } catch (SecurityException e) {
            return SuspendStatus.ERROR;
        }
        suspendCommandsLockThread.start();

        /*
         * Block until the commandLockThread has acquired the
         * EXCLUSIVE lock or times out trying.
         * We don't want the suspend command to return until we
         * know the domain is suspended.
         * The commandLockThread puts the timeout status on the suspendStatusQ
         * once it has acquired the lock or timed out trying.
         */
        while(true) {
            try {
                return suspendCommandsLockThread.suspendStatus.get();
            } catch (InterruptedException e) {
                // keep trying
            } catch (ExecutionException e) {
                throw new IllegalStateException("Execution exception while waiting for suspend status", e);
            }
        }
    }

    /**
     * Release the lock allowing the DAS to accept commands.  This method
     * may return before the lock is released.  When the thread exits the
     * lock will have been released.  
     *
     * @return  the thread maintaining the lock, null if the DAS is not
     * in a suspended state.  The caller may join() the thread to determine
     * when the lock is released.
     */
    public synchronized Thread resumeCommands() {

        /*
         * We can't resume if commands are not already locked. 
         */
        if (suspendCommandsLockThread == null ||
            suspendCommandsLockThread.isAlive() == false ||
            suspendCommandsLockThread.resumeCommandsSemaphore == null) {
 
            return null;
        }
 
        /*
         * This allows the suspendCommandsLockThread to continue.  This
         * will release the RWLock and allow commands to be processed.
         */
        suspendCommandsLockThread.resumeCommandsSemaphore.release();

        return suspendCommandsLockThread;
    }

    /**
     * The SuspendCommandsLockThread represents a thread which will
     * hold a Read/Write lock across command invocations.  Once the
     * lock is released the thread will exit.
     */
    private class SuspendCommandsLockThread extends Thread {
 
        private Semaphore resumeCommandsSemaphore;
        private long timeout;
        private String lockOwner;
        private String message;
        private final CompletableFuture<SuspendStatus> suspendStatus = new CompletableFuture<>();
 
        public SuspendCommandsLockThread(long timeout,
                                   String lockOwner,
                                   String message) {
            this.timeout = timeout;
            this.lockOwner = lockOwner;
            this.message = message;
            resumeCommandsSemaphore = null;
        }

        @Override
        public void run() {

            /*
             * The EXCLUSIVE lock/unlock must occur in the same thread.
             * The lock may block if someone else currently has the
             * EXCLUSIVE lock. 
             * This deals with both the timeout as well as the 
             * potential for an InterruptedException.
             */
            Lock lock = getLock(CommandLock.LockType.EXCLUSIVE);
            while (true) {
                try {
                    if (lock.tryLock(timeout, TimeUnit.SECONDS)) {
                        try {
                            waitForReleaseSignal();
                        } finally {
                            /*
                             * Resume the domain by unlocking the EXCLUSIVE lock.
                             */
                            lock.unlock();
                        }
                    } else {
                        suspendStatus.complete(SuspendStatus.TIMEOUT);
                    }
                    return;
                } catch (java.lang.InterruptedException e) {
                    logger.log(Level.FINE, "Interrupted acquiring command lock. ", e);
                }
            }
        }

        private void waitForReleaseSignal() {
            setLockOwner(lockOwner);
            setLockMessage(message);
            setLockTimeOfAcquisition(new Date());

            /*
             * A semaphore that is triggered to signal to the thread
             * to release the lock.  This should only be created after
             * the lock has been acquired.
             */
            resumeCommandsSemaphore = new Semaphore(0, true);

            suspendStatus.complete(SuspendStatus.SUCCESS);

            /*
             * We block here waiting to be told to resume.
             */
            while (true) {
                try {
                    resumeCommandsSemaphore.acquire();
                    return;
                } catch (InterruptedException e) {
                    logger.log(Level.FINE, "Interrupted waiting on resume semaphore", e);
                }
            }
        }
    }

}
