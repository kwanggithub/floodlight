package net.bigdb.data.syncmem;

import java.lang.management.ManagementFactory;
import java.lang.ref.SoftReference;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.JMException;
import javax.management.ObjectName;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSerializationException;
import net.bigdb.data.DataNodeUtilities;
import net.bigdb.data.memory.DelegatableDataSource;
import net.bigdb.service.internal.DataNodeJsonHandler;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IHAListener;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.io.CountingInputStream;

public class MiniSync implements IHAListener {
    private final static Logger logger = LoggerFactory.getLogger(MiniSync.class);

    private volatile SyncRole role;
    private final AllSlavesUpdater allSlavesUpdater;
    private final DataNodeJsonHandler handler;
    private final String uriTemplate;
    private final SyncUpdateServer syncServer;
    private final int serverPort;
    private String controllerId;
    private final HttpClientFactory clientFactory;

    private final AtomicInteger numRequestedUpdates = new AtomicInteger();

    private final DelegatableDataSource delegatableDataSource;

    public MiniSync(DelegatableDataSource delegate, String uriTemplate, int serverPort) {
        this.delegatableDataSource = delegate;
        this.uriTemplate = uriTemplate;
        this.serverPort = serverPort;
        role = SyncRole.MASTER;

        clientFactory = new HttpClientFactory();
        allSlavesUpdater = new AllSlavesUpdater(clientFactory);
        handler = new DataNodeJsonHandler(delegate);
        syncServer = new SyncUpdateServer(serverPort, delegate, handler);

        try {
            ObjectName objectName = new ObjectName("net.bigdb:type=MiniSync,name="+this.serverPort);

            if(logger.isDebugEnabled())
                logger.debug("Registering as MBean "+objectName);
            ManagementFactory.getPlatformMBeanServer().registerMBean(new JMXStats(), objectName);
        } catch (JMException e) {
            if(logger.isDebugEnabled())
                logger.debug("Error exposing MBean for Minisync");
            if(logger.isTraceEnabled())
                logger.trace("Stacktrace",e);
        }
    }

    public void update(DataNode root) throws DataNodeSerializationException {
        numRequestedUpdates.incrementAndGet();
        allSlavesUpdater.setContent(new DataNodeSyncContent(root));
    }

    public synchronized void start() {
        logger.info("Starting minisync on server port "+serverPort);
        allSlavesUpdater.start();

        if(role == SyncRole.SLAVE)
            allSlavesUpdater.pause();

        syncServer.start();
    }

    public synchronized void shutdown() {
        allSlavesUpdater.shutdown();
        syncServer.shutdown();
    }

    @Override
    public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs) {

        if(logger.isDebugEnabled()) {
            logger.debug("controllerNodIPsChanged(cur="+curControllerNodeIPs + ", added="+addedControllerNodeIPs + ", removed="+removedControllerNodeIPs +")");
        }
        setControllerNodeIPs(curControllerNodeIPs.entrySet());
    }

    public void setControllerNodeIPs(Collection<Entry<String, String>> ips) {
        if(logger.isDebugEnabled()) {
            logger.debug("setControllerNodeIPs("+ips+ ")");
        }
        Set<SlaveId> allIds = new HashSet<SlaveId>();
        for (Entry<String, String>entry : ips) {
            if(Objects.equal(entry.getKey(), controllerId))
                    continue;

            // skip nodes that don't have an IP address configured (can happen during provisioning).
            if(Strings.isNullOrEmpty(entry.getValue()))
                continue;

            SlaveId id = new SlaveId(entry.getValue());
            if (!allSlavesUpdater.hasSlave(id)) {
                allSlavesUpdater.addSlave(id, makeUri(entry.getValue()));
            }
            allIds.add(id);
        }
        allSlavesUpdater.retainSlaves(allIds);
    }

    private URI makeUri(String ip) {
        return URI.create(String.format(uriTemplate, ip));
    }

    class DataNodeSyncContent implements SyncContent {
        private final byte[] bytes;
        private final DataNode dataNode;

        public DataNodeSyncContent(DataNode root) throws DataNodeSerializationException {
            this.dataNode = root;
            this.bytes = handler.writeAsByteArray(root);
        }

        @Override
        public String getContentType() {
            return "application/json; charset=UTF-8";
        }

        @Override
        public byte[] getUpdate(SyncContent currentContent) {
            return bytes;
        }

        @Override
        public String toString() {
            return "DataNodeSyncContent(dataNode digest=" + DataNodeUtilities.debugToString(dataNode) + ")";
        }
    }

    public void setControllerId(String controllerId) {
        this.controllerId = controllerId;
    }

    public SyncRole getRole() {
        return role;
    }

    public void setRole(SyncRole role) {
        this.role = role;
    }

    public class SyncUpdateServer {
        private final DelegatableDataSource source;
        private final DataNodeJsonHandler handler;
        private final HttpServer server;

        private final int port;
        private final AtomicLong bytesReceived;
        private final AtomicInteger updatesIgnored;
        private final AtomicInteger updatesAccepted;
        public SoftReference<DataNode> lastReceivedRoot;

        SyncUpdateServer(int port, DelegatableDataSource source, DataNodeJsonHandler handler) {
            this.port = port;
            this.source = source;
            this.handler = handler;
            this.server = new HttpServer(port, new SyncReceiveHandler());
            this.bytesReceived = new AtomicLong(0);
            this.updatesIgnored = new AtomicInteger(0);
            this.updatesAccepted = new AtomicInteger(0);
        }

        public void start() {
            logger.info("Starting SyncUpdateServer on port "+port);
            this.server.start();
        }

        public void shutdown() {
            this.server.shutdown();
        }

        class SyncReceiveHandler implements ServerReceiveHandler {
            @Override
            public Response messageReceived(ChannelBuffer content, SocketAddress remoteAddress) throws BigDBException {

                if(role == SyncRole.SLAVE) {
                    CountingInputStream inputStream = new CountingInputStream(new ChannelBufferInputStream(content));
                    DataNode newRoot = handler.readDataNode(inputStream, source.getRootSchemaNode(), source.getName());
                    source.setRoot(newRoot);
                    bytesReceived.addAndGet(inputStream.getCount());
                    updatesAccepted.incrementAndGet();
                    lastReceivedRoot = new SoftReference<DataNode>(newRoot);
                    return new Response(HttpResponseStatus.OK, ChannelBuffers.copiedBuffer("OK", Charsets.UTF_8));
                } else {
                    logger.warn("Ignoring update received from " + remoteAddress + " - not in slave mode");
                    updatesIgnored.incrementAndGet();
                    return new Response(HttpResponseStatus.SERVICE_UNAVAILABLE, ChannelBuffers.copiedBuffer("Config ignored - not in slave mode", Charsets.UTF_8));
                }
            }
        }

        public long getBytesReceived() {
            return bytesReceived.get();
        }

        public int getNumUpdatesIgnored() {
            return updatesIgnored.get();
        }

        public int getNumUpdatesAccepted() {
            return updatesAccepted.get();
        }

        public DataNode getLastReceivedRoot() {
            return lastReceivedRoot.get();
        }

    }

    class JMXStats implements JMXStatsMBean {
        @Override
        public long getNumRequestedUpdates() {
            return numRequestedUpdates.get();
        }

        @Override
        public String getPendingSendContent() {
            return "" + allSlavesUpdater.getPendingContent();
        }

        @Override
        public String getSlavesAcceptedContents() {
            return "" + allSlavesUpdater.getSlaveCurrentContents();
        }

        @Override
        public String getLastReceivedContent() {
            return "" + syncServer.getLastReceivedRoot();
        }

        @Override
        public long getAllClientBytesSent() {
            return clientFactory.getAllStats().getBytesSent();
        }

        @Override
        public long getAllClientBytesReceived() {
            return clientFactory.getAllStats().getBytesReceived();
        }

        @Override
        public long getAllClientNumConnections() {
            return clientFactory.getAllStats().getNumConnections();
        }

        @Override
        public long getAllClientNumReusedConnections() {
            return clientFactory.getAllStats().getNumReusedConnections();
        }

        @Override
        public long getAllClientNumRequests() {
            return clientFactory.getAllStats().getNumRequests();
        }

        @Override
        public long getAllClientNumExceptions() {
            return clientFactory.getAllStats().getNumExceptions();
        }

        @Override
        public long getServerBytesReceived() {
            return syncServer.getBytesReceived();
        }

        @Override
        public long getServerNumUpdatesAccepted() {
            return syncServer.getNumUpdatesAccepted();
        }

        @Override
        public long getServerNumUpdatesIgnored() {
            return syncServer.getNumUpdatesIgnored();
        }
        @Override
        public int getServerPort() {
            return serverPort;
        }

        @Override
        public String getControllerId() {
            return controllerId;
        }

        @Override
        public String getSlaves() {
            return Joiner.on(", ").join(allSlavesUpdater.getSlaveIds());
        }

        @Override
        public String getRole() {
            return role.toString();
        }

    }

    public interface JMXStatsMBean {
        long getServerNumUpdatesIgnored();
        String getSlavesAcceptedContents();
        String getLastReceivedContent();
        long getNumRequestedUpdates();
        long getAllClientNumReusedConnections();
        long getAllClientNumRequests();
        String getRole();
        String getSlaves();
        String getControllerId();
        int getServerPort();
        long getAllClientBytesSent();
        long getAllClientBytesReceived();
        long getAllClientNumConnections();
        long getAllClientNumExceptions();
        long getServerBytesReceived();
        long getServerNumUpdatesAccepted();
        String getPendingSendContent();

    }

    // ***************
    // IHARoleListener
    // ***************

    @Override
    public String getName() {
        return "minisync";
    }

    @Override
    public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
                                            String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
                                             String name) {
        return false;
    }

    @Override
    public void transitionToMaster() {
        // update minisync's view of the current root, may have changed 
        // during slave state
        try {
            update(delegatableDataSource.getRoot());
        } catch (DataNodeSerializationException e) {
            logger.warn("Error updating minisync with current datasource root", e);
        } catch (BigDBException e) {
            logger.warn("Error updating minisync with current datasource root", e);
        }
        allSlavesUpdater.resume();        
    }
}
