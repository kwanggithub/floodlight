package org.projectfloodlight.db.data;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.IndexSpecifier;
import org.projectfloodlight.db.data.NullDataNode;
import org.projectfloodlight.db.data.memory.MemoryContainerDataNode;
import org.projectfloodlight.db.data.memory.MemoryKeyedListDataNode;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;
import org.projectfloodlight.db.data.memory.MemoryLeafListDataNode;
import org.projectfloodlight.db.data.memory.MemoryListElementDataNode;
import org.projectfloodlight.db.data.memory.MemoryUnkeyedListDataNode;

public class DataNodeDigestTest {

    @Test
    public void digestLeaf() throws Exception {
        // Compare null data nodes
        DataNode null1 = new NullDataNode();
        assertThat(null1.getDigestValue(), equalTo(DataNode.NULL.getDigestValue()));

        // Compare long leaf nodes
        DataNode long1 = new MemoryLeafDataNode(50L);
        DataNode long2 = new MemoryLeafDataNode(50L);
        DataNode long3 = new MemoryLeafDataNode(100L);
        assertThat(long1.getDigestValue(), equalTo(long2.getDigestValue()));
        assertThat(long1.getDigestValue(), not(equalTo(long3.getDigestValue())));

        // Compare boolean leaf nodes
        DataNode boolean1 = new MemoryLeafDataNode(true);
        DataNode boolean2 = new MemoryLeafDataNode(false);
        DataNode boolean3 = new MemoryLeafDataNode(true);
        assertThat(boolean1.getDigestValue(), not(equalTo(boolean2.getDigestValue())));
        assertThat(boolean1.getDigestValue(), equalTo(boolean3.getDigestValue()));

        // Compare string leaf nodes
        DataNode string1 = new MemoryLeafDataNode("foo");
        DataNode string2 = new MemoryLeafDataNode("50");
        DataNode string3 = new MemoryLeafDataNode("true");
        DataNode string4 = new MemoryLeafDataNode("foo");
        DataNode string5 = new MemoryLeafDataNode("");
        assertThat(string1.getDigestValue(), not(equalTo(string2.getDigestValue())));
        assertThat(string1.getDigestValue(), not(equalTo(string3.getDigestValue())));
        assertThat(string2.getDigestValue(), not(equalTo(long1.getDigestValue())));
        assertThat(string3.getDigestValue(), not(equalTo(boolean1.getDigestValue())));
        assertThat(string1.getDigestValue(), equalTo(string4.getDigestValue()));
        assertThat(string5.getDigestValue(), not(equalTo(DataNode.NULL.getDigestValue())));

        // Compare BigInteger leaf nodes
        DataNode bigInt1 = new MemoryLeafDataNode(new BigInteger("30000"));
        DataNode bigInt2 = new MemoryLeafDataNode(new BigInteger("30000"));
        DataNode bigInt3 = new MemoryLeafDataNode(new BigInteger("1231342452345234"));
        DataNode bigInt4 = new MemoryLeafDataNode(new BigInteger("50"));
        assertThat(bigInt1.getDigestValue(), equalTo(bigInt2.getDigestValue()));
        assertThat(bigInt1.getDigestValue(), not(equalTo(bigInt3.getDigestValue())));
        assertThat(bigInt4.getDigestValue(), not(equalTo(long1.getDigestValue())));

        // Compare BigInteger leaf nodes
        DataNode bigDecimal1 = new MemoryLeafDataNode(new BigDecimal("30000.1235345"));
        DataNode bigDecimal2 = new MemoryLeafDataNode(new BigDecimal("30000.1235345"));
        DataNode bigDecimal3 = new MemoryLeafDataNode(new BigDecimal("1231342452345234.000009"));
        DataNode bigDecimal4 = new MemoryLeafDataNode(new BigDecimal("50"));
        assertThat(bigDecimal1.getDigestValue(), equalTo(bigDecimal2.getDigestValue()));
        assertThat(bigDecimal1.getDigestValue(), not(equalTo(bigDecimal3.getDigestValue())));
        assertThat(bigDecimal4.getDigestValue(), not(equalTo(long1.getDigestValue())));
    }

    @Test
    public void digestContainer() throws Exception {
        DataNode leaf1 = new MemoryLeafDataNode(1000L);
        DataNode leaf1_1 = new MemoryLeafDataNode(1000L);
        DataNode leaf2 = new MemoryLeafDataNode("foobar");
        DataNode leaf3 = new MemoryLeafDataNode("test");
        DataNode leaf4 = new MemoryLeafDataNode(true);
        Map<String,DataNode> nodes = new HashMap<String,DataNode>();
        nodes.put("child1", leaf1);
        nodes.put("child2", leaf2);
        DataNode container1 = new MemoryContainerDataNode(false, nodes);
        nodes = new HashMap<String,DataNode>();
        nodes.put("child1", leaf1_1);
        nodes.put("child2", leaf2);
        DataNode container2 = new MemoryContainerDataNode(false, nodes);
        nodes = new HashMap<String,DataNode>();
        nodes.put("child1", leaf1_1);
        nodes.put("child2", container1);
        nodes.put("child3", leaf3);
        DataNode container3 = new MemoryContainerDataNode(false, nodes);
        nodes = new HashMap<String,DataNode>();
        nodes.put("child1", leaf1);
        nodes.put("child2", container2);
        nodes.put("child3", leaf3);
        DataNode container4 = new MemoryContainerDataNode(false, nodes);
        nodes = new HashMap<String,DataNode>();
        nodes.put("child1", leaf1);
        nodes.put("child2", container2);
        nodes.put("child3", leaf4);
        DataNode container5 = new MemoryContainerDataNode(false, nodes);

        assertTrue(container1.equals(container2));

        assertThat(container1.getDigestValue(), equalTo(container2.getDigestValue()));
        assertThat(container1.getDigestValue(), not(equalTo(leaf1.getDigestValue())));
        assertThat(container1.getDigestValue(), not(equalTo(container3.getDigestValue())));
        assertThat(container3.getDigestValue(), equalTo(container4.getDigestValue()));
        assertThat(container1.getDigestValue(), not(equalTo(container5.getDigestValue())));
        assertThat(container4.getDigestValue(), not(equalTo(container5.getDigestValue())));
    }

    @Test
    public void digestLeafList() throws Exception {
        DataNode leaf1 = new MemoryLeafDataNode("foo");
        DataNode leaf1_1 = new MemoryLeafDataNode("foo");
        DataNode leaf2 = new MemoryLeafDataNode("test");
        DataNode leaf3 = new MemoryLeafDataNode("bar");
        List<DataNode> nodes = new ArrayList<DataNode>();
        nodes.add(leaf1);
        nodes.add(leaf2);
        DataNode leafList1 = new MemoryLeafListDataNode(false, nodes);
        nodes = new ArrayList<DataNode>();
        nodes.add(leaf1_1);
        nodes.add(leaf2);
        DataNode leafList2 = new MemoryLeafListDataNode(false, nodes);
        nodes = new ArrayList<DataNode>();
        nodes.add(leaf1_1);
        DataNode leafList3 = new MemoryLeafListDataNode(false, nodes);
        nodes = new ArrayList<DataNode>();
        nodes.add(leaf1_1);
        nodes.add(leaf3);
        DataNode leafList4 = new MemoryLeafListDataNode(false, nodes);

        assertThat(leafList1.getDigestValue(), equalTo(leafList2.getDigestValue()));
        assertThat(leafList1.getDigestValue(), not(equalTo(leafList3.getDigestValue())));
        assertThat(leafList2.getDigestValue(), not(equalTo(leafList4.getDigestValue())));
    }

    private DataNode makeListElement(String name, long count, String description)
            throws BigDBException {
        Map<String, DataNode> nodes = new HashMap<String, DataNode>();
        nodes.put("name", new MemoryLeafDataNode(name));
        nodes.put("count", new MemoryLeafDataNode(count));
        if (description != null)
            nodes.put("description", new MemoryLeafDataNode(description));
        DataNode listDataNode = new MemoryListElementDataNode(false, nodes);
        return listDataNode;
    }

    private DataNode makeList(IndexSpecifier keySpecifier,
            DataNode... dataNodes) throws BigDBException {
        List<DataNode> nodes = new ArrayList<DataNode>();
        for (DataNode dataNode: dataNodes) {
            nodes.add(dataNode);
        }
        return (keySpecifier != null) ?
                new MemoryKeyedListDataNode(false, keySpecifier, nodes.iterator()) :
                new MemoryUnkeyedListDataNode(false, nodes.iterator());
    }

    @Test
    public void digestList() throws Exception {
        DataNode listElement1 = makeListElement("elem1", 50, "foo");
        DataNode listElement1_1 = makeListElement("elem1", 50, "foo");
        DataNode listElement2 = makeListElement("elem2", 100, "foo");
        DataNode listElement3 = makeListElement("elem3", 50, null);

        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames(
                Collections.singletonList("name"));
        IndexSpecifier keySpecifier2 = IndexSpecifier.fromFieldNames(
                Collections.singletonList("count"));
        DataNode keyedList1 = makeList(keySpecifier, listElement1, listElement2);
        DataNode keyedList2 = makeList(keySpecifier, listElement1_1, listElement2);
        DataNode keyedList3 = makeList(keySpecifier, listElement1, listElement3);
        DataNode keyedList4 = makeList(keySpecifier, listElement3);
        DataNode keyedList5 = makeList(keySpecifier);
        DataNode keyedList6 = makeList(keySpecifier2, listElement1, listElement2);

        assertThat(keyedList1.getDigestValue(), equalTo(keyedList2.getDigestValue()));
        assertThat(keyedList1.getDigestValue(), not(equalTo(keyedList3.getDigestValue())));
        assertThat(keyedList3.getDigestValue(), not(equalTo(keyedList4.getDigestValue())));
        assertThat(keyedList1.getDigestValue(), not(equalTo(keyedList5.getDigestValue())));
        assertThat(keyedList1.getDigestValue(), not(equalTo(keyedList6.getDigestValue())));

        DataNode unkeyedList1 = makeList(null, listElement1, listElement2);
        DataNode unkeyedList2 = makeList(null, listElement1_1, listElement2);
        DataNode unkeyedList3 = makeList(null, listElement1, listElement3);
        DataNode unkeyedList4 = makeList(null, listElement3);
        DataNode unkeyedList5 = makeList(null);
        DataNode unkeyedList6 = makeList(null, listElement2, listElement1);

        assertThat(unkeyedList1.getDigestValue(), equalTo(unkeyedList2.getDigestValue()));
        assertThat(keyedList1.getDigestValue(), not(equalTo(unkeyedList3.getDigestValue())));
        assertThat(keyedList3.getDigestValue(), not(equalTo(unkeyedList4.getDigestValue())));
        assertThat(keyedList1.getDigestValue(), not(equalTo(unkeyedList5.getDigestValue())));
        assertThat(unkeyedList1.getDigestValue(), not(equalTo(unkeyedList6.getDigestValue())));
        assertThat(unkeyedList1.getDigestValue(), not(equalTo(keyedList1.getDigestValue())));

        MemoryKeyedListDataNode mutableList = new MemoryKeyedListDataNode(keySpecifier);
        mutableList.add(listElement1);
        try {
            mutableList.getDigestValue();
            fail("Expected exception when getting digest value on mutable data node");
        }
        catch (BigDBException e) {
        }
    }
}
