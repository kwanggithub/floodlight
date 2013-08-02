package net.bigdb.auth.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.List;

import net.floodlightcontroller.util.IOUtils;

import org.junit.After;
import org.junit.Before;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;
import com.google.common.io.Files;

public class FileSessionManagerTest extends AbstractSessionManagerTest<FileSessionManager> {
    protected static Logger logger =
        LoggerFactory.getLogger(FileSessionManagerTest.class);

    private File storeDir;

    @Override
    @Before
    public void setup() {
        storeDir = Files.createTempDir();
    }

    @Override
    public FileSessionManager getSessionManager(String cacheSpec, Ticker timeSource) throws Exception {
        return new FileSessionManager(storeDir, cacheSpec, timeSource);
    }

    @Override
    protected void postTestBasic2Session(FileSessionManager manager, Session s1, Session s2) throws Exception {
        assertCountSessionFiles(2);
        assertTrue("postTestMaxSize: session file for session id "+s1.getId() + " should exist", sessionFile(s1).exists());
        manager.stop();

        // reload sesssions from the filesystem into a new manager
        FileSessionManager newManager = getSessionManager(null, null);
        assertEquals(2, Iterables.size(newManager.getActiveSessions()));

        checkRetrievedSession(s1, newManager.getSessionForId(s1.getId()));
        checkRetrievedSession(s2, newManager.getSessionForId(s2.getId()));

        // change ip for session
        newManager.getSessionForId(s2.getId()).setLastAddress("192.168.0.100");
        // this should trigger the session to be persisted again by newManager
        newManager.stop();
        FileSessionManager newerManager = getSessionManager(null, null);
        assertEquals("192.168.0.100", newerManager.getSessionForId(s2.getId()).getLastAddress());


    }

    @Override
    protected void postTestMaxSize5(FileSessionManager manager, List<Session> sessions) {
        assertCountSessionFiles(5);

        for(int i=0; i<sessions.size(); i++) {
            Session s = sessions.get(i);
            if(i != 0)
                assertTrue("postTestMaxSize: session file for session # "+i +" id "+s.getId() + " should exist", sessionFile(s).exists());
            else
                assertFalse("postTestMaxSize: session file for session # "+i +" id "+s.getId() + " should not exist", sessionFile(s).exists());
        }
    }

    @Override
    protected void postTest2SessionsRemove2nd(FileSessionManager manager, Session s1,
            Session s2) {
        assertTrue(sessionFile(s1).exists());
        assertFalse(sessionFile(s2).exists());
    }

    protected File sessionFile(Session s) {
        return new File(storeDir, s.getId() + ".json");
    }

    private void assertCountSessionFiles(int i) {
        assertEquals(i, storeDir.listFiles(jsonFilter).length);
    }

    FileFilter jsonFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.getName().endsWith(".json");
        }
    };

    @After
    public void teardown() throws IOException {
        IOUtils.deleteRecursively(storeDir);
    }

}
