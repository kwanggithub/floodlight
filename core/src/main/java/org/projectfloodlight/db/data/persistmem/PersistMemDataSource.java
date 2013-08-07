package org.projectfloodlight.db.data.persistmem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import javax.management.JMException;
import javax.management.ObjectName;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSerializationException;
import org.projectfloodlight.db.data.DataNodeUtilities;
import org.projectfloodlight.db.data.DataSource;
import org.projectfloodlight.db.data.memory.MemoryDataSource;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.service.BigDBOperation;
import org.projectfloodlight.db.service.internal.DataNodeJsonHandler;
import org.projectfloodlight.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;


/** A JSON persisted Memory DataSource. Persists data nodes in a flat json file denoted by the property 'file'.
 *  Does not use file locking - relies on UNIX atomic file rename sematics for correct operation.
 *
 *  Writes can be configured to be asynchronous (asyncWrites).
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class PersistMemDataSource extends MemoryDataSource implements PersistMemDataSourceMBean {
    private final static Logger logger = LoggerFactory.getLogger(PersistMemDataSource.class);

    public static final String PROP_KEY_FILE = "file";
    public static final String PROP_KEY_ASYNC_WRITES = "asyncWrites";
    public static final String PROP_KEY_QUIESCENCE_INTERVAL_MS = "quiescenseIntervalMs";

    static final int DEFAULT_QUIESCENCE_MS = 250;

    private final File file;
    private final DataNodeJsonHandler jsonHandler;

    private final boolean asyncWrites;
    private final int asyncQuiescenseMs;

    private final WriterDelegate<DataNode> writer;
    private SoftReference<DataNode> readRoot;

    public PersistMemDataSource(String name, boolean config, 
                                Schema schema, Map<String, String> properties)
            throws BigDBException {
        super(name, config, schema);
        if (properties == null) {
            throw new IllegalArgumentException(
                    "Must specify properties for PersistMemDataSource");
        }

        String fileName;
        if (!properties.containsKey(PROP_KEY_FILE)
            || Strings.isNullOrEmpty(fileName = properties.get(PROP_KEY_FILE))) {
            throw new IllegalArgumentException("must specify file for PersistMemDataSource");
        }

        this.file = new File(fileName);
        this.asyncWrites = Boolean.parseBoolean(properties.get(PROP_KEY_ASYNC_WRITES));
        this.asyncQuiescenseMs =
                properties.containsKey(PROP_KEY_QUIESCENCE_INTERVAL_MS) ? Integer
                        .parseInt(properties.get(PROP_KEY_QUIESCENCE_INTERVAL_MS))
                        : DEFAULT_QUIESCENCE_MS;

        this.writer =
                asyncWrites ? new AsyncWriterDelegate<DataNode>(new SyncDataNodeWriterDelegate(
                        this.file), this.asyncQuiescenseMs) : new SyncDataNodeWriterDelegate(
                        this.file);

        this.jsonHandler =
                new DataNodeJsonHandler(Collections.<String, DataSource> singletonMap(name,
                        this));
    }

    public void read() throws BigDBException, IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            logger.info("PersistMemDataSource - reading persisted config from " + file);
            root = jsonHandler.readDataNode(inputStream, getRootSchemaNode(), name);

            readRoot = new SoftReference<DataNode>(root);
            if(logger.isTraceEnabled())
                logger.trace("PersistMemDataSource - read persisted config (digest=" + DataNodeUtilities.getDigestValueStringSafe(root) + ")");
        }
    }

    @Override
    protected void mutateData(BigDBOperation operation,
            Query query, DataNode replaceDataNode,
            AuthContext authContext) throws BigDBException {

        if(getState() != State.RUNNING)
            throw new IllegalStateException("Datasource not in state RUNNING, but "+getState() + ". Make sure BigDB is listed in floodlight.properties before any modules that depend on it.");

        super.mutateData(operation, query, replaceDataNode, authContext);

        writer.write(root);
    }

    @Override
    public void setRoot(DataNode root) throws BigDBException {
        DataNode oldRoot = this.root;
        super.setRoot(root);

        if(logger.isTraceEnabled()) {
            logger.trace(this.toString() + ": setRoot(): old_root(digest=" +
                        DataNodeUtilities.getDigestValueStringSafe(oldRoot) +
                        " -> new_root(digest=" +
                        DataNodeUtilities.getDigestValueStringSafe(root)+")", new Exception());

            logger.trace("setRoot(): old_root: " + oldRoot);
            logger.trace("setRoot(): new_root: " + root);

            logger.trace("setRoot: stack trace: "+Joiner.on("\n").join(new Exception().getStackTrace()));
        }

        writer.write(root);
    }

    @Override
    public synchronized void startup() throws BigDBException {
        try {
            ObjectName objectName = new ObjectName("org.projectfloodlight.db:type=PersistMemDataSource,name="+file.getPath());
            logger.debug("Registering as MBean "+objectName);
            ManagementFactory.getPlatformMBeanServer().registerMBean(this, objectName);
        } catch (JMException e) {
            logger.debug("Error exposing MBean for Persistent Storage");
            if(logger.isTraceEnabled())
                logger.trace("Stacktrace: ", e);
        }
        super.startup();

        if (file.exists()) {
            try {
                read();
            } catch (IOException e) {
                throw new BigDBException("Eror reading persisted database from "+file, e);
            }
        } else {
            logger.debug("PersistMemDataSource - starting with empty database - " + file + " does not exist");
        }

        writer.start();
    }

    @Override
    public synchronized void shutdown() throws BigDBException {
        super.shutdown();
        writer.shutdown();
    }

    /** synchronous datanode writer. Writes the datanode out to
     *  a flat JSON file. Relies on UNIX atomic renaming sematics
     *  for atomicity.
     *
     * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
     */
    class SyncDataNodeWriterDelegate implements WriterDelegate<DataNode> {
        private final File file;
        private final Random random;

        // statistics
        private long realWrites = 0;
        private long requestedWrites = 0;
        private long bytesWritten = 0;
        private long numExceptions = 0;
        private long nsInWrite = 0;

        private DataNode requestedRoot;
        private DataNode writtenRoot;

        public SyncDataNodeWriterDelegate(File file) {
            this.file = file;
            this.random = new Random();
        }

        @Override
        public synchronized void write(DataNode root) throws DataNodeSerializationException {
            long start = System.nanoTime();
            requestedWrites++;
            requestedRoot = root;
            // the new File is created with a random extension to protect against failures due to
            // several FL processes running at the same time. We do not currently lock the file.
            File newFile = new File(file + ".new." + ( random.nextLong() & Long.MAX_VALUE));
            try {
                bytesWritten += jsonHandler.writeToFile(newFile, root);
                IOUtils.mvAndOverride(newFile, file);
                if(logger.isDebugEnabled())
                    logger.debug("persisted config (digest=" + DataNodeUtilities.getDigestValueStringSafe(root) + ") to " + file);
                realWrites++;
                writtenRoot = root;
            } catch (IOException e) {
                numExceptions++;
                throw new DataNodeSerializationException(e);
            } finally {
                nsInWrite +=  (System.nanoTime() - start);
            }
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }

        // Statistics
        @Override
        public synchronized long getBytesWritten() {
            return bytesWritten;
        }

        @Override
        public synchronized long getRealWrites() {
            return realWrites;
        }

        @Override
        public synchronized long getRequestedWrites() {
            return requestedWrites;
        }

        @Override
        public synchronized long getMsInWrite() {
            return nsInWrite / 1000000L;
        }

        @Override
        public synchronized long getNumExceptions() {
            return numExceptions;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public DataNode getCurrentRequested() {
            return requestedRoot;
        }

        @Override
        public DataNode getLastWritten() {
            return writtenRoot;
        }
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.data.persistmem.PersistMemDataSourceMBean#getBytesWritten()
     */
    @Override
    public long getBytesWritten() {
        return writer.getBytesWritten();
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.data.persistmem.PersistMemDataSourceMBean#getRealWrites()
     */
    @Override
    public long getRealWrites() {
        return writer.getRealWrites();
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.data.persistmem.PersistMemDataSourceMBean#getRequestedWrites()
     */
    @Override
    public long getRequestedWrites() {
        return writer.getRequestedWrites();
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.data.persistmem.PersistMemDataSourceMBean#getFile()
     */
    @Override
    public File getFile() {
        return writer.getFile();
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.data.persistmem.PersistMemDataSourceMBean#getNumExceptions()
     */
    @Override
    public long getNumExceptions() {
        return writer.getNumExceptions();
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.data.persistmem.PersistMemDataSourceMBean#getMsInWrite()
     */
    @Override
    public long getMsInWrite() {
        return writer.getMsInWrite();
    }

    @Override
    public String getCurrentContent() {
        try {
            return nodeToString(getRoot());
        } catch (BigDBException e) {
            logger.debug("currentContent(): Error retrieving root datanode");
            return "(err)";
        }
    }

    @Override
    public String getWrittenContent() {
        DataNode lastWritten = writer.getLastWritten();
        return nodeToString(lastWritten);
    }

    @Override
    public String getReadContent() {
        return nodeToString(readRoot.get());
    }

    private String nodeToString(DataNode node) {
        return DataNodeUtilities.debugToString(node);
    }

}
