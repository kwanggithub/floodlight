package net.bigdb.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

import static org.junit.Assert.*;

import net.bigdb.data.memory.MemoryDataNodeFactory;
import net.bigdb.data.serializers.AbstractISODateDataNodeSerializer;
import net.bigdb.test.MapperTest;

import org.junit.Test;

public class DataNodeJsonSerializationTest extends MapperTest {
    
    protected DataNodeFactory dataNodeFactory;
    
    public DataNodeJsonSerializationTest() {
        this.dataNodeFactory = new MemoryDataNodeFactory();
    }
    
//  public DataNodeJsonSerializationTest(DataNodeFactory dataNodeFactory) {
//      this.dataNodeFactory = dataNodeFactory;
//  }
    
    @Test
    public void testSerializeLeafDataNodes() throws Exception {
        ContainerDataNode containerDataNode =
                dataNodeFactory.createContainerDataNode(true, null);
        LeafDataNode dataNode = dataNodeFactory.createLeafDataNode(1234L);
        containerDataNode.put("int", dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(true);
        containerDataNode.put("boolean", dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(
                new BigInteger("21422452345234234"));
        containerDataNode.put("biginteger", dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(
                new BigDecimal("21422452342.34234"));
        containerDataNode.put("bigdecimal", dataNode);
        dataNode = dataNodeFactory.createLeafDataNode("HelloWorld");
        containerDataNode.put("string", dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(23456.3456);
        containerDataNode.put("double", dataNode);
        byte[] bytes = { 1, 2, 3 };
        dataNode = dataNodeFactory.createLeafDataNode(bytes);
        containerDataNode.put("binary", dataNode);
        checkExpectedResult(containerDataNode, "TestSerializeLeafDataNodes");
    }
    
    @Test
    public void testSerializeListDataNode() throws Exception {
        ListDataNode listDataNode =
                dataNodeFactory.createListDataNode(true, null, null);
        ListElementDataNode entryDataNode =
                dataNodeFactory.createListElementDataNode(true, null);
        DataNode leafDataNode = dataNodeFactory.createLeafDataNode("Bar");
        entryDataNode.put("Foo", leafDataNode);
        leafDataNode = dataNodeFactory.createLeafDataNode(1234);
        entryDataNode.put("Int", leafDataNode);
        listDataNode.add(entryDataNode);
        
        entryDataNode = dataNodeFactory.createListElementDataNode(true, null);
        leafDataNode = dataNodeFactory.createLeafDataNode("Bar2");
        entryDataNode.put("Foo", leafDataNode);
        leafDataNode = dataNodeFactory.createLeafDataNode(5678);
        entryDataNode.put("Int", leafDataNode);
        listDataNode.add(entryDataNode);

        checkExpectedResult(listDataNode, "TestSerializeListDataNode");
    }
    
    @Test
    public void testSerializeLeafListDataNode() throws Exception {
        LeafListDataNode leafListDataNode =
                dataNodeFactory.createLeafListDataNode(true, null);
        
        LeafDataNode dataNode = dataNodeFactory.createLeafDataNode(1);
        leafListDataNode.add(dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(2);
        leafListDataNode.add(dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(3);
        leafListDataNode.add(dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(4);
        leafListDataNode.add(dataNode);
        
        checkExpectedResult(leafListDataNode, "TestSerializeLeafListDataNode");
    }
    
    @Test
    public void testSerializeComplexDataNodes() throws Exception {
        ContainerDataNode containerDataNode =
                dataNodeFactory.createContainerDataNode(true, null);
        
        LeafDataNode dataNode =
                dataNodeFactory.createLeafDataNode("TestValue");
        containerDataNode.put("Test", dataNode);
        
        ListDataNode listDataNode = dataNodeFactory.createListDataNode(true, null, null);
        ListElementDataNode entryDataNode =
                dataNodeFactory.createListElementDataNode(true, null);
        dataNode = dataNodeFactory.createLeafDataNode("Bar");
        entryDataNode.put("Foo", dataNode);
        LeafListDataNode leafListDataNode =
                dataNodeFactory.createLeafListDataNode(true, null);
        dataNode = dataNodeFactory.createLeafDataNode(1);
        leafListDataNode.add(dataNode);
        dataNode = dataNodeFactory.createLeafDataNode(2);
        leafListDataNode.add(dataNode);
        entryDataNode.put("IntList", leafListDataNode);
        listDataNode.add(entryDataNode);
        containerDataNode.put("List", listDataNode);
        
        checkExpectedResult(containerDataNode, "TestSerializeComplexDataNodes");
    }
    
    @Test
    public void testSerializeISODate() {
        long d = (new Date()).getTime();

        String dateString = AbstractISODateDataNodeSerializer.formatISO(new Date(d));
        Date d1 = AbstractISODateDataNodeSerializer.parse(dateString);
        assertEquals(d, d1.getTime());
    }
}
