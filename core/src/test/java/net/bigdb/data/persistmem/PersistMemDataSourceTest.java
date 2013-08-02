package net.bigdb.data.persistmem;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthContext;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeFactory;
import net.bigdb.data.IndexSpecifier;
import net.bigdb.data.MutationListener;
import net.bigdb.query.Query;
import net.bigdb.schema.AbstractSchemaNodeVisitor;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.ReferenceSchemaNode;
import net.bigdb.schema.TypeSchemaNode;
import net.bigdb.schema.TypedefSchemaNode;
import net.bigdb.schema.UsesSchemaNode;
import net.bigdb.schema.internal.SchemaImpl;
import net.bigdb.service.internal.TreespaceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class PersistMemDataSourceTest {
    private final static Logger logger =
            LoggerFactory.getLogger(PersistMemDataSourceTest.class);

    private File configFile;
    private PersistMemDataSource persistMemDataSource;
    private SchemaImpl schemaImpl;

    @Rule
    public TemporaryFolder basePath = new TemporaryFolder();

    @Before
    public void setup() throws IOException, BigDBException {
        configFile = basePath.newFile("PersistMemDataSourceTest.config.json");
        InputStream simpleConfigStream = getClass().getResourceAsStream("/net/bigdb/data/persistmem/simpleconfig.json");
        if(simpleConfigStream == null) {
            throw new IllegalStateException("Could not find simpleconfig.json");
        }
        ByteStreams.copy(simpleConfigStream, Files.newOutputStreamSupplier(configFile, false));

        schemaImpl = new SchemaImpl();
        schemaImpl.getModuleLocator().addSearchPath(new File("../floodlight/schema"), true);
        schemaImpl.loadModule(new ModuleIdentifier("floodlight", "2012-10-22"));
        schemaImpl.loadModule(new ModuleIdentifier("aaa"));
        schemaImpl.finishLoading();

    }

    @Test
    public void readAndMutateSimpleConfigTest() throws BigDBException, JsonParseException, JsonMappingException, IOException {
        Map<String, String> properties = ImmutableMap.of("file", configFile.getPath());
        persistMemDataSource = new PersistMemDataSource("config", true, schemaImpl, properties);
        persistMemDataSource.setMutationListener(new MutationListener() {
            @Override
            public void dataNodesMutated(Set<Query> mutatedNodes, Operation operation, AuthContext authContext)
                    throws BigDBException {
                assertEquals(1, mutatedNodes.size());
            }
        });
        initSchema();

        persistMemDataSource.startup();
        DataNode root = persistMemDataSource.getRoot();
        assertTrue(root.hasChild("core"));
        assertEquals("hrtlbrnft", root.getChild("core").getChild("aaa").getChild("local-user").iterator().next().getChild("full-name").getString());

        Query query = Query.parse("/core/aaa/local-user[user-name='admin']/full-name");
        persistMemDataSource.updateData(query, persistMemDataSource.getDataNodeFactory().createLeafDataNode("Hallo"), AuthContext.SYSTEM);

        assertEquals("Hallo", persistMemDataSource.getRoot().getChild("core").getChild("aaa").getChild("local-user").iterator().next().getChild("full-name").getString());
        checkName("Hallo");
    }

    @Test
    public void readAndMutateMany() throws BigDBException, InterruptedException, JsonParseException, JsonMappingException, IOException {
        Map<String, String> properties = ImmutableMap.of("file", configFile.getPath(), PersistMemDataSource.PROP_KEY_ASYNC_WRITES, "true");
        persistMemDataSource = new PersistMemDataSource("config", true, schemaImpl, properties);
        persistMemDataSource.setMutationListener(new MutationListener() {
            @Override
            public void dataNodesMutated(Set<Query> mutatedNodes, Operation operation, AuthContext authContext)
                    throws BigDBException {
                assertEquals(1, mutatedNodes.size());
            }
        });
        initSchema();

        persistMemDataSource.startup();
        DataNode root = persistMemDataSource.getRoot();
        assertTrue(root.hasChild("core"));
        assertEquals("hrtlbrnft", root.getChild("core").getChild("aaa").getChild("local-user").iterator().next().getChild("full-name").getString());


        for(int i=0; i < 500; i++) {
            Query query = Query.parse("/core/aaa/local-user[user-name='admin']/full-name");
            persistMemDataSource.updateData(query, persistMemDataSource.getDataNodeFactory().createLeafDataNode("Hallo"+i), AuthContext.SYSTEM);
            assertEquals("Hallo"+i, persistMemDataSource.getRoot().getChild("core").getChild("aaa").getChild("local-user").iterator().next().getChild("full-name").getString());
        }

        Thread.sleep(PersistMemDataSource.DEFAULT_QUIESCENCE_MS * 2);
        persistMemDataSource.shutdown();
        checkName("Hallo499");
    }


    @Test
    public void readAndAddUsersMultihreaded() throws Exception {
        Map<String, String> properties = ImmutableMap.of("file", configFile.getPath(), PersistMemDataSource.PROP_KEY_ASYNC_WRITES, "true");
        persistMemDataSource = new PersistMemDataSource("config", true, schemaImpl, properties);
        persistMemDataSource.setMutationListener(new MutationListener() {
            @Override
            public void dataNodesMutated(Set<Query> mutatedNodes, Operation operation, AuthContext authContext)
                    throws BigDBException {
                assertEquals(1, mutatedNodes.size());
            }
        });
        initSchema();
        persistMemDataSource.startup();

        final AtomicInteger uid = new AtomicInteger(0);

        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<Exception>());
        ExecutorService e = Executors.newFixedThreadPool(3);
        for(int i=0; i < 100 ;i++) {
            e.execute(new Runnable() {
                @Override
                public void run() {
                    DataNodeFactory factory = persistMemDataSource.getDataNodeFactory();
                    try {
                        DataNode userName = factory.createLeafDataNode("user"+uid.incrementAndGet());
                        DataNode fullName = factory.createLeafDataNode("created by "+Thread.currentThread().getName());
                        DataNode userNode = factory.createListElementDataNode(false, ImmutableMap.<String,DataNode>of("user-name", userName, "full-name", fullName));
                        DataNode listNode = factory.createListDataNode(false, IndexSpecifier.fromFieldNames("user-name"), Iterators.singletonIterator(userNode));
                        Query query = Query.parse("/core/aaa/local-user");
                        persistMemDataSource.insertData(query, listNode, AuthContext.SYSTEM);

                    } catch (Exception e) {
                        logger.warn("Exception caught ",e);
                        exceptions.add(e);
                    }
                }
            });
        }

        e.shutdown();
        // FIXME: RobV: I still get intermittent errors here on my laptop,
        // an old MacBook Pro, no SSD, even if I increase this to 60 secs.
        // This seems to only happen if I run using the make file. If I run
        // under Eclipse it passes.
        assertTrue(e.awaitTermination(15, TimeUnit.SECONDS));

        if(!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }

        assertEquals(100, uid.get());
        assertEquals(101, persistMemDataSource.getRoot().getChild("core").getChild("aaa").getChild("local-user").childCount());

        Thread.sleep(PersistMemDataSource.DEFAULT_QUIESCENCE_MS * 2);
        persistMemDataSource.shutdown();

        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readValue(configFile, JsonNode.class);

        JsonNode userNode = root.get("core").get("aaa").get("local-user");
        assertEquals(101, userNode.size());
    }


    private void checkName(String string) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readValue(configFile, JsonNode.class);
        assertEquals(string, root.get("core").get("aaa").get("local-user").get(0).get("full-name").asText());
    }

    private void initSchema() throws BigDBException {
        TreespaceImpl mockTreespace = createNiceMock(TreespaceImpl.class);
        expect(mockTreespace.getSchema()).andReturn(schemaImpl).anyTimes();
        mockTreespace.authorize(null, null, null, null);
        replay(mockTreespace);
        persistMemDataSource.setTreespace(mockTreespace);
        schemaImpl.getSchemaRoot().accept(new AbstractSchemaNodeVisitor() {
            @Override
            public Result visit(LeafSchemaNode leafSchemaNode) throws BigDBException {
                leafSchemaNode.setDataSource("config");
                return Result.CONTINUE;
            }

            @Override
            public Result visit(LeafListSchemaNode leafListSchemaNode)
                    throws BigDBException {
                leafListSchemaNode.setDataSource("config");
                if (leafListSchemaNode.getDataSources().iterator().hasNext()) {
                    String dataSource = leafListSchemaNode.getDataSources().iterator().next();
                    LeafSchemaNode leafSchemaNode = leafListSchemaNode.getLeafSchemaNode();
                    leafSchemaNode.setDataSource(dataSource);
                }
                return Result.CONTINUE;
            }

            @Override
            public Result visit(ReferenceSchemaNode referenceSchemaNode)
                    throws BigDBException {
                referenceSchemaNode.setDataSource("config");
                return Result.CONTINUE;
            }

            @Override
            public Result visit(TypedefSchemaNode typedefSchemaNode) throws BigDBException {
                typedefSchemaNode.setDataSource("config");
                return Result.CONTINUE;
            }

            @Override
            public Result visit(UsesSchemaNode usesSchemaNode) throws BigDBException {
                usesSchemaNode.setDataSource("config");
                return Result.CONTINUE;
            }

            @Override
            public Result visit(TypeSchemaNode typeSchemaNode) throws BigDBException {
                typeSchemaNode.setDataSource("config");
                return Result.CONTINUE;
            }

        });
    }

}
