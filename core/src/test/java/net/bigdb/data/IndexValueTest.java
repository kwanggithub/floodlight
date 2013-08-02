package net.bigdb.data;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.data.memory.MemoryLeafDataNode;
import net.bigdb.data.memory.MemoryListElementDataNode;

import org.junit.Test;

public class IndexValueTest {

    private DataNode getListElementDataNode(String name, long priority)
            throws BigDBException {
        DataNode nameDataNode = new MemoryLeafDataNode(name);
        DataNode priorityDataNode = new MemoryLeafDataNode(priority);
        Map<String, DataNode> initNodes = new HashMap<String, DataNode>();
        initNodes.put("name", nameDataNode);
        initNodes.put("priority", priorityDataNode);
        DataNode listElementDataNode = new MemoryListElementDataNode(false, initNodes);
        return listElementDataNode;
    }

    @Test
    public void stringKeyField() throws Exception {
        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames("name");
        DataNode listElement1 = getListElementDataNode("jack", 100);
        IndexValue indexValue1 = IndexValue.fromListElement(keySpecifier, listElement1);
        DataNode listElement2 = getListElementDataNode("henry", 200);
        IndexValue indexValue2 = IndexValue.fromListElement(keySpecifier, listElement2);
        DataNode listElement3 = getListElementDataNode("henry", 200);
        IndexValue indexValue3 = IndexValue.fromListElement(keySpecifier, listElement3);
        int hashCode3 = indexValue3.hashCode();
        DataNode listElement4 = getListElementDataNode("HENRY", 200);
        IndexValue indexValue4 = IndexValue.fromListElement(keySpecifier, listElement4);
        int hashCode4 = indexValue4.hashCode();
        int result = indexValue1.compareTo(indexValue2);
        assertTrue(result > 0);
        result = indexValue2.compareTo(indexValue1);
        assert(result < 0);
        result = indexValue2.compareTo(indexValue3);
        assertEquals(result, 0);
        assertTrue(indexValue2.equals(indexValue3));
        assertTrue(indexValue3.equals(indexValue2));
        assertTrue(hashCode3 != hashCode4);
    }

    @Test
    public void buildFromValues() throws Exception {
        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames("name", "priority");
        DataNode nameDataNode = new MemoryLeafDataNode("john");
        DataNode priorityDataNode = new MemoryLeafDataNode(200L);
        IndexValue.Builder builder = new IndexValue.Builder(keySpecifier);
        IndexValue indexValue = builder.addValue("name", nameDataNode)
                .addValue("priority", priorityDataNode)
                .getIndexValue();
        assertEquals(indexValue.getIndexSpecifier(), keySpecifier);
        assertEquals(indexValue.getDataNode().getChild("name").getString(), "john");
        assertTrue(indexValue.getDataNode().getChild("priority").getLong() == 200);
    }

    @Test
    public void multipleKeyFields() throws Exception {
        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames("priority", "name");
        DataNode listElement1 = getListElementDataNode("jack", 30);
        IndexValue indexValue1 = IndexValue.fromListElement(keySpecifier, listElement1);
        DataNode listElement2 = getListElementDataNode("henry", 30);
        IndexValue indexValue2 = IndexValue.fromListElement(keySpecifier, listElement2);
        DataNode listElement3 = getListElementDataNode("henry", 100);
        IndexValue indexValue3 = IndexValue.fromListElement(keySpecifier, listElement3);
        DataNode listElement4 = getListElementDataNode("bob", 1000);
        IndexValue indexValue4 = IndexValue.fromListElement(keySpecifier, listElement4);
        int result = indexValue1.compareTo(indexValue2);
        assertTrue(result > 0);
        result = indexValue2.compareTo(indexValue3);
        assertTrue(result < 0);
        result = indexValue1.compareTo(indexValue3);
        assertTrue(result < 0);
        result = indexValue3.compareTo(indexValue4);
        assertTrue(result < 0);
    }

    @Test
    public void reverseSortOrder() throws Exception {
        IndexSpecifier.Builder builder = new IndexSpecifier.Builder(false);
        builder.addField("name", IndexSpecifier.SortOrder.REVERSE, false);
        IndexSpecifier keySpecifier = builder.getIndexSpecifier();
        DataNode listElement1 = getListElementDataNode("jack", 30);
        IndexValue indexValue1 = IndexValue.fromListElement(keySpecifier, listElement1);
        DataNode listElement2 = getListElementDataNode("henry", 30);
        IndexValue indexValue2 = IndexValue.fromListElement(keySpecifier, listElement2);
        int result = indexValue1.compareTo(indexValue2);
        assertTrue(result < 0);
    }

    @Test
    public void caseInsensitiveField() throws Exception {
        IndexSpecifier.Builder builder = new IndexSpecifier.Builder(false);
        builder.addField("name", IndexSpecifier.SortOrder.FORWARD, false);
        IndexSpecifier keySpecifier = builder.getIndexSpecifier();
        DataNode listElement1 = getListElementDataNode("jack", 30);
        IndexValue indexValue1 = IndexValue.fromListElement(keySpecifier, listElement1);
        int hashCode1 = indexValue1.hashCode();
        DataNode listElement2 = getListElementDataNode("JACK", 30);
        IndexValue indexValue2 = IndexValue.fromListElement(keySpecifier, listElement2);
        int hashCode2 = indexValue2.hashCode();
        int result = indexValue1.compareTo(indexValue2);
        assertTrue(result == 0);
        assertTrue(hashCode1 == hashCode2);
    }
}
