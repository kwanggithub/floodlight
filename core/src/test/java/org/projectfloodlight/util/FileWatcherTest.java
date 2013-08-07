package org.projectfloodlight.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.projectfloodlight.util.FileWatcher;
import org.projectfloodlight.util.FileWatcherScheduler;
import org.projectfloodlight.util.FileWatcher.Acceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatcherTest {
    protected static Logger logger = LoggerFactory.getLogger(FileWatcherTest.class);

    @Rule
    public TemporaryFolder basePath = new TemporaryFolder();
    
    class MockFile extends File {
        private static final long serialVersionUID = 1L;

        private volatile long lastModified;
        private volatile boolean exists;

        public MockFile(String pathname) {
            super(pathname);
            lastModified = super.lastModified();
            exists = super.exists();
        }

        public void touch() {
            lastModified = System.currentTimeMillis();
            exists = true;
        }

        @Override
        public boolean exists() {
            return exists;
        }

        @Override
        public long lastModified() {
            return lastModified;
        }

        @Override
        public synchronized boolean delete() {
            boolean res = super.delete();
            if(res)
                exists = false;
            return res;
        }
    }

    class WaitResult {
        private boolean done = false;


        public synchronized void done() {
            done = true;
            this.notifyAll();
        }

        public synchronized boolean waitOn(long timeout) throws InterruptedException {
            this.wait(timeout);
            return done;
        }

        public synchronized boolean isDone() {
            return done;
        }

        public synchronized void comeUndone() {
            done = false;
        }

        public void assertWait() throws InterruptedException {
            if(!waitOn(1000)) {
                fail("result not called after 1 sec");
            }
        }
    }

    private MockFile file;
    private FileWatcher.Loader<Boolean> loader;
    private Acceptor<Boolean> acceptor;
    private WaitResult result;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws IOException {
       logger.debug("setup");
       File tempFile = basePath.newFile("watcher_test..txt");
       file = new MockFile(tempFile.getAbsolutePath());
       logger.debug("created temp tile: "+file);

       loader = EasyMock.createMock(FileWatcher.Loader.class);
       result = new WaitResult();
       acceptor = new FileWatcher.Acceptor<Boolean>() {
           @Override
           public void accept(Boolean obj) {
               result.done();
           }
       };
    }

    @After
    public void teardown() {
        logger.debug("teardown");
        file.delete();
        FileWatcherScheduler.getInstance().stopAll();
    }

    @Test
    public void testCreateNoOp() throws IOException {
        logger.debug("testCreateNoOp");
        @SuppressWarnings("unchecked")
        FileWatcher.Loader<Object> loader = EasyMock.createMock(FileWatcher.Loader.class);
        @SuppressWarnings("unchecked")
        FileWatcher.Acceptor<Object> acceptor = EasyMock.createMock(FileWatcher.Acceptor.class);

        EasyMock.replay(loader);

        FileWatcher watcher = FileWatcher.Builder.create().onChanged(loader).onSuccessfullyReloaded(acceptor).watchFile(file);
        watcher.stop();
    }

    @Test
    public void testCreateNotMustExistOK() throws IOException {
        file.delete();
        FileWatcher watcher = FileWatcher.Builder.create().onChanged(loader).onSuccessfullyReloaded(acceptor).watchFile(file);
        watcher.stop();
    }

    @Test(expected=FileNotFoundException.class)
    public void testCreateMustExistFail() throws IOException {
        file.delete();
        FileWatcher watcher = FileWatcher.Builder.create().onChanged(loader).onSuccessfullyReloaded(acceptor).mustExist(true).watchFile(file);
        watcher.stop();
    }

    @Test
    public void testCreateLoadOnStart() throws IOException {
        FileWatcher watcher = FileWatcher.Builder.create().onChanged(loader).onSuccessfullyReloaded(acceptor).loadOnStart(true).watchFile(file);
        assertTrue(result.isDone());
        watcher.stop();
    }

    @Test
    public void testCreateSafeInterval() throws IOException, InterruptedException {
        logger.debug("testCreateLoadonStartAndTouch");
        EasyMock.expect(loader.load(EasyMock.<InputStream>anyObject())).andReturn(Boolean.TRUE).times(2);
        EasyMock.replay(loader);

        int safetyPeriod = 150;
        FileWatcher watcher = FileWatcher.Builder.create().onChanged(loader).onSuccessfullyReloaded(acceptor).loadOnStart(true).safetyPeriod(safetyPeriod).watchInterval(50).watchFile(file);
        assertTrue(result.isDone());
        result.comeUndone();
        long start = System.currentTimeMillis();
        file.touch();
        result.assertWait();
        long end = System.currentTimeMillis();
        assertTrue ( end - start > safetyPeriod);
        watcher.stop();
    }

    @Test
    public void testCreateLoadOnStartAndTouch() throws IOException, InterruptedException {
        logger.debug("testCreateLoadonStartAndTouch");
        EasyMock.expect(loader.load(EasyMock.<InputStream>anyObject())).andReturn(Boolean.TRUE).times(2);
        EasyMock.replay(loader);

        FileWatcher watcher = FileWatcher.Builder.create().onChanged(loader).onSuccessfullyReloaded(acceptor).loadOnStart(true).safetyPeriod(1).watchInterval(50).watchFile(file);
        assertTrue(result.isDone());
        result.comeUndone();
        file.touch();
        result.assertWait();
        watcher.stop();
    }

    @Test
    public void testCreateTouch() throws IOException, InterruptedException {
        logger.debug("testCreateTouch");
        EasyMock.expect(loader.load(EasyMock.<InputStream>anyObject())).andReturn(Boolean.TRUE);
        EasyMock.replay(loader);

        FileWatcher.Builder.create().onChanged(loader).onSuccessfullyReloaded(acceptor).watchInterval(50).safetyPeriod(1).watchFile(file);
        file.touch();
        result.assertWait();
    }


}
