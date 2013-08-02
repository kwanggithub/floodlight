package net.floodlightcontroller.util;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

public class DirLockTest {
    private File dir;

    @Before
    public void setup() throws IOException {
        dir = Files.createTempDir();
    }

    @After
    public void teardown() throws IOException {
        IOUtils.deleteRecursively(dir);
    }

    @Test
    public void testLock() throws IOException {
        DirLock lock = DirLock.lockDir(dir);
        lock.unlock();
    }
    @Test
    public void testLockUnlockLock() throws IOException {
        DirLock lock = DirLock.lockDir(dir);
        lock.unlock();
        DirLock lock2 = DirLock.lockDir(dir);
        lock2.unlock();
    }

    @SuppressWarnings("unused")
    @Test
    public void testLock2Threads() throws IOException {
        DirLock lock = DirLock.lockDir(dir);
        try {
            DirLock lock2 = DirLock.lockDir(dir);
            fail("Expected a lock on second invocation");
        } catch(IOException e) {
            // this is what we want
        } finally {
            lock.unlock();
        }
    }


}
