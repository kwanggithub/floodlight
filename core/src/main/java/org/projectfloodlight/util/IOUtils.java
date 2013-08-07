package org.projectfloodlight.util;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IO-related static helper methods.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public final class IOUtils {
    protected static final Logger logger = 
            LoggerFactory.getLogger(IOUtils.class);

    // don't instantiate
    private IOUtils() {
    }

    /**
     * Try to atomically mv over src to dst, overwriting dst (ideally)
     * atomically. If this fails, it deletes the dst file and retries the rename
     *
     * @param src
     *            The source file to be renamed
     * @param dst
     *            the dst file that should be overwritten as atomically as
     *            possible
     * @throws IOException
     */
    public static void mvAndOverride(File src, File dst) throws IOException {
        if (src.renameTo(dst))
            return;

        // on windows, renaming a file to an existing file fails
        // try to remove first. Note that this leaves a (short)
        // window open where the file is not present.
        if (dst.exists()) {
            if (!dst.delete()) {
                throw new IOException("Could not remove existing file " + dst);
            }
            if (!src.renameTo(dst)) {
                throw new IOException("Could not rename file " + src + " to " + dst);
            }
        } else {
            throw new IOException("Could not rename file " + src + " to " + dst);
        }
    }

    /**
     * ensure sessionDir exists and is a writeable directory. If sessionDir does
     * not exists, create it. Throw an exception if that fails. If sessionDir
     * exists but is not a directory, throw an exception.
     *
     * @param sessionDir
     * @throws IOException
     */
    public static void ensureDirectoryExistsAndWritable(File sessionDir) throws IOException {
        if (!sessionDir.exists()) {
            if (!sessionDir.mkdir()) {
                throw new IOException("Could not create directory " + sessionDir);
            }
        }

        if (!sessionDir.isDirectory()) {
            throw new IOException("File " + sessionDir + " exists, but is not directory");
        }

        if (!sessionDir.canWrite()) {
            throw new IOException("Directory " + sessionDir + " is not writeable");
        }
    }

    /**
     * recursively delete a directory. Careful: this may interact unexpectely
     * with sym links in Windows. Better only use in testing.
     *
     * @param dir
     * @throws IOException
     */
    public static void deleteRecursively(File fileOrDir) throws IOException {
        if (fileOrDir.isDirectory()) {
            for (File f : fileOrDir.listFiles()) {
                deleteRecursively(f);
            }
        }
        if (fileOrDir.exists() && !fileOrDir.delete()) {
            throw new IOException("Could not delete: " + fileOrDir);
        }
    }
}
