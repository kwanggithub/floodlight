package net.bigdb.data.persistmem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.bigdb.data.DataNodeSerializationException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class AsyncWriterDelegateTest {
    // We occasionally have to wait for the quiescence period, so don't make it too long.
    // but also not to short, so we still have a shot of actually batching requests
    private static final int QUIESCENCE_MS = 150;

    // There may be some shortcutting of timers - use some grace period to guard times
    private static final int QUIESCENCE_SAFEGUARD = 100;

    private final static Logger logger =
            LoggerFactory.getLogger(AsyncWriterDelegateTest.class);

    private static final int TIMEOUT = 1000;
    int numWrites = 0;
    private List<Integer> writtenRoots;
    private WriterDelegate<Integer> delegate;
    private WriterDelegate<Integer> asyncWriter;

    @Before
    public void setup() {
        numWrites = 0;
        writtenRoots = Collections.synchronizedList(new ArrayList<Integer>());
        delegate = new WriterDelegate<Integer>() {
            @Override
            public void write(Integer root) throws DataNodeSerializationException {
                logger.debug("MockWriter.write");
                recordWrite(root);
            }

            @Override
            public void start() {
                logger.debug("MockWriter.start");
                synchronized(AsyncWriterDelegateTest.this) {
                    numWrites = 0;
                    writtenRoots.clear();
                }
            }

            @Override
            public void shutdown() {
            }

            @Override
            public long getBytesWritten() {
                return 0;
            }

            @Override
            public long getRealWrites() {
                return 0;
            }

            @Override
            public long getRequestedWrites() {
                return 0;
            }

            @Override
            public File getFile() {
                return null;
            }

            @Override
            public long getNumExceptions() {
                return 0;
            }

            @Override
            public long getMsInWrite() {
                return 0;
            }

            @Override
            public Integer getCurrentRequested() {
                return null;
            }

            @Override
            public Integer getLastWritten() {
                return null;
            }
        };
        asyncWriter = new AsyncWriterDelegate<Integer>(delegate, QUIESCENCE_MS);
        asyncWriter.start();
    }

    protected synchronized void recordWrite(Integer root) {
            numWrites++;
            writtenRoots.add(root);
            notifyAll();
    }

    public synchronized void waitForNumWrites(int i) throws InterruptedException {
        long start = System.currentTimeMillis();
        while(numWrites < i) {
            long remaining = TIMEOUT - (System.currentTimeMillis() - start);
            logger.debug("numWrites: "+numWrites + " remaining: "+remaining);
            if(remaining <= 0)
                fail("Timeout - didn't see "+i+" writes until timeout ");
            wait(remaining);
        }
    }

    public synchronized void waitForWrite(Integer i) throws InterruptedException {
        Stopwatch watch = new Stopwatch().start();
        while(!writtenRoots.contains(i)) {
            long remaining = TIMEOUT - watch.elapsed(TimeUnit.MILLISECONDS);
            logger.debug("numWrites: "+numWrites + " remaining: "+remaining);
            if(remaining <= 0)
                fail("Timeout - didn't see write of root "+i+" until timeout ");
            wait(remaining);
        }
    }

    @After
    public void shutdown() {
        asyncWriter.shutdown();
    }

    @Test
    public void testSyncOneWrite() throws DataNodeSerializationException, InterruptedException {
        assertEquals("initially, numWrites = 0 ", numWrites, 0);
        asyncWriter.write(1);
        waitForNumWrites(1);
        assertEquals("after 1 write executed, numwrites should be 1", 1, numWrites);
    }

    @Test
    public void testSyncTwoWrites() throws DataNodeSerializationException, InterruptedException {
        assertEquals("initially, numWrites = 0 ", numWrites, 0);
        asyncWriter.write(1);
        waitForNumWrites(1);

        Thread.sleep(QUIESCENCE_MS);
        asyncWriter.write(2);

        waitForNumWrites(2);
        assertEquals(2, numWrites);
    }

    @Test
    public void testSyncTwoIndividualWritesQuiesence()
            throws DataNodeSerializationException, InterruptedException {
        assertEquals("initially, numWrites = 0 ", numWrites, 0);
        Stopwatch watch = new Stopwatch();
        watch.start();
        asyncWriter.write(1);
        logger.debug("write 1 scheduled: " + watch.elapsed(TimeUnit.MILLISECONDS));
        waitForWrite(1);
        logger.debug("write 1 suceeded: " + watch.elapsed(TimeUnit.MILLISECONDS));
        asyncWriter.write(2);
        logger.debug("write 2 scheduled: " + watch.elapsed(TimeUnit.MILLISECONDS));
        waitForWrite(2);
        watch.stop();
        logger.debug("write 2 suceeded: " + watch.elapsed(TimeUnit.MILLISECONDS));
        assertEquals(
                "two write requests with waiting should have resulted in exactly 2 writes",
                2, numWrites);
        assertTrue(
                "between the two writes, at least the quiescence period ("
                        + QUIESCENCE_SAFEGUARD + ") should have passed (but was "
                        + watch.elapsed(TimeUnit.MILLISECONDS) + ")",
                watch.elapsed(TimeUnit.MILLISECONDS) >= QUIESCENCE_SAFEGUARD);
    }

    @Test
    public void testSyncTwoWritesQuiesence() throws DataNodeSerializationException,
            InterruptedException {
        assertEquals("initially, numWrites = 0 ", numWrites, 0);
        Stopwatch watch = new Stopwatch();
        watch.start();
        asyncWriter.write(1);
        long timeBefore2 = watch.elapsed(TimeUnit.MILLISECONDS);
        asyncWriter.write(2);

        waitForWrite(2);

        if (numWrites == 2) {
            // writes were written separately
            assertTrue(
                    "between the two individual writes, at least the quiescence period ("
                            + QUIESCENCE_MS + ") should have passed",
                    watch.elapsed(TimeUnit.MILLISECONDS) > QUIESCENCE_SAFEGUARD);
        } else if (numWrites == 1) {
            assertTrue("between the two batched writes, at most (" + QUIESCENCE_MS
                    + ") should have passed", timeBefore2 < QUIESCENCE_MS);
        } else {
            fail("unexpected number of writes: " + numWrites);
        }
    }

    @Test
    public void testManyWritesQuiesence() throws DataNodeSerializationException,
            InterruptedException {
        assertEquals("initially, numWrites = 0 ", numWrites, 0);

        Stopwatch watch = new Stopwatch().start();
        int largestWrite = 5000;
        for (int i = 0; i <= largestWrite; i++) {
            asyncWriter.write(i);
            if (i % (largestWrite / 3) == 0) {
                Thread.sleep(QUIESCENCE_MS);
            }
        }

        waitForWrite(largestWrite);
        watch.stop();
        long numOfQuiescencePeriodsPassed =
                (watch.elapsed(TimeUnit.MILLISECONDS) / QUIESCENCE_MS) + 1;
        logger.info("For " + asyncWriter.getRequestedWrites() + " write requests in "
                + watch.elapsed(TimeUnit.MILLISECONDS) + " ms ("
                + numOfQuiescencePeriodsPassed + " periods), got " + numWrites
                + " real writes");

        long maxRealWriteRequests = numOfQuiescencePeriodsPassed + 1;
        assertTrue("should see at most " + maxRealWriteRequests
                + " real write requests (saw " + numWrites + ")",
                numWrites <= maxRealWriteRequests);
    }

    public long getBytesWritten() {
        return 0;
    }

    public long getRealWrites() {
        return 0;
    }

    public long getRequestedWrites() {
        return 0;
    }

    public File getFile() {
        return null;
    }

    public synchronized long getNumExceptions() {
        return 0;
    }

    public synchronized long getMsInWrite() {
        return 0;
    }
}
