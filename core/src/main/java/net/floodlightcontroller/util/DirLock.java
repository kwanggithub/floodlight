package net.floodlightcontroller.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locks a directory for exclusive use. Uses a hybrid mechanism to protect both
 * against against other locks with the JVM as well as other processes:
 * <ul>
 * <li>a static hashset of canonical locked paths</li> (protects against other
 * locks on the same VM)
 * <li>a file system FileLock on the File "LOCK" in the directory</li> (protects
 * against other VMs/processes).
 * </ul>
 * Threadsafe, obviously.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class DirLock {
    protected static final Logger logger = 
            LoggerFactory.getLogger(DirLock.class);

    private final static Set<String> inProcessLockedCanonicalPaths = new HashSet<String>();
    private final File dir;
    private final File lockFile;
    private FileOutputStream lockFileStream;
    private FileLock lock;

    public DirLock(File dir) throws IOException {
        this.dir = dir;
        IOUtils.ensureDirectoryExistsAndWritable(dir);
        lockFile = new File(dir, "LOCK");
    }

    /** lock the dir lock both locally and globally. NOOP if the dirlock is already locked */
    public synchronized void lock() throws IOException {
        synchronized (inProcessLockedCanonicalPaths) {
            if (lock==null) {
                String absName = lockFile.getCanonicalPath();
                if (inProcessLockedCanonicalPaths.contains(absName))
                    throw new IOException("Store dir " + dir
                            + " already locked by this process");

                lockFileStream = new FileOutputStream(lockFile);
                FileChannel channel = lockFileStream.getChannel();
                lock = channel.tryLock();
                if (lock == null)
                    throw new IOException("Could not lock store dir " + dir
                            + " (lock file " + lockFile + ")");
                inProcessLockedCanonicalPaths.add(absName);
            }
        }
    }

    /** release the dir lock both locally and globally. NOOP if the dirlock is already unlocked */
    public synchronized void unlock() throws IOException {
        if (lock != null) {
            synchronized (inProcessLockedCanonicalPaths) {
                String absName = lockFile.getCanonicalPath();
                lock.release();
                lock = null;
                lockFileStream.close();
                if (!lockFile.delete()) {
                    logger.info("Could not delete lock file " + lockFile);
                }
                inProcessLockedCanonicalPaths.remove(absName);
            }
        }
    }

    /** convenience method: create + lock + return a DirLock */
    public static DirLock lockDir(File dir) throws IOException {
        DirLock lock = new DirLock(dir);
        lock.lock();
        return lock;
    }
}
