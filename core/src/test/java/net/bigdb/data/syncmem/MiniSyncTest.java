package net.bigdb.data.syncmem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.ContainerDataNode;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSerializationException;
import net.bigdb.data.memory.MemoryDataSource;
import net.bigdb.schema.ContainerSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.ScalarSchemaNode;
import net.bigdb.schema.SchemaNode;
import net.bigdb.schema.internal.SchemaImpl;
import net.bigdb.service.internal.DataNodeJsonHandler;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;

/** This functional test exposes the minisync state machine to certain corner cases.
 *  The testing harness is created manually here, which make it fairly brittle and elegant.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class MiniSyncTest {
    private final static Logger logger = LoggerFactory.getLogger(MiniSyncTest.class);

    public class TestServerReceiveHandler implements ServerReceiveHandler {
        private static final long TIMEOUT = 1000;
        private int numUpdates;

        private DataNode readDataNode;

        @Override
        public synchronized Response messageReceived(ChannelBuffer content, SocketAddress socketAddress)
                throws Exception {
            readDataNode = jsonHandler.readDataNode(new ChannelBufferInputStream(content), dataSource.getRootSchemaNode(), dataSource.getName());
            logger.info("Received update: "+readDataNode);
            numUpdates++;
            notifyAll();
            return new Response(HttpResponseStatus.OK, ChannelBuffers.copiedBuffer("OK", Charsets.UTF_8));
        }

        public synchronized void waitForUpdates(int updates) throws InterruptedException {
            long start = System.currentTimeMillis();
            while(numUpdates < updates) {
                long left = TIMEOUT - (System.currentTimeMillis() - start);
                if(left <= 0)
                    throw new RuntimeException("timeout expired waiting for update");
                logger.info("Waiting for " + left + " ms");
                wait(left);
            }
            logger.info("Waited for "+(System.currentTimeMillis() - start) + " ms");
        }
    }

    private int testServerPort;
    private int miniSyncPort;
    private HttpServer testServer;
    private DataNodeJsonHandler jsonHandler;
    private MemoryDataSource dataSource;
    private MiniSync miniSync;

    private final Set<Integer> usedPorts = new HashSet<Integer>();
    private TestServerReceiveHandler receiveHandler;
    private HttpClientFactory factory;
    private ContainerDataNode nodeToSync;

    public int findPort() {
        Random r = new Random();
        for(int port=8888; port <= 18888; port += r.nextInt(10)) {
            if(usedPorts.contains(port))
                continue;

            ServerSocket s;
            try {
                s = new ServerSocket(port);
                s.close();
                usedPorts.add(port);
                return s.getLocalPort();
            } catch (IOException e) {
                // port busy, try next
            }
        }
        throw new IllegalStateException("Could not find port");
    }

    @Before
    public void setup() throws BigDBException {
        // port that the manual test harness listens on
        testServerPort = findPort();
        // port that minisync will listen on
        miniSyncPort = findPort();
        logger.info("testServer port: " + testServerPort + ", miniSync port: "+miniSyncPort);

        ContainerSchemaNode schemaNode = new ContainerSchemaNode();
        ModuleIdentifier moduleId = new ModuleIdentifier("test");

        // simple schema {child : 'string' }
        ScalarSchemaNode leafSchemaNode = new LeafSchemaNode("child", moduleId, SchemaNode.LeafType.STRING);
        leafSchemaNode.addDataSource("name");
        leafSchemaNode.setLeafType(SchemaNode.LeafType.STRING);
        schemaNode.addChildNode("child", leafSchemaNode);
        SchemaImpl schema = new SchemaImpl(schemaNode);
        dataSource = new MemoryDataSource("name", true, schema);
        jsonHandler = new DataNodeJsonHandler(dataSource);

        // Test http server that will receive posts from minisync
        receiveHandler = new TestServerReceiveHandler();
        testServer = new HttpServer(testServerPort, receiveHandler);

        // mini sync; under tests
        miniSync = new MiniSync(dataSource, "http://localhost:"+testServerPort+"/config", miniSyncPort);
        testServer.start();
        miniSync.start();

        // let minisync know of the fake communication partner
        miniSync.controllerNodeIPsChanged(ImmutableMap.of("1", "127.0.0.1"), ImmutableMap.of("1", "127.0.0.1"), ImmutableMap.<String,String>of());

        // create a client factory
        factory = new HttpClientFactory();
        nodeToSync = dataSource.getDataNodeFactory().createContainerDataNode(false, ImmutableMap.<String, DataNode>of("child", dataSource.getDataNodeFactory().createLeafDataNode("state")));
    }

    @After
    public void shutdown() {
        if(miniSync != null)
            miniSync.shutdown();
        if(testServer != null)
            testServer.shutdown();
    }

    /*** verify that minisync sends the right update when it is in State MASTER and locally learns of a new update */
    @Test(timeout=1000)
    public void testSendSyncAsMaster() throws BigDBException, InterruptedException {
        DataNode node = dataSource.getDataNodeFactory().createContainerDataNode(false, ImmutableMap.<String, DataNode>of("child", dataSource.getDataNodeFactory().createLeafDataNode("state")));
        miniSync.update(node);
        receiveHandler.waitForUpdates(1);
        assertThat(receiveHandler.readDataNode.getDigestValue(), is(node.getDigestValue()));
    }


    /*** verify that minisync DOES accept a pushed remote update when it is SLAVE itself */
    @Test(timeout=1000)
    public void testAcceptSyncAsSlave() throws BigDBException, InterruptedException {
        miniSync.setRole(SyncRole.SLAVE);
        HttpClient httpClient = factory.getClient(URI.create("http://localhost:"+miniSyncPort+"/config"));
        SlaveUpdater updater = new SlaveUpdater(new SlaveId("1"), httpClient);

        updater.setContent(new SyncContent() {
            @Override
            public byte[] getUpdate(SyncContent currentContent) {
                try {
                    return jsonHandler.writeAsByteArray(nodeToSync);
                } catch (DataNodeSerializationException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public String getContentType() {
                return "text/json";
            }
        });
        updater.update();
        while(updater.needsUpdate()) {
            Thread.sleep(20);
        }

        assertThat(dataSource.getRoot().getDigestValue(), is(nodeToSync.getDigestValue()));
    }

    /*** verify that minisync does not accept a pushed remote update when it is MASTER itself and returns the right error code */
    @Test(timeout=1000)
    public void testDontAcceptSyncAsMaster() throws BigDBException, InterruptedException {
        final DataNode node = dataSource.getDataNodeFactory().createContainerDataNode(false, ImmutableMap.<String, DataNode>of("child", dataSource.getDataNodeFactory().createLeafDataNode("state")));
        HttpClient httpClient = factory.getClient(URI.create("http://localhost:"+miniSyncPort+"/config"));
        SlaveUpdater updater = new SlaveUpdater(new SlaveId("1"), httpClient);

        updater.setContent(new SyncContent() {
            @Override
            public byte[] getUpdate(SyncContent currentContent) {
                try {
                    return jsonHandler.writeAsByteArray(node);
                } catch (DataNodeSerializationException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public String getContentType() {
                return "text/json";
            }
        });
        updater.update();
        while(updater.getNumIgnored() == 0) {
            Thread.sleep(20);
        }

        assertThat(dataSource.getRoot().getDigestValue(), not(is(node.getDigestValue())));
    }


    /*** verify that, after receiving an update in slave mode and being promoted to MASTER, minisync sends back the right update */
    @Test(timeout=1000)
    public void testAcceptSyncAsSlaveThenSendBackAsMaster() throws BigDBException, InterruptedException {
        testAcceptSyncAsSlave();
        miniSync.transitionToMaster();
        receiveHandler.waitForUpdates(1);
        assertThat(receiveHandler.readDataNode.getDigestValue(), is(nodeToSync.getDigestValue()));
    }
}
