package org.projectfloodlight.db.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeMapper;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.db.data.DataNodeSerializerRegistry;
import org.projectfloodlight.db.data.annotation.BigDBIgnore;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBSerialize;
import org.projectfloodlight.db.data.serializers.StringDataNodeSerializer;
import org.projectfloodlight.db.schema.ContainerSchemaNode;
import org.projectfloodlight.db.schema.LeafListSchemaNode;
import org.projectfloodlight.db.schema.LeafSchemaNode;
import org.projectfloodlight.db.schema.ListElementSchemaNode;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.ModuleIdentifier;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.test.MapperTest;

public class DataNodeMapperTest extends MapperTest {
    
    private void testMapper(Object object, SchemaNode schemaNode,
            String testName) throws Exception {
        DataNodeMapper dataNodeMapper = new DataNodeMapper();
        DataNode dataNode = dataNodeMapper.convertObjectToDataNode(object, schemaNode);
        checkExpectedResult(dataNode, testName);
    }
    
    @Test
    public void testLeafDataNodeMapper() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode =
                new ContainerSchemaNode("", moduleId);
        containerSchemaNode.addChildNode("Boolean",
                new LeafSchemaNode("Boolean", moduleId, SchemaNode.LeafType.BOOLEAN));
        containerSchemaNode.addChildNode("Character",
                new LeafSchemaNode("Character", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("Short",
                new LeafSchemaNode("Short", moduleId, SchemaNode.LeafType.INTEGER));
        containerSchemaNode.addChildNode("Int",
                new LeafSchemaNode("Int", moduleId, SchemaNode.LeafType.INTEGER));
        containerSchemaNode.addChildNode("Long",
                new LeafSchemaNode("Long", moduleId, SchemaNode.LeafType.INTEGER));
        containerSchemaNode.addChildNode("Float",
                new LeafSchemaNode("Float", moduleId, SchemaNode.LeafType.DECIMAL));
        containerSchemaNode.addChildNode("Double",
                new LeafSchemaNode("Double", moduleId, SchemaNode.LeafType.DECIMAL));
        containerSchemaNode.addChildNode("BigInteger",
                new LeafSchemaNode("BigInteger", moduleId, SchemaNode.LeafType.INTEGER));
        containerSchemaNode.addChildNode("BigDecimal",
                new LeafSchemaNode("BigDecimal", moduleId, SchemaNode.LeafType.DECIMAL));
        containerSchemaNode.addChildNode("String",
                new LeafSchemaNode("String", moduleId, SchemaNode.LeafType.STRING));
        
        Map<String, Object> container = new TreeMap<String, Object>();
        container.put("Boolean", Boolean.TRUE);
        container.put("Character", new Character('b'));
        container.put("Short", new Short((short)1234));
        container.put("Int", new Integer(2345));
        container.put("Long", new Long(3333));
        container.put("Float", new Float(12313.2424));
        container.put("Double", new Double(3312313.24));
        container.put("BigInteger", new BigInteger("345623423422346"));
        container.put("BigDecimal", new BigDecimal("3456234.23422346"));
        container.put("String", "Foobar");
        testMapper(container, containerSchemaNode, "TestLeafDataNodeMapper");
    }
    
    @Test
    public void testLeafListArrayDataNodeMapper() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode =
                new ContainerSchemaNode("", moduleId);
        containerSchemaNode.addChildNode("boolean",
                new LeafListSchemaNode("boolean", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.BOOLEAN)));
        containerSchemaNode.addChildNode("char",
                new LeafListSchemaNode("char", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.STRING)));
        containerSchemaNode.addChildNode("short",
                new LeafListSchemaNode("short", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.INTEGER)));
        containerSchemaNode.addChildNode("int",
                new LeafListSchemaNode("short", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.INTEGER)));
        containerSchemaNode.addChildNode("long",
                new LeafListSchemaNode("long", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.INTEGER)));
        containerSchemaNode.addChildNode("float",
                new LeafListSchemaNode("float", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.DECIMAL)));
        containerSchemaNode.addChildNode("double",
                new LeafListSchemaNode("double", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.DECIMAL)));
        containerSchemaNode.addChildNode("Integer",
                new LeafListSchemaNode("Integer", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.INTEGER)));
        containerSchemaNode.addChildNode("BigInteger",
                new LeafListSchemaNode("BigInteger", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.INTEGER)));
        containerSchemaNode.addChildNode("BigDecimal",
                new LeafListSchemaNode("BigDecimal", moduleId,
                new LeafSchemaNode("BigDecimal", moduleId, SchemaNode.LeafType.DECIMAL)));
        containerSchemaNode.addChildNode("String",
                new LeafListSchemaNode("String", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.STRING)));

        Map<String, Object> container = new TreeMap<String, Object>();
        container.put("boolean", new boolean[] { true, false });
        container.put("char", new char[] { 'a', 'b', 'c' });
        container.put("short", new short[] { (short)123, (short)234 });
        container.put("int", new int[] { 345, 2342, 2342 });
        container.put("long", new long[] { 3453L, 12341L, 5674674L });
        container.put("float", new float[] { 234234.234F, 345345 });
        container.put("double", new double[] { 345345.234234, 1.34535e-15 });
        container.put("Integer",
                new Integer[] { new Integer(345), new Integer(2342) });
        container.put("BigInteger",
                new BigInteger[] { new BigInteger("345623423422346")});
        container.put("BigDecimal",
                new BigDecimal[] { new BigDecimal("3456234.23422346")});
        container.put("String", new String[] { "Foo", "Bar" });
        testMapper(container, containerSchemaNode, "TestLeafListArrayDataNodeMapper");
    }
    
    public static class TestBean {
        
        protected String namespace;
        protected String name;
        protected String value;
        
        public TestBean(String namespace, String name, String value) {
            this.namespace = namespace;
            this.name = name;
            this.value = value;
        }
        
        public String getNamespace() {
            return namespace;
        }
        
        public String getName() {
            return name;
        }
        
        public String getValue() {
            return value;
        }
        
        @BigDBIgnore
        public int getHiddenField() {
            return 100;
        }
    }
    
    @Test
    public void testSimpleBean() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode =
                new ContainerSchemaNode("", moduleId);
        containerSchemaNode.addChildNode("namespace",
                new LeafSchemaNode("namespace", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("name",
                new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("value",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.STRING));
        TestBean bean = new TestBean("Basic", "Foo", "Dummy");
        testMapper(bean, containerSchemaNode, "TestSimpleBean");
    }
    
    @Test
    public void testSimpleBeanArray() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ListElementSchemaNode listElementSchemaNode = new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("namespace",
                new LeafSchemaNode("namespace", moduleId, SchemaNode.LeafType.STRING));
        listElementSchemaNode.addChildNode("name",
                new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        listElementSchemaNode.addChildNode("value",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.STRING));
        ListSchemaNode listSchemaNode = new ListSchemaNode("", moduleId, listElementSchemaNode);
        TestBean[] array = new TestBean[] {
            new TestBean("Basic1", "Foo1", "Dummy1"),
            new TestBean("Basic2", "Foo2", "Dummy2"),
            new TestBean("Basic3", "Foo3", "Dummy3")
        };
        testMapper(array, listSchemaNode, "TestSimpleBeanArray");
    }

    @Test
    public void testSimpleBeanList() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ListElementSchemaNode listElementSchemaNode = new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("namespace",
                new LeafSchemaNode("namespace", moduleId, SchemaNode.LeafType.STRING));
        listElementSchemaNode.addChildNode("name",
                new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        listElementSchemaNode.addChildNode("value",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.STRING));
        ListSchemaNode listSchemaNode = new ListSchemaNode("", moduleId, listElementSchemaNode);
        List<TestBean> list = new ArrayList<TestBean>();
        TestBean bean = new TestBean("Basic1", "Foo1", "Dummy1");
        list.add(bean);
        bean = new TestBean("Basic2", "Foo2", "Dummy2");
        list.add(bean);
        bean = new TestBean("Basic3", "Foo3", "Dummy3");
        list.add(bean);
        testMapper(list, listSchemaNode, "TestSimpleBeanList");
        // Also check the iterator variant
        testMapper(list.iterator(), listSchemaNode, "TestSimpleBeanList");
    }
    
    @Test
    public void testLeafListDataNodeMapper() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafListSchemaNode leafListSchemaNode =
                new LeafListSchemaNode("", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.INTEGER));
        List<Integer> list = new ArrayList<Integer>();
        list.add(new Integer(123));
        list.add(new Integer(234));
        list.add(new Integer(345));
        list.add(new Integer(4566456));
        testMapper(list, leafListSchemaNode, "TestLeafListDataNodeMapper");
    }
    
    @Test
    public void testMapDataNodeMapper() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode =
                new ContainerSchemaNode("", moduleId);
        ContainerSchemaNode beanSchemaNode =
                new ContainerSchemaNode("TestBean", moduleId);
        beanSchemaNode.addChildNode("namespace",
                new LeafSchemaNode("namespace", moduleId, SchemaNode.LeafType.STRING));
        beanSchemaNode.addChildNode("name",
                new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        beanSchemaNode.addChildNode("value",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("TestBean", beanSchemaNode);
        containerSchemaNode.addChildNode("Integer",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.INTEGER));
        LeafListSchemaNode leafListSchemaNode =
                new LeafListSchemaNode("List", moduleId,
                new LeafSchemaNode("", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("List", leafListSchemaNode);
        Map<String,Object> map = new TreeMap<String,Object>();
        map.put("TestBean", new TestBean("Basic", "Foo", "Bar"));
        map.put("Integer", new Integer(3453565));
        List<String> list = new ArrayList<String>();
        list.add("Test1");
        list.add("Test2");
        map.put("List", list);
        testMapper(map, containerSchemaNode, "TestMapDataNodeMapper");
    }
    
    public static class MoreComplexBean {
        
        public enum Answer { YES, NO, MAYBE };
        
        public TestBean testContainer;
        public List<TestBean> testList;
        public Map<String,Object> testMap;
        public Answer testAnswer;
        public long testRenamed;
        
        public TestBean getTestContainer() {
            return testContainer;
        }
        
        public List<TestBean> getTestList() {
            return testList;
        }
        
        public Map<String, Object> getTestMap() {
            return testMap;
        }
        
        public Answer getTestAnswer() {
            return testAnswer;
        }
        
        @BigDBProperty(value="CustomName")
        public long getTestRenamed() {
            return testRenamed;
        }
    }
    
    @Test
    public void testMoreComplexBean() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode =
                new ContainerSchemaNode("", moduleId);
        ContainerSchemaNode beanSchemaNode =
                new ContainerSchemaNode("testContainer", moduleId);
        beanSchemaNode.addChildNode("namespace",
                new LeafSchemaNode("namespace", moduleId, SchemaNode.LeafType.STRING));
        beanSchemaNode.addChildNode("name",
                new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        beanSchemaNode.addChildNode("value",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("testContainer", beanSchemaNode);
        ListElementSchemaNode listElementSchemaNode = new ListElementSchemaNode(moduleId);
        listElementSchemaNode.addChildNode("namespace",
                new LeafSchemaNode("namespace", moduleId, SchemaNode.LeafType.STRING));
        listElementSchemaNode.addChildNode("name",
                new LeafSchemaNode("name", moduleId, SchemaNode.LeafType.STRING));
        listElementSchemaNode.addChildNode("value",
                new LeafSchemaNode("value", moduleId, SchemaNode.LeafType.STRING));
        ListSchemaNode listSchemaNode = new ListSchemaNode("testList",
                moduleId, listElementSchemaNode);
        containerSchemaNode.addChildNode("testList", listSchemaNode);
        ContainerSchemaNode mapSchemaNode = new ContainerSchemaNode("testMap", moduleId);
        mapSchemaNode.addChildNode("int", new LeafSchemaNode("int", moduleId, SchemaNode.LeafType.INTEGER));
        mapSchemaNode.addChildNode("String", new LeafSchemaNode("String", moduleId, SchemaNode.LeafType.STRING));
        mapSchemaNode.addChildNode("Bean", beanSchemaNode.clone());
        mapSchemaNode.addChildNode("Array", new LeafListSchemaNode("Array",
                moduleId, new LeafSchemaNode("", moduleId, SchemaNode.LeafType.INTEGER)));
        containerSchemaNode.addChildNode("testMap", mapSchemaNode);
        containerSchemaNode.addChildNode("testAnswer",
                new LeafSchemaNode("testAnswer", moduleId, SchemaNode.LeafType.STRING));
        containerSchemaNode.addChildNode("CustomName",
                new LeafSchemaNode("CustomName", moduleId, SchemaNode.LeafType.INTEGER));

        MoreComplexBean bean = new MoreComplexBean();
        bean.testContainer = new TestBean("Basic", "Container", "XYZ");
        bean.testList = new ArrayList<TestBean>();
        bean.testList.add(new TestBean("Basic", "Bean1", "ABC"));
        bean.testList.add(new TestBean("Basic", "Bean2", "FGH"));
        bean.testList.add(new TestBean("Basic", "Bean2", "XYZ"));
        bean.testMap = new TreeMap<String, Object>();
        bean.testMap.put("int", 1235);
        bean.testMap.put("String", "Hello");
        bean.testMap.put("Bean", new TestBean("Basic", "Bean2", "XYZ"));
        bean.testMap.put("Array", new Long[] { 234234L, 3453453L });
        bean.testAnswer = MoreComplexBean.Answer.MAYBE;
        bean.testRenamed = 333;
        testMapper(bean, containerSchemaNode, "TestMoreComplexBean");
    }
    
    @Test
    public void testNull() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        SchemaNode leafSchemaNode = new LeafSchemaNode("namespace", moduleId,
                SchemaNode.LeafType.STRING);
        testMapper(null, leafSchemaNode, "TestNull");
    }

    public static class CustomSerializedBean implements
            DataNodeSerializer<CustomSerializedBean> {
        
        public String testString;
        public int testInt;
        
        public void serialize(CustomSerializedBean bean,
                DataNodeGenerator generator) throws BigDBException {
            String serializedString = String.format(
                    "%s:%d", testString, new Integer(testInt));
            generator.writeString(serializedString);
        }
        
        public CustomSerializedBean(String testString, int testInt) {
            this.testString = testString;
            this.testInt = testInt;
        }
    }
    
    @Test
    public void testDerivedCustomSerializer() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafSchemaNode schemaNode = new LeafSchemaNode("", moduleId,
                SchemaNode.LeafType.STRING);
        CustomSerializedBean bean = new CustomSerializedBean("foo", 10);
        testMapper(bean, schemaNode, "TestDerivedCustomSerializer");
    }

    @BigDBSerialize(using=CustomSerializedBean2.Serializer.class, args={"Test"})
    public static class CustomSerializedBean2 {

        public String testString;
        public int testInt;

        public static class Serializer implements
                DataNodeSerializer<CustomSerializedBean2> {
            
            public Serializer(String[] args) {
                assert(args[0].equals("Test"));
            }
            
            public void serialize(CustomSerializedBean2 bean,
                    DataNodeGenerator generator) throws BigDBException {
                String serializedString = String.format(
                        "%s:%d", bean.getTestString(), bean.getTestInt());
                generator.writeString(serializedString);
            }
        }
        
        public CustomSerializedBean2(String testString, int testInt) {
            this.testString = testString;
            this.testInt = testInt;
        }
        
        public String getTestString() {
            return testString;
        }
        
        public int getTestInt() {
            return testInt;
        }
    }

    @Test
    public void testAnnotatedClassCustomSerializer() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafSchemaNode schemaNode = new LeafSchemaNode("", moduleId,
                SchemaNode.LeafType.STRING);
        CustomSerializedBean2 bean = new CustomSerializedBean2("foo", 10);
        testMapper(bean, schemaNode, "TestAnnotatedClassCustomSerializer");
    }
    
    @BigDBSerialize(using=CustomSerializedBean3.Serializer.class)
    public static class CustomSerializedBean3 {
        
        public String testString;
        public int testInt;

        public static class Serializer implements
                DataNodeSerializer<CustomSerializedBean3> {
            
            public void serialize(CustomSerializedBean3 bean,
                    DataNodeGenerator generator) throws BigDBException {
                String serializedString = String.format(
                        "%s:%d", bean.testString, bean.testInt);
                generator.writeString(serializedString);
            }
        }
        
        public CustomSerializedBean3(String testString, int testInt) {
            this.testString = testString;
            this.testInt = testInt;
        }
        
        public String getTestString() {
            return testString;
        }
        
        public int getTestInt() {
            return testInt;
        }
    }

    @Test
    public void testAnnotatedClassNoArgsCustomSerializer() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        LeafSchemaNode schemaNode = new LeafSchemaNode("", moduleId,
                SchemaNode.LeafType.STRING);
        CustomSerializedBean3 bean = new CustomSerializedBean3("foo", 10);
        testMapper(bean, schemaNode, "TestAnnotatedClassNoArgsCustomSerializer");
    }
    
    public static class CustomMethodSerializedBean {
        
        protected CustomSerializedBean bean;
        
        public CustomMethodSerializedBean(CustomSerializedBean bean) {
            this.bean = bean;
        }
        
        public static class Serializer implements
                DataNodeSerializer<CustomSerializedBean> {
    
            public void serialize(CustomSerializedBean bean,
                    DataNodeGenerator generator) throws BigDBException {
                String serializedString = String.format(
                        "%s:%d", bean.testString, bean.testInt);
                generator.writeString(serializedString);
            }
        }

        @BigDBSerialize(using=CustomMethodSerializedBean.Serializer.class)
        public CustomSerializedBean getBean() {
            return bean;
        }
    }
    
    @Test
    public void testAnnotatedMethodCustomSerializer() throws Exception {
        ModuleIdentifier moduleId = new ModuleIdentifier("test");
        ContainerSchemaNode containerSchemaNode = new ContainerSchemaNode("", moduleId);
        containerSchemaNode.addChildNode("bean", new LeafSchemaNode("", moduleId,
                SchemaNode.LeafType.STRING));
        CustomMethodSerializedBean bean = new CustomMethodSerializedBean(
                new CustomSerializedBean("Foo", 10));
        testMapper(bean, containerSchemaNode, "TestAnnotatedMethodCustomSerializer");
    }

    public static class Foo {
    }

    @Test(expected=BigDBException.class)
    public void testMultipleSerializerRegistrations() throws Exception {
        DataNodeSerializerRegistry.registerDataNodeSerializer(Foo.class,
                StringDataNodeSerializer.getInstance());
        DataNodeSerializerRegistry.registerDataNodeSerializer(Foo.class,
                StringDataNodeSerializer.getInstance());
    }
}
