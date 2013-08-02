package net.bigdb.data;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.bigdb.data.DataNode.DataNodeWithPath;
import net.bigdb.data.memory.MemoryContainerDataNode;
import net.bigdb.data.memory.MemoryKeyedListDataNode;
import net.bigdb.data.memory.MemoryLeafDataNode;
import net.bigdb.data.memory.MemoryLeafListDataNode;
import net.bigdb.data.memory.MemoryListElementDataNode;
import net.bigdb.data.memory.MemoryUnkeyedListDataNode;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.schema.ContainerSchemaNode;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.SchemaNode;
import net.bigdb.test.MapperTest;

import org.junit.Test;

public class DataNodeQueryTest extends MapperTest {

    @Test
    public void queryLeaf() throws Exception {
        // Set up the schema for a leaf node
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        SchemaNode leafSchemaNode = new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING);

        // Create a leaf data node
        DataNode leafDataNode = new MemoryLeafDataNode("foo");

        // Perform query
        LocationPathExpression queryPath = LocationPathExpression.parse("name");
        Iterable<DataNodeWithPath> result = leafDataNode.queryWithPath(leafSchemaNode, queryPath);
        Iterator<DataNodeWithPath> iter = result.iterator();

        DataNodeWithPath dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), equalTo("foo"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("name"));
    }

    @Test
    public void queryContainer() throws Exception {
        // Set up the schema for a leaf node
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode = new ContainerSchemaNode("", moduleId);
        containerSchemaNode.addChildNode("child1", new LeafSchemaNode("child1", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("child2", new LeafSchemaNode("child2", moduleId, SchemaNode.LeafType.INTEGER));

        Map<String, DataNode> initNodes = new HashMap<String, DataNode>();
        DataNode child1 = new MemoryLeafDataNode("abc");
        initNodes.put("child1", child1);
        DataNode child2 = new MemoryLeafDataNode(200L);
        initNodes.put("child2", child2);
        DataNode containerDataNode = new MemoryContainerDataNode(false, initNodes);

        // Query the entire container data node
        LocationPathExpression queryPath = LocationPathExpression.parse("/");
        Iterable<DataNodeWithPath> result = containerDataNode.queryWithPath(containerSchemaNode, queryPath);
        Iterator<DataNodeWithPath> iter = result.iterator();
        DataNodeWithPath dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getPath().toString(), is("/"));
        assertThat(dataNodeWithPath.getDataNode(), is(containerDataNode));
        assertThat(iter.hasNext(), is(false));

        // Query the second child
        queryPath = LocationPathExpression.parse("/child2");
        result = containerDataNode.queryWithPath(containerSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getPath().toString(), is("/child2"));
        assertThat(dataNodeWithPath.getDataNode(), is(child2));
        assertThat(iter.hasNext(), is(false));
    }

    private void queryInvalidPathsForArrayDataNode(SchemaNode schemaNode, DataNode dataNode) {
        String[] invalidQueryPaths = {
                "name[-1]",                 // Negative index
                "name[test=\"dummy\"]",     // Non-integer predicate
                "name[3]"                   // Index out of range
        };
        for (String invalidQueryPath: invalidQueryPaths) {
            try {
                LocationPathExpression queryPath = LocationPathExpression.parse(invalidQueryPath);
                @SuppressWarnings("unused")
                Iterable<DataNodeWithPath> result = dataNode.queryWithPath(schemaNode, queryPath);
                fail("Expected exception from invalid query of leaf list data node");
            }
            catch (Exception e) {
                // Expect exception, so ignore
                // FIXME
            }
        }
    }

    @Test
    public void queryLeafList() throws Exception {
        // Set up a schema for a leaf list
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafSchemaNode leafSchemaNode = new LeafSchemaNode("", moduleId, SchemaNode.LeafType.STRING);
        SchemaNode leafListSchemaNode = new LeafListSchemaNode("name", moduleId, leafSchemaNode);

        // Create a leaf list data node
        List<DataNode> initNodes = new ArrayList<DataNode>();
        initNodes.add(new MemoryLeafDataNode("abc"));
        initNodes.add(new MemoryLeafDataNode("def"));
        DataNode leafListDataNode = new MemoryLeafListDataNode(false, initNodes);

        // Query for the entire leaf list
        LocationPathExpression queryPath = LocationPathExpression.parse("/name");
        Iterable<DataNodeWithPath> result =
                leafListDataNode.queryWithPath(leafListSchemaNode, queryPath);

        Iterator<DataNodeWithPath> iter = result.iterator();
        DataNodeWithPath dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("abc"));
        assertThat(dataNodeWithPath.getPath().toString(), is("/name[0]"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("def"));
        assertThat(dataNodeWithPath.getPath().toString(), is("/name[1]"));
        assertThat(iter.hasNext(), is(false));

        // Query for a single indexed leaf element
        queryPath = LocationPathExpression.parse("/name[0]");
        result = leafListDataNode.queryWithPath(leafListSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), equalTo("abc"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("/name[0]"));
        assertThat(iter.hasNext(), is(false));

        queryInvalidPathsForArrayDataNode(leafListSchemaNode, leafListDataNode);
    }

    @Test
    public void queryUnkeyedList() throws Exception {
        // Set up a schema for a leaf list
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafSchemaNode nameSchemaNode = new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING);
        LeafSchemaNode countSchemaNode = new LeafSchemaNode("count", moduleId, SchemaNode.LeafType.INTEGER);
        ListElementSchemaNode listElementSchemaNode = new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("name", nameSchemaNode);
        listElementSchemaNode.addChildNode("count", countSchemaNode);
        SchemaNode listSchemaNode = new ListSchemaNode("test", moduleId, listElementSchemaNode);

        // Create a list data node
        List<DataNode> initListNodes = new ArrayList<DataNode>();
        Map<String, DataNode> initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", new MemoryLeafDataNode("bar"));
        initListElementNodes.put("count", new MemoryLeafDataNode(100L));
        DataNode listElement1 = new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement1);
        initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", new MemoryLeafDataNode("foo"));
        initListElementNodes.put("count", new MemoryLeafDataNode(200L));
        DataNode listElement2 = new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement2);

        DataNode listDataNode = new MemoryUnkeyedListDataNode(false, initListNodes.iterator());

        // Query for the entire list
        LocationPathExpression queryPath = LocationPathExpression.parse("test");
        Iterable<DataNodeWithPath> result =
                listDataNode.queryWithPath(listSchemaNode, queryPath);
        Iterator<DataNodeWithPath> iter = result.iterator();
        DataNodeWithPath dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement1));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[0]"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement2));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[1]"));
        assertThat(iter.hasNext(), is(false));

        // Query for all of the name leaf nodes
        queryPath = LocationPathExpression.parse("test/name");
        result = listDataNode.queryWithPath(listSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("bar"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[0]/name"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("foo"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[1]/name"));
        assertThat(iter.hasNext(), is(false));

        // Query for the second list element
        queryPath = LocationPathExpression.parse("test[1]");
        result = listDataNode.queryWithPath(listSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement2));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[1]"));
        assertThat(iter.hasNext(), is(false));

        // Query for the count of first list element
        queryPath = LocationPathExpression.parse("test[0]/count");
        result = listDataNode.queryWithPath(listSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getLong(), is(100L));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[0]/count"));
        assertThat(iter.hasNext(), is(false));

        // Check that invalid queries throw exceptions
        queryInvalidPathsForArrayDataNode(listSchemaNode, listDataNode);
    }

    @Test
    public void queryKeyedList() throws Exception {
        // Set up a schema for a leaf list
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafSchemaNode nameSchemaNode = new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING);
        LeafSchemaNode countSchemaNode = new LeafSchemaNode("count", moduleId, SchemaNode.LeafType.INTEGER);
        ListElementSchemaNode listElementSchemaNode = new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addKeyNodeName("name");
        listElementSchemaNode.addChildNode("name", nameSchemaNode);
        listElementSchemaNode.addChildNode("count", countSchemaNode);
        ListSchemaNode listSchemaNode = new ListSchemaNode("test", moduleId, listElementSchemaNode);

        // Create a list data node
        List<DataNode> initListNodes = new ArrayList<DataNode>();
        Map<String, DataNode> initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", new MemoryLeafDataNode("bar"));
        initListElementNodes.put("count", new MemoryLeafDataNode(100L));
        DataNode listElement1 = new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement1);
        initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", new MemoryLeafDataNode("fog"));
        initListElementNodes.put("count", new MemoryLeafDataNode(200L));
        DataNode listElement2 = new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement2);
        initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", new MemoryLeafDataNode("foo"));
        initListElementNodes.put("count", new MemoryLeafDataNode(300L));
        DataNode listElement3 = new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement3);

        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames("name");
        DataNode listDataNode = new MemoryKeyedListDataNode(false, keySpecifier, initListNodes.iterator());

        // Query for the entire list
        LocationPathExpression queryPath = LocationPathExpression.parse("test");
        Iterable<DataNodeWithPath> result =
                listDataNode.queryWithPath(listSchemaNode, queryPath);
        Iterator<DataNodeWithPath> iter = result.iterator();
        DataNodeWithPath dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement1));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"bar\"]"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement2));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"fog\"]"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement3));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"foo\"]"));
        assertThat(iter.hasNext(), is(false));

        // Query for all of the name leaf nodes
        queryPath = LocationPathExpression.parse("test/name");
        result = listDataNode.queryWithPath(listSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("bar"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"bar\"]/name"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("fog"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"fog\"]/name"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("foo"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"foo\"]/name"));
        assertThat(iter.hasNext(), is(false));

        // Query for the second list element
        queryPath = LocationPathExpression.parse("test[name=\"fog\"]");
        result = listDataNode.queryWithPath(listSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement2));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"fog\"]"));
        assertThat(iter.hasNext(), is(false));

        // Query with a starts-with predicate
        queryPath = LocationPathExpression.parse("test[starts-with(name,\"fo\")]");
        result = listDataNode.queryWithPath(listSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement2));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"fog\"]"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement3));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"foo\"]"));
        assertThat(iter.hasNext(), is(false));

        // Query for the count of first list element
        queryPath = LocationPathExpression.parse("test[name=\"bar\"]/count");
        result = listDataNode.queryWithPath(listSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getLong(), is(100L));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("test[name=\"bar\"]/count"));
        assertThat(iter.hasNext(), is(false));
    }

    @Test
    public void queryComplexData() throws Exception {
        // Set up a somewhat more complicated schema with nested nodes
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode rootSchemaNode =
                new ContainerSchemaNode("", moduleId);
        ContainerSchemaNode beanSchemaNode =
                new ContainerSchemaNode("bean", moduleId);
        beanSchemaNode.addChildNode("name",
                new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        beanSchemaNode.addChildNode("value",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.STRING));
        rootSchemaNode.addChildNode("bean", beanSchemaNode);
        ListElementSchemaNode listElementSchemaNode = new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("name", new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        listElementSchemaNode.addChildNode("count", new LeafSchemaNode("count", moduleId, SchemaNode.LeafType.INTEGER));
        ListSchemaNode listSchemaNode = new ListSchemaNode("list", moduleId, listElementSchemaNode);
        rootSchemaNode.addChildNode("list", listSchemaNode);

        // Set up some data
        Map<String, DataNode> rootContainerInitNodes = new HashMap<String, DataNode>();

        Map<String, DataNode> initBeanNodes = new HashMap<String, DataNode>();
        initBeanNodes.put("name", new MemoryLeafDataNode("abc"));
        initBeanNodes.put("value", new MemoryLeafDataNode("def"));
        DataNode beanContainerNode = new MemoryContainerDataNode(false, initBeanNodes);
        rootContainerInitNodes.put("bean", beanContainerNode);

        List<DataNode> initListNodes = new ArrayList<DataNode>();
        Map<String, DataNode> initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", new MemoryLeafDataNode("bar"));
        initListElementNodes.put("count", new MemoryLeafDataNode(100L));
        DataNode listElement1 = new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement1);
        initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", new MemoryLeafDataNode("foo"));
        initListElementNodes.put("count", new MemoryLeafDataNode(200L));
        DataNode listElement2 = new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement2);
        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames("name");
        DataNode listDataNode = new MemoryKeyedListDataNode(false, keySpecifier, initListNodes.iterator());
        rootContainerInitNodes.put("list", listDataNode);

        DataNode rootContainer = new MemoryContainerDataNode(false, rootContainerInitNodes);

        // Query for the entire list
        LocationPathExpression queryPath = LocationPathExpression.parse("/list");
        Iterable<DataNodeWithPath> result =
                rootContainer.queryWithPath(rootSchemaNode, queryPath);
        Iterator<DataNodeWithPath> iter = result.iterator();
        DataNodeWithPath dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement1));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("/list[name=\"bar\"]"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode(), is(listElement2));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("/list[name=\"foo\"]"));
        assertThat(iter.hasNext(), is(false));

        // Query for all of the name leaf nodes
        queryPath = LocationPathExpression.parse("/list/name");
        result = rootContainer.queryWithPath(rootSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("bar"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("/list[name=\"bar\"]/name"));
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("foo"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("/list[name=\"foo\"]/name"));
        assertThat(iter.hasNext(), is(false));

        // Query for the value field in the bean container
        queryPath = LocationPathExpression.parse("/bean/value");
        result = rootContainer.queryWithPath(rootSchemaNode, queryPath);
        iter = result.iterator();
        dataNodeWithPath = iter.next();
        assertThat(dataNodeWithPath.getDataNode().getString(), is("def"));
        assertThat(dataNodeWithPath.getPath().toString(), equalTo("/bean/value"));
        assertThat(iter.hasNext(), is(false));
    }
}
