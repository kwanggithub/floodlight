package net.floodlightcontroller.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A neat little helper that watches a file for changes. When the watched file as changed (as indicated by File.lastModified()), the watcher calls
 *  a Loader delegate. Once that has successfully returned, it calls an acceptor.
 *
 *  All Watchers, loaders and Acceptors run on a single Timer.
 *
 *  Example way to use this:
 *
 *  <pre>
 *  FileWatcher.Builder.create()
 *      .safetyPeriod(500)
 *      .watchInterval(1000)
 *      .onChanged(new Loader<MyObject>() {
 *          MyObject load(InputStream in) {
 *             return MyObject.load(in);
 *          }
 *      }.onSuccessfullyReloaded(new Acceptor<MyObject>()) {
 *          void accept(MyObject obj) {
 *              setNewUsedObject(obj);
 *           }
 *      }.watchFile(file);
 *
 * </pre>
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 *
 */
public interface FileWatcher {
    /** stop this file watcher forever */
    public void stop();

    /** pause this filewatcher for the moment. */
    public void pause();

    /** unpause and resume this filewatcher. */
    public void resume();

    /** Interface for a Loader. This loads the new resource from the InputStream.
     *  Called from the watcher thread.
     **/
    public interface Loader<T> {
        /** load and return the resource fromthe Input Stream */
        T load(InputStream in) throws IOException;
    }

    /** Interface for an Acceptor. This accepts and sets the new value.
     *  Called from the watcher thread.
     *  <p>
     *  <b>Note:</b> Will typically have to employ synchronized or volatile to ensure that
     *  the new value is picked up by the other threads.
     **/
    public interface Acceptor<T> {
        void accept(T obj);
    }

    /** Builder Object for FileWatchers. Use this to build your watcher, then start() to start it */
    public class Builder<T> {
        protected static Logger logger = LoggerFactory.getLogger(FileWatcherTask.class);

        private boolean mustExist;
        private boolean loadOnStart;
        private long modifiedTime;
        private long safetyMillisecs;
        private Loader<T> loader;
        private Acceptor<T> acceptor;
        private long interval;

        /** private constructor. use .forFile() */
        private Builder() {
            this.mustExist = false;
            this.loadOnStart = false;
            this.interval = 2000;
            this.safetyMillisecs = 500;
        }

        /** create and return a new Builder with default settings */
        public static Builder<Object> create() {
            return new Builder<Object>();
        }

        /** set whether the file must exist during creation of the watcher.
         *
         **/
        public Builder<T> mustExist(boolean mustExist) {
            this.mustExist = mustExist;
            return this;
        }

        /** set whether the file must exist during creation of the watcher.
         **/
        public Builder<T> loadOnStart(boolean loadOnStart) {
            this.loadOnStart = loadOnStart;
            return this;
        }

        public Builder<T> initialModifiedTime(long modifiedTime) {
            this.modifiedTime = modifiedTime;
            return this;
        }

        public Builder<T> watchInterval(long interval) {
            if(interval <= 0)
                throw new IllegalArgumentException("interval must be > 0");
            this.interval = interval;
            return this;
        }

        public Builder<T> safetyPeriod(long safetyMillisecs) {
            this.safetyMillisecs = safetyMillisecs;
            return this;
        }

        public <F extends T> Builder<F> onChanged(Loader<F> loader) {
            // safely limit the type annotation in the return type
            @SuppressWarnings("unchecked")
            Builder<F> me = (Builder<F>) this;
            me.loader = loader;
            return me;
        }

        public <F extends T> Builder<F> onSuccessfullyReloaded(Acceptor<F> acceptor) {
            // safely limit the type annotation in the return type
            @SuppressWarnings("unchecked")
            Builder<F> me = (Builder<F>) this;
            me.acceptor = acceptor;
            return me;
        }

        public FileWatcher watchFile(File file) throws IOException {
            if (mustExist && !file.exists()) {
                throw new FileNotFoundException("File " + file + " not found");
            }
            FileWatcherTask<T> task =
                    new FileWatcherTask<T>(file, loader, acceptor, interval,
                            safetyMillisecs, modifiedTime);
            if (loadOnStart) {
                task.load();
            }
            FileWatcherScheduler.getInstance().schedule(task);
            return task;
        }
    }
}

class FileWatcherTask<T> extends TimerTask implements FileWatcher {
    protected static Logger logger = LoggerFactory.getLogger(FileWatcherTask.class);

    private final File file;
    private final Loader<T> loader;
    private final Acceptor<T> acceptor;

    private final long interval;
    private final long safetyMillisecs;
    private long modifiedTime;

    private volatile boolean paused;

    public FileWatcherTask(File file, Loader<T> loader, Acceptor<T> acceptor,
            long interval, long safetyMillisecs, long modifiedTime) {
        super();
        if(file == null)
            throw new NullPointerException("file must be set");
        if(loader == null)
            throw new NullPointerException("loader must be set");
        if(acceptor == null)
            throw new NullPointerException("acceptor must be set");
        if(interval <= 0)
            throw new IllegalArgumentException("interval must be > 0");

        this.file = file;
        this.loader = loader;
        this.acceptor = acceptor;
        this.interval = interval;
        this.safetyMillisecs = safetyMillisecs;

        if (modifiedTime == 0 && file.exists())
            this.modifiedTime = file.lastModified();
        else
            this.modifiedTime = modifiedTime;

        this.paused = false;
    }

    public void load() throws IOException {
        if (file.exists()) {
            long newModifiedTime = file.lastModified();

            T res = loader.load(new FileInputStream(file));
            acceptor.accept(res);

            if (newModifiedTime > this.modifiedTime)
                this.modifiedTime = newModifiedTime;
        }
    }

    @Override
    public void run() {
        if (this.paused)
            return;
        long currentModified = file.lastModified();
        if (currentModified > this.modifiedTime
                && currentModified < (System.currentTimeMillis() - safetyMillisecs)) {
            try {
                load();
            } catch (Throwable t) {
                logger.warn("Exception on reloading file " + file + ". Canceling", t);
                cancel();
            }
        }
    }

    @Override
    public void stop() {
        this.cancel();
    }

    @Override
    public void pause() {
        this.paused = true;

    }

    @Override
    public void resume() {
        this.paused = false;
    }

    public long getInterval() {
        return interval;
    }
}

class FileWatcherScheduler {
    static class SingletonHolder {
        static final FileWatcherScheduler INSTANCE = new FileWatcherScheduler();
    }

    public static FileWatcherScheduler getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private final Timer timer;

    private FileWatcherScheduler() {
        timer = new Timer("File Watcher Timer", true);
    }

    public void schedule(FileWatcherTask<?> entry) {
        timer.scheduleAtFixedRate(entry, entry.getInterval(), entry.getInterval());
    }

    void stopAll() {
        timer.purge();
    }
}
