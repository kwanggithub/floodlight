package org.projectfloodlight.db.data;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.CompoundDictionaryDataNode;
import org.projectfloodlight.db.data.CompoundKeyedListDataNode;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.IndexSpecifier;
import org.projectfloodlight.db.data.IndexValue;
import org.projectfloodlight.db.data.ListDataNode;
import org.projectfloodlight.db.data.memory.MemoryContainerDataNode;
import org.projectfloodlight.db.data.memory.MemoryKeyedListDataNode;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;
import org.projectfloodlight.db.data.memory.MemoryListElementDataNode;
import org.projectfloodlight.db.schema.ContainerSchemaNode;
import org.projectfloodlight.db.schema.LeafSchemaNode;
import org.projectfloodlight.db.schema.ListElementSchemaNode;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.ModuleIdentifier;
import org.projectfloodlight.db.schema.SchemaNode.LeafType;
import org.projectfloodlight.db.test.MapperTest;

public class CompoundDataNodeTest extends MapperTest {

    @Test
    public void compoundDictionaryDataNode() throws Exception {
        // Manually construct a schema
        // This corresponds to a YANG schema of:
        // container test {
        //     leaf child1 {
        //         type int32;
        //     }
        //     leaf child2 {
        //         type string;
        //     }
        //     container child3 {
        //         leaf child3.1 {
        //             type int32;
        //         }
        //         leaf child3.2 {
        //             type string;
        //         }
        //         leaf child3.3 {
        //             type string;
        //         }
        //     }
        // }
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode = new ContainerSchemaNode("root", moduleId);
        containerSchemaNode.addChildNode("child1",
                new LeafSchemaNode("child1", moduleId, LeafType.INTEGER));
        containerSchemaNode.addChildNode("child2",
                new LeafSchemaNode("child2", moduleId, LeafType.STRING));
        ContainerSchemaNode childContainerSchemaNode =
                new ContainerSchemaNode("child3", moduleId);
        containerSchemaNode.addChildNode("child3", childContainerSchemaNode);
        childContainerSchemaNode.addChildNode("child3.1",
                new LeafSchemaNode("child3.1", moduleId, LeafType.INTEGER));
        childContainerSchemaNode.addChildNode("child3.2",
                new LeafSchemaNode("child3.2", moduleId, LeafType.STRING));
        childContainerSchemaNode.addChildNode("child3.3",
                new LeafSchemaNode("child3.3", moduleId, LeafType.STRING));

        // The first data node contributes child1
        DataNode container1 = new MemoryContainerDataNode(false,
                Collections.<String,DataNode>singletonMap("child1", new MemoryLeafDataNode(100L)));
        // The second data node contributes child2 and child3.1
        DataNode childContainer = new MemoryContainerDataNode(false,
                Collections.<String,DataNode>singletonMap("child3.1", new MemoryLeafDataNode(200L)));
        Map<String, DataNode> initNodes = new HashMap<String, DataNode>();
        initNodes.put("child2", new MemoryLeafDataNode("foo"));
        initNodes.put("child3", childContainer);
        DataNode container2 = new MemoryContainerDataNode(false, initNodes);
        // The third data node contributes child3.2 and child3.3
        initNodes = new HashMap<String, DataNode>();
        initNodes.put("child3.2", new MemoryLeafDataNode("abc"));
        initNodes.put("child3.3", new MemoryLeafDataNode("xyz"));
        childContainer = new MemoryContainerDataNode(false, initNodes);
        DataNode container3 = new MemoryContainerDataNode(false,
                Collections.<String,DataNode>singletonMap("child3", childContainer));

        CompoundDictionaryDataNode.Builder builder =
                new CompoundDictionaryDataNode.Builder(containerSchemaNode);
        builder.addDataNode(container1)
            .addDataNode(container2)
            .addDataNode(container3);
        DataNode compoundDataNode = builder.build();

        // Check properties of the compound data node
        assertThat(compoundDataNode.getNodeType(), is(DataNode.NodeType.CONTAINER));
        assertThat(compoundDataNode.isDictionary(), is(true));
        assertThat(compoundDataNode.isScalar(), is(false));
        assertThat(compoundDataNode.isKeyedList(), is(false));
        assertThat(compoundDataNode.hasChildren(), is(true));

        // Check that it correctly includes all 3 contributions
        assertThat(compoundDataNode.getChild("child1").getLong(), equalTo(100L));
        assertThat(compoundDataNode.getChild("child2").getString(), equalTo("foo"));
        assertThat(compoundDataNode.getChild("child3").getChild("child3.1").getLong(), equalTo(200L));
        assertThat(compoundDataNode.getChild("child3").getChild("child3.2").getString(), equalTo("abc"));
        assertThat(compoundDataNode.getChild("child3").getChild("child3.3").getString(), equalTo("xyz"));

        checkExpectedResult(compoundDataNode, "CompoundDataNodeTest.CompoundDictionaryDataNode");
    }

    private void checkListElement(DataNode listDataNode, IndexValue keyValue,
            String name, long flags, long counter, String description)
            throws BigDBException {
        DataNode listElement = listDataNode.getChild(keyValue);
        assertThat(listElement.getChild("name").getString(), equalTo(name));
        assertThat(listElement.getChild("flags").getLong(), equalTo(flags));
        DataNode info = listElement.getChild("info");
        assertThat(info.getChild("counter").getLong(), equalTo(counter));
        assertThat(info.getChild("description").getString(),
                equalTo(description));
    }

    @Test
    public void compoundKeyedListDataNode() throws Exception {
        // Manually construct a schema
        // This corresponds to a YANG schema of:
        // list test {
        //     key "name";
        //     leaf name {
        //         type string;
        //     }
        //     leaf flags {
        //         type int32;
        //     }
        //     container info {
        //         leaf counter {
        //             type int32;
        //         }
        //         leaf description {
        //             type string;
        //         }
        //     }
        // }
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ListElementSchemaNode listElementSchemaNode =
                new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("name", new LeafSchemaNode("name",
                moduleId, LeafType.STRING));
        listElementSchemaNode.addChildNode("flags", new LeafSchemaNode("flags",
                moduleId, LeafType.INTEGER));
        ContainerSchemaNode containerSchemaNode =
                new ContainerSchemaNode("info", moduleId);
        containerSchemaNode.addChildNode("counter", new LeafSchemaNode(
                "counter", moduleId, LeafType.INTEGER));
        containerSchemaNode.addChildNode("description", new LeafSchemaNode(
                "description", moduleId, LeafType.STRING));
        listElementSchemaNode.addChildNode("info", containerSchemaNode);
        listElementSchemaNode.addKeyNodeName("name");
        ListSchemaNode listSchemaNode =
                new ListSchemaNode("test", moduleId, listElementSchemaNode);

        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames("name");
        DataNode keyDataNode1 = new MemoryLeafDataNode("abc");
        IndexValue keyIndexValue1 =
                IndexValue.fromValues(keySpecifier, Collections
                        .<String, DataNode> singletonMap("name",
                                keyDataNode1));
        DataNode keyDataNode2 = new MemoryLeafDataNode("bar");
        IndexValue keyIndexValue2 =
                IndexValue.fromValues(keySpecifier, Collections
                        .<String, DataNode> singletonMap("name",
                                keyDataNode2));
        DataNode keyDataNode3 = new MemoryLeafDataNode("foobar");
        IndexValue keyIndexValue3 =
                IndexValue.fromValues(keySpecifier, Collections
                        .<String, DataNode> singletonMap("name",
                                keyDataNode3));
        DataNode keyDataNode4 = new MemoryLeafDataNode("xyz");
        IndexValue keyIndexValue4 =
                IndexValue.fromValues(keySpecifier, Collections
                        .<String, DataNode> singletonMap("name",
                                keyDataNode4));

        // Construct the first contribution. This contains parts of the
        // 1st, 3rd, and 4th list elements.
        List<DataNode> initListNodes = new ArrayList<DataNode>();
        Map<String, DataNode> initListElementNodes =
                new HashMap<String, DataNode>();
        Map<String, DataNode> initContainerNodes =
                new HashMap<String, DataNode>();
        initListElementNodes.put("name", keyDataNode1);
        initListElementNodes.put("flags", new MemoryLeafDataNode(11L));
        initContainerNodes.put("counter", new MemoryLeafDataNode(111L));
        DataNode containerDataNode =
                new MemoryContainerDataNode(false, initContainerNodes);
        initListElementNodes.put("info", containerDataNode);
        DataNode listElement =
                new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement);

        initListElementNodes = new HashMap<String, DataNode>();
        initContainerNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", keyDataNode3);
        initContainerNodes.put("counter", new MemoryLeafDataNode(333L));
        initContainerNodes.put("description", new MemoryLeafDataNode("hello3"));
        containerDataNode =
                new MemoryContainerDataNode(false, initContainerNodes);
        initListElementNodes.put("info", containerDataNode);
        listElement =
                new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement);

        initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", keyDataNode4);
        initListElementNodes.put("flags", new MemoryLeafDataNode(44L));
        listElement =
                new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement);

        ListDataNode list1 =
                new MemoryKeyedListDataNode(false, keySpecifier, initListNodes
                        .iterator());

        // Construct the second contribution. This contains parts of the
        // 2nd and 3rd list elements.
        initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("flags", new MemoryLeafDataNode(22L));
        initContainerNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", keyDataNode2);
        initContainerNodes.put("counter", new MemoryLeafDataNode(222L));
        initContainerNodes.put("description", new MemoryLeafDataNode("hello2"));
        containerDataNode =
                new MemoryContainerDataNode(false, initContainerNodes);
        initListElementNodes.put("info", containerDataNode);
        listElement =
                new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement);

        initListElementNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", keyDataNode3);
        initListElementNodes.put("flags", new MemoryLeafDataNode(33L));
        listElement =
                new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement);

        ListDataNode list2 =
                new MemoryKeyedListDataNode(false, keySpecifier, initListNodes
                        .iterator());

        // Construct the second contribution. This contains parts of the
        // 1st and 4th list elements.
        initListElementNodes = new HashMap<String, DataNode>();
        initContainerNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", keyDataNode1);
        initContainerNodes.put("description", new MemoryLeafDataNode("hello1"));
        containerDataNode =
                new MemoryContainerDataNode(false, initContainerNodes);
        initListElementNodes.put("info", containerDataNode);
        listElement =
                new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement);

        initListElementNodes = new HashMap<String, DataNode>();
        initContainerNodes = new HashMap<String, DataNode>();
        initListElementNodes.put("name", keyDataNode4);
        initContainerNodes.put("counter", new MemoryLeafDataNode(444L));
        initContainerNodes.put("description", new MemoryLeafDataNode("hello4"));
        containerDataNode =
                new MemoryContainerDataNode(false, initContainerNodes);
        initListElementNodes.put("info", containerDataNode);
        listElement =
                new MemoryListElementDataNode(false, initListElementNodes);
        initListNodes.add(listElement);

        ListDataNode list3 =
                new MemoryKeyedListDataNode(false, keySpecifier, initListNodes
                        .iterator());

        CompoundKeyedListDataNode.Builder builder = new CompoundKeyedListDataNode.Builder(listSchemaNode);
        builder.addDataNode(list1).addDataNode(list2).addDataNode(list3);
        DataNode compoundDataNode = builder.build();

        // Check properties of the compound data node
        assertThat(compoundDataNode.getNodeType(), is(DataNode.NodeType.LIST));
        assertThat(compoundDataNode.isDictionary(), is(false));
        assertThat(compoundDataNode.isScalar(), is(false));
        assertThat(compoundDataNode.isKeyedList(), is(true));
        assertThat(compoundDataNode.hasChildren(), is(true));

        // Check that it correctly includes all 3 contributions
        checkListElement(compoundDataNode, keyIndexValue1, "abc", 11, 111, "hello1");
        checkListElement(compoundDataNode, keyIndexValue2, "bar", 22, 222, "hello2");
        checkListElement(compoundDataNode, keyIndexValue3, "foobar", 33, 333, "hello3");
        checkListElement(compoundDataNode, keyIndexValue4, "xyz", 44, 444, "hello4");

        checkExpectedResult(compoundDataNode, "CompoundDataNodeTest.CompoundKeyedListDataNode");
    }
}
