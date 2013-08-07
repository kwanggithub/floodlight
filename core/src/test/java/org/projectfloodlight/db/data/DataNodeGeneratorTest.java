package org.projectfloodlight.db.data;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.schema.ContainerSchemaNode;
import org.projectfloodlight.db.schema.LeafListSchemaNode;
import org.projectfloodlight.db.schema.LeafSchemaNode;
import org.projectfloodlight.db.schema.ListElementSchemaNode;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.ModuleIdentifier;
import org.projectfloodlight.db.schema.SchemaNode.LeafType;
import org.projectfloodlight.db.test.MapperTest;

public class DataNodeGeneratorTest extends MapperTest {

    @Test
    public void testLeafDataNodeGenerator() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode = new ContainerSchemaNode("", moduleId);
        containerSchemaNode.addChildNode("TestBoolean",
                new LeafSchemaNode("TestBoolean", moduleId, LeafType.BOOLEAN));
        containerSchemaNode.addChildNode("TestLong",
                new LeafSchemaNode("TestLong", moduleId, LeafType.INTEGER));
        containerSchemaNode.addChildNode("TestInt",
                new LeafSchemaNode("TestInt", moduleId, LeafType.INTEGER));
        containerSchemaNode.addChildNode("TestBigInteger",
                new LeafSchemaNode("TestBigInteger", moduleId, LeafType.INTEGER));
        containerSchemaNode.addChildNode("TestBigDecimal",
                new LeafSchemaNode("TestBigDecimal", moduleId, LeafType.DECIMAL));
        containerSchemaNode.addChildNode("TestDouble",
                new LeafSchemaNode("TestDouble", moduleId, LeafType.DECIMAL));
        containerSchemaNode.addChildNode("TestBinary",
                new LeafSchemaNode("TestBinary", moduleId, LeafType.BINARY));
        containerSchemaNode.addChildNode("TestString",
                new LeafSchemaNode("TestString", moduleId, LeafType.STRING));
        
        DataNodeGenerator generator = new DataNodeGenerator(containerSchemaNode);
        generator.writeMapStart();
        generator.writeBooleanField("TestBoolean", Boolean.TRUE);
        generator.writeNumberField("TestLong", 1234L);
        generator.writeNumberField("TestInt", 1234);
        generator.writeNumberField("TestBigInteger",
                new BigInteger("34524572934528734509834"));
        generator.writeNumberField("TestBigDecimal",
                new BigDecimal("3452457293452873450.9834"));
        generator.writeNumberField("TestDouble", 3452938457.234234);
        byte[] binaryData = { 1, 2, 3, 4 };
        generator.writeBinaryField("TestBinary", binaryData);
        generator.writeStringField("TestString", "Foobar");
        generator.writeMapEnd();
        
        checkExpectedResult(generator.getResult(), "TestLeafDataNodeGenerator");
    }
    
    @Test
    public void testLeafListDataNodeGenerator() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafListSchemaNode leafListSchemaNode =
                new LeafListSchemaNode("", moduleId,
                new LeafSchemaNode("", moduleId, LeafType.INTEGER));
        
        DataNodeGenerator generator = new DataNodeGenerator(leafListSchemaNode);
        generator.writeListStart();
        generator.writeNumber(123);
        generator.writeNumber(234);
        generator.writeNumber(345);
        generator.writeListEnd();
        
        checkExpectedResult(generator.getResult(), "TestLeafListDataNodeGenerator");
    }
    
    @Test
    public void testListDataNodeGenerator() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ListElementSchemaNode listElementSchemaNode =
                new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("StringField",
                new LeafSchemaNode("StringField", moduleId, LeafType.STRING));
        listElementSchemaNode.addChildNode("NumberField",
                new LeafSchemaNode("NumberField", moduleId, LeafType.INTEGER));
        ListSchemaNode listSchemaNode =
                new ListSchemaNode("", moduleId, listElementSchemaNode);
        
        DataNodeGenerator generator = new DataNodeGenerator(listSchemaNode);
        generator.writeListStart();
        generator.writeMapStart();
        generator.writeStringField("StringField", "Foo1");
        generator.writeNumberField("NumberField", 1234);
        generator.writeMapEnd();
        generator.writeMapStart();
        generator.writeStringField("StringField", "Foo2");
        generator.writeNumberField("NumberField", 2345);
        generator.writeMapEnd();
        generator.writeListEnd();
        
        checkExpectedResult(generator.getResult(), "TestListDataNodeGenerator");
    }
    
    @Test
    public void testNestedDataNodeGenerator() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode = new ContainerSchemaNode("", moduleId);
        containerSchemaNode.addChildNode("ChildInt",
                new LeafSchemaNode("ChildInt", moduleId, LeafType.INTEGER));
        ContainerSchemaNode childContainerSchemaNode =
                new ContainerSchemaNode("ChildContainer", moduleId);
        LeafListSchemaNode leafListSchemaNode =
                new LeafListSchemaNode("ChildLeafList", moduleId,
                new LeafSchemaNode("", moduleId, LeafType.STRING));
        childContainerSchemaNode.addChildNode("ChildLeafList", leafListSchemaNode);
        ListElementSchemaNode listElementSchemaNode =
                new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("ID",
                new LeafSchemaNode("ID", moduleId, LeafType.STRING));
        listElementSchemaNode.addChildNode("Ratio",
                new LeafSchemaNode("Ratio", moduleId, LeafType.DECIMAL));
        ListSchemaNode childListSchemaNode =
                new ListSchemaNode("ChildList", moduleId, listElementSchemaNode);
        childContainerSchemaNode.addChildNode("ChildList", childListSchemaNode);
        containerSchemaNode.addChildNode("ChildContainer", childContainerSchemaNode);
                
        DataNodeGenerator generator = new DataNodeGenerator(containerSchemaNode);
        generator.writeMapStart();
        generator.writeNumberField("ChildInt", 2345);
        generator.writeMapFieldStart("ChildContainer");
        generator.writeListFieldStart("ChildLeafList");
        generator.writeString("Foo1");
        generator.writeString("Foo2");
        generator.writeString("Bar");
        generator.writeListEnd();
        generator.writeListFieldStart("ChildList");
        generator.writeMapStart();
        generator.writeStringField("ID", "Key1");
        generator.writeNumberField("Ratio", 345.234);
        generator.writeMapEnd();
        generator.writeListEnd();
        generator.writeMapEnd();
        generator.writeMapEnd();
        
        checkExpectedResult(generator.getResult(), "TestNestedDataNodeGenerator");
    }
    
    @Test
    public void testNullDataNodeGenerator() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode = new ContainerSchemaNode("", moduleId);
        DataNodeGenerator generator = new DataNodeGenerator(containerSchemaNode);
        generator.writeNull();
        DataNode dataNode = generator.getResult();
        assertNotNull(dataNode);
        assertTrue(dataNode.isNull());
    }

    // FIXME: Should add a few more negative tests for cases where
    // we expect an exception to be thrown
}
