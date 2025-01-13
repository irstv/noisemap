/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;
import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.ProgressVisitor;

import java.util.concurrent.*;

/**
 *
 * @author Nicolas Fortin
 */
public class ThreadPool extends ThreadPoolExecutor {
    ProgressVisitor progressVisitor = new EmptyProgressVisitor();

    /**
     * Default constructor. Set CorePoolSize size to 32 Set Maximum pool size to
     * 256 Set Keep Alive Time to 60 seconds
     */
    public ThreadPool() {
        super(32, 256, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Default constructor. Set CorePoolSize size to 32 Set Maximum pool size to
     * 256 Set Keep Alive Time to 60 seconds
     */
    public ThreadPool(int queueSize) {
        super(32, 256, 60, TimeUnit.SECONDS,
                queueSize < 0 ? new LinkedBlockingQueue<Runnable>()
                        : (queueSize == 0 ? new SynchronousQueue<Runnable>()
                        : new ArrayBlockingQueue<Runnable>(queueSize)));
    }

    /**
     * Size constructor.
     *
     */
    public ThreadPool(int corePoolSize, int maximumPoolSize,
                      long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Size constructor.
     *
     */
    public ThreadPool(int corePoolSize, int maximumPoolSize,
                      long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * Size constructor.
     *
     */
    public ThreadPool(int corePoolSize, int maximumPoolSize,
                      long keepAliveTime, TimeUnit unit,
                      BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                handler);
    }

    /**
     * Size constructor.
     *
     */
    public ThreadPool(int corePoolSize, int maximumPoolSize,
                      long keepAliveTime, TimeUnit unit,
                      BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                      RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, handler);
    }

    /**
     * Size constructor.
     *
     */
    public ThreadPool(int corePoolSize, int maximumPoolSize,
                      long keepAliveTime, TimeUnit unit,
                      BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory);
    }

    public void setProgressVisitor(ProgressVisitor progressVisitor) {
        this.progressVisitor = progressVisitor;
    }

    /**
     * @return True if poolSize is inferior of maximum pool size
     */
    public boolean hasAvaibleQueueSlot() {
        return this.getQueue().size() + this.getActiveCount() < this
                .getMaximumPoolSize();
    }

    /**
     *
     * @return Remaining threads Running and queued
     */
    public int getRemainingTasks() {
        return this.getQueue().size() + this.getActiveCount();
    }

    /**
     * Wait for free queue slot if poolSize is superior or equal of maximum pool
     * size then executes the given task sometime in the future. The task may
     * execute in a new thread or in an existing pooled thread. If the task
     * cannot be submitted for execution, either because this executor has been
     * shutdown or because its capacity has been reached, the task is handled by
     * the current RejectedExecutionHandler.
     *
     * @param command
     */
    public void executeBlocking(Runnable command) {
        while (this.getQueue().size() + this.getActiveCount() >= this
                .getMaximumPoolSize() && !progressVisitor.isCanceled()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return; // do not execute
            }
        }
        super.execute(command);
    }

    /**
     * Wait for free queue slot if poolSize is superior or equal of maximum pool
     * size then executes the given task sometime in the future. The task may
     * execute in a new thread or in an existing pooled thread. If the task
     * cannot be submitted for execution, either because this executor has been
     * shutdown or because its capacity has been reached, the task is handled by
     * the current RejectedExecutionHandler.
     *
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submitBlocking(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        executeBlocking(ftask);
        return ftask;
    }
}
