package org.projectfloodlight.db.schema;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;
import org.projectfloodlight.db.schema.AggregateSchemaNode;
import org.projectfloodlight.db.schema.ContainerSchemaNode;
import org.projectfloodlight.db.schema.GroupingSchemaNode;
import org.projectfloodlight.db.schema.LeafListSchemaNode;
import org.projectfloodlight.db.schema.LeafSchemaNode;
import org.projectfloodlight.db.schema.ListElementSchemaNode;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.ModuleIdentifier;
import org.projectfloodlight.db.schema.ReferenceSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.schema.SchemaNodeNotFoundException;
import org.projectfloodlight.db.schema.SchemaNodeVisitor;
import org.projectfloodlight.db.schema.TypeSchemaNode;
import org.projectfloodlight.db.schema.TypedefSchemaNode;
import org.projectfloodlight.db.schema.UsesSchemaNode;
import org.projectfloodlight.db.schema.SchemaNodeTest.TestVisitor.ExpectedCall.Type;
import org.projectfloodlight.db.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class SchemaNodeTest {

    private static final String MODULE_NAME = "Test";
    private static final String MODULE_REVISION = "2012-08-12";
    
    protected static File dumpDirectory;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Check if a dump directory has been specified
        Map<String, String> env = System.getenv();
        String dumpDirectoryName =
                env.get("SCHEMA_LOADER_UNIT_TEST_DUMP_DIRECTORY");
        if (dumpDirectoryName != null && !dumpDirectoryName.isEmpty()) {
            dumpDirectory = new File(dumpDirectoryName);
            dumpDirectory.mkdirs();
        }
    }

    @Test
    public void testModuleIdentifier() {
        ModuleIdentifier moduleId =
                new ModuleIdentifier(MODULE_NAME, MODULE_REVISION);
        assertEquals(moduleId.getName(), MODULE_NAME);
        assertEquals(moduleId.getRevision(), MODULE_REVISION);
        assertEquals(moduleId.toString(), MODULE_NAME + "@" + MODULE_REVISION);
        
        moduleId = new ModuleIdentifier(MODULE_NAME);
        assertEquals(moduleId.getName(), MODULE_NAME);
        assertEquals(moduleId.getRevision(), null);
        assertEquals(moduleId.toString(), MODULE_NAME);
    }
    
    @Test
    public void testLeafSchemaNode() throws BigDBException {
        ModuleIdentifier moduleId =
                new ModuleIdentifier(MODULE_NAME, MODULE_REVISION);
        DataNode defaultValue = new MemoryLeafDataNode(1234L);
        LeafSchemaNode leafNode = new LeafSchemaNode("Test", moduleId,
                SchemaNode.LeafType.INTEGER);
        leafNode.setDefaultValue(defaultValue);
        
        assertEquals(leafNode.getName(), "Test");
        assertEquals(leafNode.getModule(), moduleId);
        assertEquals(leafNode.getLeafType(), SchemaNode.LeafType.INTEGER);
        assertEquals(leafNode.getDefaultValue(), defaultValue);
        
        leafNode.setDefaultValue(null);
        
        DataNode typedefDefaultValue = new MemoryLeafDataNode("<unknown>");
        TypeSchemaNode typedefNode = new TypeSchemaNode("dpid", moduleId,
                SchemaNode.LeafType.STRING);
        typedefNode.setDefaultValue(typedefDefaultValue);
        leafNode.setBaseTypedef(typedefNode);
        assertEquals(leafNode.getLeafType(), SchemaNode.LeafType.STRING);
        assertEquals(leafNode.getDefaultValue(), typedefDefaultValue);
    }
    
    private ContainerSchemaNode constructTestContainerSchemaNode() {
        ModuleIdentifier moduleId =
                new ModuleIdentifier(MODULE_NAME, MODULE_REVISION);
        ContainerSchemaNode containerNode =
                new ContainerSchemaNode("Test", moduleId);
        LeafSchemaNode integerLeafNode = new LeafSchemaNode("Int", moduleId,
                SchemaNode.LeafType.INTEGER);
        containerNode.addChildNode("Int", integerLeafNode);
        LeafListSchemaNode stringListNode = new LeafListSchemaNode("StringList",
                moduleId, new LeafSchemaNode("", moduleId,
                SchemaNode.LeafType.STRING));
        containerNode.addChildNode("StringList", stringListNode);
        
        ContainerSchemaNode childContainerNode =
                new ContainerSchemaNode("ChildContainer", moduleId);
        LeafSchemaNode countNode = new LeafSchemaNode("Count", moduleId,
                SchemaNode.LeafType.INTEGER);
        childContainerNode.addChildNode("Count", countNode);
        childContainerNode.addChildNode("Foo", new LeafSchemaNode("Foo",
                moduleId, SchemaNode.LeafType.STRING));
        containerNode.addChildNode("ChildContainer", childContainerNode);

        ReferenceSchemaNode referenceNode = new ReferenceSchemaNode("Ref",
                moduleId, "/ChildContainer/Count", countNode);
        containerNode.addChildNode("Ref", referenceNode);
        return containerNode;
    }
    
    @Test
    public void testContainerNode() throws BigDBException {
        ContainerSchemaNode aggregateNode = constructTestContainerSchemaNode();
        assertEquals(aggregateNode.getName(), "Test");
        LeafSchemaNode leafIntNode = (LeafSchemaNode)
                aggregateNode.getChildSchemaNode("Int");
        assertEquals(leafIntNode.getName(), "Int");
        assertEquals(leafIntNode.getLeafType(), SchemaNode.LeafType.INTEGER);
        LeafListSchemaNode leafListNode = (LeafListSchemaNode)
                aggregateNode.getChildSchemaNode("StringList");
        assertEquals(leafListNode.getName(), "StringList");
        assertEquals(leafListNode.getNodeType(), SchemaNode.NodeType.LEAF_LIST);
        LeafSchemaNode leafNode = (LeafSchemaNode)
                leafListNode.getLeafSchemaNode();
        assertEquals(leafNode.getLeafType(), SchemaNode.LeafType.STRING);
        ContainerSchemaNode childAggregateNode = (ContainerSchemaNode)
                aggregateNode.getChildSchemaNode("ChildContainer");
        assertEquals(childAggregateNode.getName(), "ChildContainer");
        assertEquals(childAggregateNode.getNodeType(), SchemaNode.NodeType.CONTAINER);
        LeafSchemaNode countNode = (LeafSchemaNode)
                childAggregateNode.getChildSchemaNode("Count");
        assertEquals(countNode.getName(), "Count");
        assertEquals(countNode.getLeafType(), SchemaNode.LeafType.INTEGER);
        LeafSchemaNode fooNode = (LeafSchemaNode)
                childAggregateNode.getChildSchemaNode("Foo");
        assertEquals(fooNode.getName(), "Foo");
        assertEquals(fooNode.getLeafType(), SchemaNode.LeafType.STRING);
        try {
            childAggregateNode.getChildSchemaNode("DoesNotExist");
            fail("Expected SchemaNodeNotFoundException");
        }
        catch (SchemaNodeNotFoundException exc) {
            // Expecting exception so ignore exception
        }
    }
    
    static class TestVisitor implements SchemaNodeVisitor {
        
        static class ExpectedCall {
            enum Type {
                ENTER_CONTAINER,
                LEAVE_CONTAINER,
                ENTER_LIST,
                LEAVE_LIST,
                VISIT_LEAF,
                VISIT_LEAF_LIST,
                VISIT_REFERENCE,
                VISIT_TYPEDEF
            };
            
            Type type;
            String name;
            
            public ExpectedCall(Type type, String name) {
                this.type = type;
                this.name = name;
            }
            
            public Type getType() {
                return type;
            }
            
            public String getName() {
                return name;
            }
        }
        
        ExpectedCall[] expectedCalls;
        int current = 0;
        
        public TestVisitor(ExpectedCall[] expectedCalls) {
            this.expectedCalls = expectedCalls;
        }
        
        public void checkDone() {
            assertEquals(expectedCalls.length, current);
        }
        
        public void checkVisit(ExpectedCall.Type type, SchemaNode schemaNode) {
            ExpectedCall call = expectedCalls[current];
            assertEquals(call.getType(), type);
            assertEquals(call.getName(), schemaNode.getName());
            current++;
        }
        public SchemaNodeVisitor.Result visitEnter(
                ContainerSchemaNode containerSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.ENTER_CONTAINER, containerSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        public SchemaNodeVisitor.Result visitLeave(
                ContainerSchemaNode containerSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.LEAVE_CONTAINER, containerSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }
        
        public SchemaNodeVisitor.Result visitEnter(
                ListSchemaNode listSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.ENTER_LIST, listSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }
        
        public SchemaNodeVisitor.Result visitLeave(
                ListSchemaNode listSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.LEAVE_LIST, listSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public Result visitEnter(ListElementSchemaNode listElementSchemaNode)
                throws BigDBException {
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public Result visitLeave(ListElementSchemaNode listElementSchemaNode)
                throws BigDBException {
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public Result visitEnter(GroupingSchemaNode groupingSchemaNode)
                throws BigDBException {
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public Result visitLeave(GroupingSchemaNode groupingSchemaNode)
                throws BigDBException {
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        public SchemaNodeVisitor.Result visit(
                LeafListSchemaNode listSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.VISIT_LEAF_LIST, listSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        public SchemaNodeVisitor.Result visit(LeafSchemaNode leafSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.VISIT_LEAF, leafSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }
        
        public SchemaNodeVisitor.Result visit(
                ReferenceSchemaNode referenceSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.VISIT_REFERENCE, referenceSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }
        
        public SchemaNodeVisitor.Result visit(
                TypedefSchemaNode typedefSchemaNode)
                throws BigDBException {
            checkVisit(ExpectedCall.Type.VISIT_TYPEDEF, typedefSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public SchemaNodeVisitor.Result
                visit(UsesSchemaNode usesSchemaNode) throws BigDBException {
            return SchemaNodeVisitor.Result.CONTINUE;
            
        }

        @Override
        public Result visit(TypeSchemaNode typeSchemaNode) 
                throws BigDBException {
            return SchemaNodeVisitor.Result.CONTINUE;
        }
    }

    @Test
    public void testJsonSerialization() throws Exception {
        AggregateSchemaNode aggregateSchemaNode =
                constructTestContainerSchemaNode();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        String actualJson = mapper.writeValueAsString(aggregateSchemaNode);
        
        String nameExpected = "TestJsonSerialization.expected";
        if (dumpDirectory != null) {
            File outputFile = new File(dumpDirectory, nameExpected);
            FileWriter outputWriter = null;
            try {
                outputWriter = new FileWriter(outputFile);
                outputWriter.append(actualJson);
            }
            finally {
                if (outputWriter != null)
                    outputWriter.close();
            }
        }
        InputStream inputStream = getClass().getResourceAsStream(nameExpected);
        String expectedJson =
                StringUtils.readStringFromInputStream(inputStream);
        assertEquals(expectedJson, actualJson);
    }
    
    @Test
    public void testVisitor() throws BigDBException {
        AggregateSchemaNode aggregateSchemaNode =
                constructTestContainerSchemaNode();
        TestVisitor.ExpectedCall[] expectedCalls = {
            new TestVisitor.ExpectedCall(Type.ENTER_CONTAINER, "Test"),
            new TestVisitor.ExpectedCall(Type.ENTER_CONTAINER, "ChildContainer"),
            new TestVisitor.ExpectedCall(Type.VISIT_LEAF, "Count"),
            new TestVisitor.ExpectedCall(Type.VISIT_LEAF, "Foo"),
            new TestVisitor.ExpectedCall(Type.LEAVE_CONTAINER, "ChildContainer"),
            new TestVisitor.ExpectedCall(Type.VISIT_LEAF, "Int"),
            new TestVisitor.ExpectedCall(Type.VISIT_REFERENCE, "Ref"),
            new TestVisitor.ExpectedCall(Type.VISIT_LEAF_LIST, "StringList"),
            new TestVisitor.ExpectedCall(Type.LEAVE_CONTAINER, "Test")
        };
        TestVisitor visitor = new TestVisitor(expectedCalls);
        aggregateSchemaNode.accept(visitor);
        visitor.checkDone();
    }
}
