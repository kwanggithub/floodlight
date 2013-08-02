package net.bigdb.data.persistmem;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.bigdb.data.DataNodeSerializationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * asynchronously writes stuff using a single asynchronous execution thread. The
 * actual write is performed by a delegate writer passed in. Writes are
 * asynchronously scheduled to be at least quiesenceMs milliseconds apart.
 * Errors are logged, but not handled otherwise
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @param <T>
 *            the type of data that's to be written.
 */
class AsyncWriterDelegate<T> implements WriterDelegate<T> {
    private static Logger logger = LoggerFactory.getLogger(AsyncWriterDelegate.class);

    private final ScheduledExecutorService executor;

    /** the quiescence period */
    private final long quiescenceMs;

    /**
     * whether an asynchrounous write has been scheduled (but not started
     * executing)
     */
    private boolean asyncScheduled = false;
    /** time the last asynchronous write started executing */
    private long lastWrite;
    /** current 'requested' value of the data to be written */
    private T requestedRoot;
    /** current effective data that was written by the asynchronous writer */
    private T writtenRoot;

    /** delegate writer that does the actual I/O work */
    private final WriterDelegate<T> delegate;

    /** runnable that gets scheduled */
    private final WriterRunnable doWriteRunnable;

    /** how many writes where requested, for statistics */
    private long writesRequested = 0;

    /**
     * creates a new AsyncDataNodeWriter. The actual IO work is delegated to
     * delegate. Writes are spaced out by quiescenceMs.
     *
     * @param delegate
     * @param quiescenceMs
     */
    public AsyncWriterDelegate(WriterDelegate<T> delegate, long quiescenceMs) {
        this.delegate = delegate;
        this.quiescenceMs = quiescenceMs;
        this.lastWrite = 0;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.doWriteRunnable = new WriterRunnable();
    }

    /**
     * asynchronously write out a message. Records root as the current requested
     * root. If no message has been previously written, the last write is longer
     * than quiescenceMs ago, will schedule an asynchronous write for immediate
     * execution. Else, if no write is currently scheduled, will schedule a
     * write at the end of the current quiescence period.
     */
    @Override
    public synchronized void write(T root) throws DataNodeSerializationException {
        long timeSinceLastWrite = System.currentTimeMillis() - lastWrite;

        this.requestedRoot = root;
        writesRequested++;
        if (!asyncScheduled) {
            long timeLeft = quiescenceMs - timeSinceLastWrite;
            if (timeLeft > 0) {
                if (logger.isDebugEnabled())
                    logger.debug("Scheduling for delayed write in " + timeLeft + " ms");
                executor.schedule(doWriteRunnable, timeLeft, TimeUnit.MILLISECONDS);
            } else {
                if (logger.isDebugEnabled())
                    logger.debug("Scheduling for immediate write");
                executor.execute(doWriteRunnable);
            }
            asyncScheduled = true;
        }
    }

    /** Runnable implementenation that does the write */
    class WriterRunnable implements Runnable {

        @Override
        public void run() {
            T localRequestedRoot = null;
            if (logger.isDebugEnabled())
                logger.debug("WriterRunnable.run()");
            synchronized (AsyncWriterDelegate.this) {
                asyncScheduled = false;
                lastWrite = System.currentTimeMillis();
                // using reference comparison for now, DataNode doesn't have a
                // useful equals (yet)
                if (requestedRoot != writtenRoot) {
                    localRequestedRoot = requestedRoot;
                }
            }
            if (localRequestedRoot != null) {
                try {
                    delegate.write(localRequestedRoot);
                } catch (Exception e) {
                    logger.warn("Error on asynchronous save of " + localRequestedRoot
                            + ": " + e.getMessage(), e);
                }
                writtenRoot = localRequestedRoot;
            }
        }
    }

    /** NOOP - executors are directly */
    @Override
    public void start() {
    }

    @Override
    /** shut down the executor */
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public long getRequestedWrites() {
        return writesRequested;
    }

    @Override
    public long getRealWrites() {
        return delegate.getRealWrites();
    }

    @Override
    public long getBytesWritten() {
        return delegate.getBytesWritten();
    }

    @Override
    public File getFile() {
        return delegate.getFile();
    }

    @Override
    public synchronized long getNumExceptions() {
        return delegate.getNumExceptions();
    }

    @Override
    public synchronized long getMsInWrite() {
        return delegate.getMsInWrite();
    }

    @Override
    public T getCurrentRequested() {
        return requestedRoot;
    }

    @Override
    public T getLastWritten() {
        // TODO Auto-generated method stub
        return writtenRoot;
    }

}
