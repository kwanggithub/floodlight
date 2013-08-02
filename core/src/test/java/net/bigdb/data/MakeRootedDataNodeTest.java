package net.bigdb.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import net.bigdb.data.memory.MemoryContainerDataNode;
import net.bigdb.data.memory.MemoryKeyedListDataNode;
import net.bigdb.data.memory.MemoryLeafDataNode;
import net.bigdb.data.memory.MemoryListElementDataNode;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.schema.ContainerSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.SchemaNode;
import net.bigdb.test.MapperTest;

public class MakeRootedDataNodeTest extends MapperTest {

    private SchemaNode rootSchemaNode = buildSchema();

    private SchemaNode buildSchema() {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode rootSchemaNode = new ContainerSchemaNode("", moduleId);
        LeafSchemaNode counterSchemaNode = new LeafSchemaNode("counter", moduleId, LeafSchemaNode.LeafType.INTEGER);
        rootSchemaNode.addChildNode("counter", counterSchemaNode);
        ContainerSchemaNode testSchemaNode = new ContainerSchemaNode("test", moduleId);
        rootSchemaNode.addChildNode("test", testSchemaNode);
        ListElementSchemaNode policyListElement = new ListElementSchemaNode(moduleId);
        ListSchemaNode policyList = new ListSchemaNode("policy", moduleId, policyListElement);
        testSchemaNode.addChildNode("policy", policyList);
        policyListElement.addKeyNodeName("name");
        LeafSchemaNode policyNameSchemaNode = new LeafSchemaNode("name", moduleId, LeafSchemaNode.LeafType.STRING);
        policyListElement.addChildNode("name", policyNameSchemaNode);
        LeafSchemaNode prioritySchemaNode = new LeafSchemaNode("priority", moduleId, LeafSchemaNode.LeafType.INTEGER);
        policyListElement.addChildNode("priority", prioritySchemaNode);
        ListElementSchemaNode ruleListElement = new ListElementSchemaNode(moduleId);
        LeafSchemaNode ruleNameSchemaNode = new LeafSchemaNode("id", moduleId, LeafSchemaNode.LeafType.STRING);
        ruleListElement.addChildNode("id", ruleNameSchemaNode);
        LeafSchemaNode sequenceSchemaNode = new LeafSchemaNode("sequence", moduleId, LeafSchemaNode.LeafType.INTEGER);
        ruleListElement.addChildNode("sequence", sequenceSchemaNode);
        LeafSchemaNode descriptionSchemaNode = new LeafSchemaNode("description", moduleId, LeafSchemaNode.LeafType.STRING);
        ruleListElement.addChildNode("description", descriptionSchemaNode);
        ruleListElement.addKeyNodeName("sequence");
        ruleListElement.addKeyNodeName("id");
        ListSchemaNode ruleList = new ListSchemaNode("rule", moduleId, ruleListElement);
        policyListElement.addChildNode("rule", ruleList);
        return rootSchemaNode;
    }

    @Test
    public void test1() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/test/policy[name=\"foo\"]");
        DataNode dataNode = new MemoryListElementDataNode(false,
                Collections.<String,DataNode>singletonMap("name",
                        new MemoryLeafDataNode("foo")));
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, dataNode);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test1");
    }

    @Test
    public void test2() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/counter");
        DataNode dataNode = new MemoryLeafDataNode(100L);
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, dataNode);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test2");
    }

    @Test
    public void test3() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/test/policy[name=\"foo\"]/rule[id=\"foo\"][sequence=100]");
        Map<String, DataNode> initNodes = new HashMap<String, DataNode>();
        initNodes.put("id", new MemoryLeafDataNode("foo"));
        initNodes.put("sequence", new MemoryLeafDataNode(100L));
        DataNode dataNode = new MemoryListElementDataNode(false, initNodes);
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, dataNode);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test3");
    }

    @Test
    public void test4() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/test/policy[name=\"foo\"]/rule[id=\"foo\"][sequence=100]/description");
        DataNode dataNode = new MemoryLeafDataNode("test-description");
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, dataNode);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test4");
    }

    @Test
    public void test5() throws Exception {
        DataNode dataNode = new MemoryContainerDataNode(false, null);
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(
                rootSchemaNode, null, LocationPathExpression.EMPTY_PATH, dataNode);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test5");
    }

    @Test
    public void test6() throws Exception {
        ListSchemaNode listSchemaNode = (ListSchemaNode) rootSchemaNode.getDescendantSchemaNode("/test/policy");
        IndexSpecifier keySpecifier = listSchemaNode.getKeySpecifier();
        IndexValue indexValue = IndexValue.fromValues(keySpecifier,
                Collections.<String,DataNode>singletonMap("name",
                        new MemoryLeafDataNode("foo")));
        LocationPathExpression path = LocationPathExpression.parse("rule[id=\"foo\"][sequence=100]/description");
        DataNode dataNode = new MemoryLeafDataNode("test-description");
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(listSchemaNode, indexValue, path, dataNode);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test6");
    }

    @Test
    public void test7() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/test/policy");
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, DataNode.DELETED);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test7");
    }

    @Test
    public void test8() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/test");
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, DataNode.DELETED);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test8");
    }

    @Test
    public void test9() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/counter");
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, DataNode.DELETED);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test9");
    }

    @Test
    public void test10() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/test/policy[name=\"foo\"]/priority");
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, DataNode.DELETED);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test10");
    }

    @Test
    public void test11() throws Exception {
        LocationPathExpression path = LocationPathExpression.parse("/test/policy");
        ListSchemaNode listSchemaNode = (ListSchemaNode) rootSchemaNode.getDescendantSchemaNode("/test/policy");
        IndexSpecifier keySpecifier = listSchemaNode.getKeySpecifier();
        Map<String, DataNode> initNodes = new HashMap<String, DataNode>();
        initNodes.put("name", new MemoryLeafDataNode("foo"));
        DataNode listElement = new MemoryListElementDataNode(false, initNodes);
        DataNode dataNode = new MemoryKeyedListDataNode(false, keySpecifier,
                Collections.singleton(listElement).iterator());
        DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(rootSchemaNode, null, path, dataNode);
        checkExpectedResult(rootedDataNode, "MakeRootedDataNode.Test11");
    }
}
