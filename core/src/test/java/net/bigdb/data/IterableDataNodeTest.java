package net.bigdb.data;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.bigdb.data.DataNode.KeyedListEntry;
import net.bigdb.data.memory.MemoryLeafDataNode;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.SchemaNode.LeafType;
import net.bigdb.test.MapperTest;

import org.junit.Test;

public class IterableDataNodeTest extends MapperTest {

    public static class Bean {
        private final String name;
        private final long count;
        public Bean(String name, long count) {
            this.name = name;
            this.count = count;
        }
        public String getName() {
            return name;
        }
        public long getCount() {
            return count;
        }
    }

    @Test
    public void iterableKeyedList() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ListElementSchemaNode listElementSchemaNode =
                new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("name", new LeafSchemaNode("name",
                moduleId, LeafType.STRING));
        listElementSchemaNode.addChildNode("count", new LeafSchemaNode("count",
                moduleId, LeafType.INTEGER));
        listElementSchemaNode.addKeyNodeName("name");
        ListSchemaNode listSchemaNode =
                new ListSchemaNode("test", moduleId, listElementSchemaNode);

        List<Bean> beanList = new ArrayList<Bean>();
        beanList.add(new Bean("abc", 100));
        beanList.add(new Bean("xyz", 200));
        DataNode listDataNode = IterableKeyedListDataNode.from(listSchemaNode, beanList);

        // Check properties of the compound data node
        assertThat(listDataNode.getNodeType(), is(DataNode.NodeType.LIST));
        assertThat(listDataNode.isDictionary(), is(false));
        assertThat(listDataNode.isScalar(), is(false));
        assertThat(listDataNode.isKeyedList(), is(true));
        assertThat(listDataNode.hasChildren(), is(true));
        assertThat(listDataNode.childCount(), is(2));

        // Test keyed list entry iteration
        Iterator<KeyedListEntry> iter = listDataNode.getKeyedListEntries().iterator();
        assertThat(iter.hasNext(), is(true));
        KeyedListEntry entry = iter.next();
        assertThat(entry.getKeyValue().toString(), is("abc"));
        assertThat(entry.getDataNode().getChild("name").getString(), is("abc"));
        assertThat(entry.getDataNode().getChild("count").getString(), is("100"));
        entry = iter.next();
        assertThat(entry.getKeyValue().toString(), is("xyz"));
        assertThat(entry.getDataNode().getChild("name").getString(), is("xyz"));
        assertThat(entry.getDataNode().getChild("count").getString(), is("200"));
        assertThat(iter.hasNext(), is(false));

        // Test looking up a child by key value
        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames("name");
        IndexValue keyValue =
                IndexValue.fromValues(keySpecifier, Collections
                        .<String, DataNode> singletonMap("name",
                                new MemoryLeafDataNode("abc")));
        DataNode listElementDataNode = listDataNode.getChild(keyValue);
        assertThat(listElementDataNode.getChild("name").getString(), is("abc"));
        assertThat(listElementDataNode.getChild("count").getLong(), is(100L));

        // Check that the overall results are as expected
        checkExpectedResult(listDataNode,
                "IterableDataNodeTest.IterableKeyedList");
    }

    @Test
    public void iterableUnkeyedList() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ListElementSchemaNode listElementSchemaNode =
                new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("name", new LeafSchemaNode("name",
                moduleId, LeafType.STRING));
        listElementSchemaNode.addChildNode("count", new LeafSchemaNode("count",
                moduleId, LeafType.INTEGER));
        ListSchemaNode listSchemaNode =
                new ListSchemaNode("test", moduleId, listElementSchemaNode);

        List<Bean> beanList = new ArrayList<Bean>();
        beanList.add(new Bean("abc", 100));
        beanList.add(new Bean("xyz", 200));
        DataNode listDataNode = IterableUnkeyedListDataNode.from(listSchemaNode, beanList);

        // Check properties of the compound data node
        assertThat(listDataNode.getNodeType(), is(DataNode.NodeType.LIST));
        assertThat(listDataNode.isDictionary(), is(false));
        assertThat(listDataNode.isScalar(), is(false));
        assertThat(listDataNode.isKeyedList(), is(false));
        assertThat(listDataNode.isArray(), is(true));
        assertThat(listDataNode.hasChildren(), is(true));
        assertThat(listDataNode.childCount(), is(2));

        // Test iteration
        Iterator<DataNode> iter = listDataNode.iterator();
        assertThat(iter.hasNext(), is(true));
        DataNode listElementDataNode = iter.next();
        assertThat(listElementDataNode.getChild("name").getString(), is("abc"));
        assertThat(listElementDataNode.getChild("count").getString(), is("100"));
        listElementDataNode = iter.next();
        assertThat(listElementDataNode.getChild("name").getString(), is("xyz"));
        assertThat(listElementDataNode.getChild("count").getString(), is("200"));
        assertThat(iter.hasNext(), is(false));

        // Check that the overall results are as expected
        checkExpectedResult(listDataNode,
                "IterableDataNodeTest.IterableUnkeyedList");
    }

    @Test
    public void iterableLeafList() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafSchemaNode leafSchemaNode =
                new LeafSchemaNode("", moduleId, LeafSchemaNode.LeafType.INTEGER);
        LeafListSchemaNode leafListSchemaNode =
                new LeafListSchemaNode("test", moduleId, leafSchemaNode);

        List<Long> longList = new ArrayList<Long>();
        longList.add(3L);
        longList.add(2L);
        longList.add(1L);

        DataNode listDataNode = IterableLeafListDataNode.from(leafListSchemaNode, longList);

        // Check properties of the compound data node
        assertThat(listDataNode.getNodeType(), is(DataNode.NodeType.LEAF_LIST));
        assertThat(listDataNode.isDictionary(), is(false));
        assertThat(listDataNode.isScalar(), is(false));
        assertThat(listDataNode.isKeyedList(), is(false));
        assertThat(listDataNode.isArray(), is(true));
        assertThat(listDataNode.hasChildren(), is(true));
        assertThat(listDataNode.childCount(), is(3));

        // Test iteration
        Iterator<DataNode> iter = listDataNode.iterator();
        assertThat(iter.hasNext(), is(true));
        DataNode dataNode = iter.next();
        assertEquals(dataNode.getLong(), 3);
        dataNode = iter.next();
        assertEquals(dataNode.getLong(), 2);
        dataNode = iter.next();
        assertEquals(dataNode.getLong(), 1);
        assertThat(iter.hasNext(), is(false));

        // Check that the overall results are as expected
        checkExpectedResult(listDataNode,
                "IterableDataNodeTest.IterableLeafList");
    }
}
