package org.projectfloodlight.db.service.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.DataSource;
import org.projectfloodlight.db.data.IndexSpecifier;
import org.projectfloodlight.db.data.IndexValue;
import org.projectfloodlight.db.data.LogicalDataNodeBuilder;
import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.schema.SchemaNodeNotFoundException;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.ServiceImpl;
import org.projectfloodlight.db.service.internal.TreespaceImpl;
import org.projectfloodlight.db.test.MapperTest;

public class LogicalDataNodeTest extends MapperTest {

    protected ServiceImpl service;
    protected TreespaceImpl treespace;

    private static final String CONTAINER_TEST_NAME = "container-test";
    private static final String KEYED_LIST_TEST_NAME = "keyed-list-test";
    private static final String UNKEYED_LIST_TEST_NAME = "unkeyed-list-test";
    private static final String DEFAULT_VALUE_TEST_NAME = "default-value-test";

    @Before
    public void initializeService() throws BigDBException {
        service = new ServiceImpl();
        service.setAuthConfig(new AuthConfig().setParam(
                AuthConfig.AUTH_ENABLED, false));
        service.initializeFromResource("/org/projectfloodlight/db/service/internal/LogicalDataNodeTest.yaml");
        treespace = (TreespaceImpl) service.getTreespace("LogicalDataNodeTest");
    }

    private void replaceData(Query query, String data) throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, inputStream, null);
    }

    private DataNode makeLogicalDataNode(String childName,
            Set<String> enabledDataSources) throws BigDBException {
        SchemaNode rootSchemaNode = treespace.getSchema().getSchemaNode(LocationPathExpression.ROOT_PATH);
        LogicalDataNodeBuilder builder = new LogicalDataNodeBuilder(rootSchemaNode);
        for (DataSource dataSource: treespace.dataSources.values()) {
            if ((enabledDataSources == null) || enabledDataSources.contains(dataSource.getName())) {
                Iterable<DataNodeWithPath> results =
                        dataSource.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
                Iterator<DataNodeWithPath> iter = results.iterator();
                if (iter.hasNext()) {
                    DataNode dataNode = iter.next().getDataNode();
                    builder.addContribution(dataSource, dataNode);
                    assertFalse(iter.hasNext());
                }
            }
        }
        DataNode dataNode = builder.getDataNode();
        if (childName != null)
            dataNode = dataNode.getChild(childName);
        return dataNode;
    }

    @Test
    public void containerOperations() throws Exception {
        Query query = Query.parse("/" + CONTAINER_TEST_NAME);
        replaceData(query, "{\"child1\": 5, \"child2\": 23, \"child3\": [\"foo\"], " +
                "\"child4\": {\"child4.1\": \"test\", \"child4.2\": 100}}");
        DataNode containerTestDataNode = makeLogicalDataNode(CONTAINER_TEST_NAME, null);

        assertEquals(DataNode.NodeType.CONTAINER, containerTestDataNode.getNodeType());

        assertTrue(containerTestDataNode.isDictionary());
        assertFalse(containerTestDataNode.isArray());
        assertFalse(containerTestDataNode.isNull());
        assertFalse(containerTestDataNode.isKeyedList());
        assertFalse(containerTestDataNode.isScalar());
        assertTrue(containerTestDataNode.hasChildren());

        assertEquals(4, containerTestDataNode.childCount());
        DataNode childNode = containerTestDataNode.getChild("child1");
        assertEquals(5, childNode.getLong());
        childNode = containerTestDataNode.getChild("child3");
        DataNode elementNode = childNode.iterator().next();
        assertEquals("foo", elementNode.getString());

        childNode = containerTestDataNode.getChild("child4");
        assertTrue(childNode.isDictionary());
        DataNode grandchildNode = childNode.getChild("child4.1");
        assertEquals("test", grandchildNode.getString());

        try {
            childNode.getChild("bad-child-name");
            fail("Expected SchemaNodeNotFoundException");
        }
        catch (SchemaNodeNotFoundException e) {
        }

        Iterator<DataNode> iter = containerTestDataNode.iterator();
        assertTrue(iter.hasNext());
        assertEquals(5, iter.next().getLong());
        assertEquals(23, iter.next().getLong());
        assertEquals("foo", iter.next().iterator().next().getString());
        assertEquals(DataNode.NodeType.CONTAINER, iter.next().getNodeType());
        assertFalse(iter.hasNext());
        try {
            iter.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }

        // Test getting the child names
        Set<String> childNames = containerTestDataNode.getChildNames();
        assertEquals(4, childNames.size());
        assertTrue(childNames.contains("child1"));
        assertTrue(childNames.contains("child2"));
        assertTrue(childNames.contains("child3"));
        assertTrue(childNames.contains("child4"));

        // Test iterating over the entries
        Iterator<DataNode.DictionaryEntry> entryIterator =
                containerTestDataNode.getDictionaryEntries().iterator();
        assertTrue(entryIterator.hasNext());
        DataNode.DictionaryEntry entry = entryIterator.next();
        assertEquals("child1", entry.getName());
        assertEquals(5, entry.getDataNode().getLong());
        entry = entryIterator.next();
        assertEquals("child2", entry.getName());
        assertEquals(23, entry.getDataNode().getLong());
        entry = entryIterator.next();
        assertEquals("child3", entry.getName());
        assertEquals("foo", entry.getDataNode().iterator().next().getString());
        entry = entryIterator.next();
        assertEquals("child4", entry.getName());
        assertEquals(DataNode.NodeType.CONTAINER, entry.getDataNode().getNodeType());
        assertFalse(entryIterator.hasNext());
        try {
            entryIterator.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }
    }

    @Test
    public void queryContainer1() throws Exception {
        Query query = Query.parse("/" + CONTAINER_TEST_NAME);
        replaceData(query, "{\"child1\": 5, \"child2\": 23, \"child3\": [\"foo\"], " +
                "\"child4\": {\"child4.1\": \"test\", \"child4.2\": 100}}");
        DataNode containerTestDataNode = makeLogicalDataNode(CONTAINER_TEST_NAME, null);
        checkExpectedResult(containerTestDataNode,
                "LogicalDataNodeTest.QueryContainer1");
    }

    @Test
    public void queryContainer2() throws Exception {
        Query query = Query.parse("/" + CONTAINER_TEST_NAME);
        replaceData(query, "{\"child1\": 5, \"child3\": [\"foo\"], " +
                "\"child4\": {\"child4.1\": \"test\"}}");
        DataNode containerTestDataNode = makeLogicalDataNode(CONTAINER_TEST_NAME, null);
        checkExpectedResult(containerTestDataNode,
                "LogicalDataNodeTest.QueryContainer2");
    }

    @Test
    public void queryContainerDS1() throws Exception {
        Query query = Query.parse("/" + CONTAINER_TEST_NAME);
        replaceData(query, "{\"child1\": 5, \"child2\": 23, \"child3\": [\"foo\"], " +
                "\"child4\": {\"child4.1\": \"test\", \"child4.2\": 100}}");
        Set<String> enabledDataSources = new HashSet<String>();
        enabledDataSources.add("ds1");
        DataNode containerTestDataNode = makeLogicalDataNode(
                CONTAINER_TEST_NAME, enabledDataSources);
        checkExpectedResult(containerTestDataNode,
                "LogicalDataNodeTest.QueryContainerDS1");
    }

    @Test
    public void keyedListOperations() throws Exception {
        Query query = Query.parse("/" + KEYED_LIST_TEST_NAME);
        replaceData(query, "[" +
                "{\"child1\": \"foo\", \"child2\": 1, \"child3\": \"test1\"}, " +
                "{\"child1\": \"bar\", \"child2\": 20, \"child3\": \"abc\", \"child4\": {\"child4.1\": 25, \"child4.2\": \"boo\"}}" +
                "]");
        DataNode keyedListTestDataNode = makeLogicalDataNode(KEYED_LIST_TEST_NAME, null);

        assertEquals(DataNode.NodeType.LIST, keyedListTestDataNode.getNodeType());

        assertFalse(keyedListTestDataNode.isDictionary());
        assertFalse(keyedListTestDataNode.isArray());
        assertFalse(keyedListTestDataNode.isNull());
        assertTrue(keyedListTestDataNode.isKeyedList());
        assertFalse(keyedListTestDataNode.isScalar());
        assertTrue(keyedListTestDataNode.hasChildren());

        assertEquals(2, keyedListTestDataNode.childCount());
        IndexSpecifier keySpecifier = keyedListTestDataNode.getKeySpecifier();
        IndexValue.Builder indexValueBuilder = new IndexValue.Builder(keySpecifier);
        indexValueBuilder.addValue("child1", new MemoryLeafDataNode("bar"));
        indexValueBuilder.addValue("child2", new MemoryLeafDataNode(20L));
        IndexValue keyValue = indexValueBuilder.getIndexValue();
        DataNode childNode = keyedListTestDataNode.getChild(keyValue);
        assertEquals("bar", childNode.getChild("child1").getString());
        assertEquals(20L, childNode.getChild("child2").getLong());
        assertEquals("abc", childNode.getChild("child3").getString());
        DataNode grandchildNode = childNode.getChild("child4");
        assertEquals(25, grandchildNode.getChild("child4.1").getLong());
        assertEquals("boo", grandchildNode.getChild("child4.2").getString());

        indexValueBuilder = new IndexValue.Builder(keySpecifier);
        indexValueBuilder.addValue("child1", new MemoryLeafDataNode("bogus"));
        indexValueBuilder.addValue("child2", new MemoryLeafDataNode(1L));
        keyValue = indexValueBuilder.getIndexValue();
        childNode = keyedListTestDataNode.getChild(keyValue);
        assertTrue(childNode.isNull());

        // Test iterator
        Iterator<DataNode> iter = keyedListTestDataNode.iterator();
        assertTrue(iter.hasNext());
        childNode = iter.next();
        assertEquals("bar", childNode.getChild("child1").getString());
        assertEquals(20L, childNode.getChild("child2").getLong());
        childNode = iter.next();
        assertEquals("foo", childNode.getChild("child1").getString());
        assertEquals(1L, childNode.getChild("child2").getLong());
        assertFalse(iter.hasNext());
        try {
            iter.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }
    }

    @Test
    public void queryKeyedList1() throws Exception {
        Query query = Query.parse("/" + KEYED_LIST_TEST_NAME);
        replaceData(query, "[" +
                "{\"child1\": \"foo\", \"child2\": 1, \"child3\": \"test1\"}, " +
                "{\"child1\": \"bar\", \"child2\": 20, \"child3\": \"abc\", \"child4\": {\"child4.1\": 25, \"child4.2\": \"boo\"}}, " +
                "{\"child1\": \"foo\", \"child2\": 3, \"child4\": {\"child4.1\": 100}}, " +
                "{\"child1\": \"bar\", \"child2\": 50, \"child4\": {\"child4.2\": \"boo\"}}, " +
                "{\"child1\": \"foo\", \"child2\": 100, \"child3\": \"def\", \"child4\": {\"child4.1\": 125, \"child4.2\": \"foobar\"}} " +
                "]");
        DataNode keyedListTestDataNode = makeLogicalDataNode(KEYED_LIST_TEST_NAME, null);
        checkExpectedResult(keyedListTestDataNode,
                "LogicalDataNodeTest.QueryKeyedList1");
    }

    @Test
    public void unkeyedListOperations() throws Exception {
        Query query = Query.parse("/" + UNKEYED_LIST_TEST_NAME);
        replaceData(query, "[" +
                "{\"child1\": \"foo\"}, " +
                "{\"child1\": \"foo\", \"child2\": 100, \"child3\": {\"child3.1\": 25}}, " +
                "{\"child1\": \"bar\", \"child2\": 20, \"child3\": {\"child3.1\": 1000}}" +
                "]");
        DataNode unkeyedListTestDataNode = makeLogicalDataNode(UNKEYED_LIST_TEST_NAME, null);

        assertEquals(DataNode.NodeType.LIST, unkeyedListTestDataNode.getNodeType());

        assertFalse(unkeyedListTestDataNode.isDictionary());
        assertTrue(unkeyedListTestDataNode.isArray());
        assertFalse(unkeyedListTestDataNode.isNull());
        assertFalse(unkeyedListTestDataNode.isKeyedList());
        assertFalse(unkeyedListTestDataNode.isScalar());
        assertTrue(unkeyedListTestDataNode.hasChildren());

        assertEquals(3, unkeyedListTestDataNode.childCount());
        DataNode childNode = unkeyedListTestDataNode.getChild(0);
        assertEquals(1, childNode.childCount());
        assertEquals("foo", childNode.getChild("child1").getString());
        childNode = unkeyedListTestDataNode.getChild(1);
        assertEquals(3, childNode.childCount());
        assertEquals("foo", childNode.getChild("child1").getString());
        assertEquals(100L, childNode.getChild("child2").getLong());
        DataNode grandchildNode = childNode.getChild("child3");
        assertEquals(1, grandchildNode.childCount());
        assertEquals(25L, grandchildNode.getChild("child3.1").getLong());
        childNode = unkeyedListTestDataNode.getChild(2);
        assertEquals(3, childNode.childCount());
        assertEquals("bar", childNode.getChild("child1").getString());
        assertEquals(20L, childNode.getChild("child2").getLong());
        grandchildNode = childNode.getChild("child3");
        assertEquals(1, grandchildNode.childCount());
        assertEquals(1000L, grandchildNode.getChild("child3.1").getLong());

        // Test iterator
        Iterator<DataNode> iter = unkeyedListTestDataNode.iterator();
        assertTrue(iter.hasNext());
        childNode = iter.next();
        assertEquals(1, childNode.childCount());
        assertEquals("foo", childNode.getChild("child1").getString());
        childNode = iter.next();
        assertEquals(3, childNode.childCount());
        assertEquals("foo", childNode.getChild("child1").getString());
        assertEquals(100L, childNode.getChild("child2").getLong());
        grandchildNode = childNode.getChild("child3");
        assertEquals(1, grandchildNode.childCount());
        assertEquals(25L, grandchildNode.getChild("child3.1").getLong());
        childNode = iter.next();
        assertEquals(3, childNode.childCount());
        assertEquals("bar", childNode.getChild("child1").getString());
        assertEquals(20L, childNode.getChild("child2").getLong());
        grandchildNode = childNode.getChild("child3");
        assertEquals(1, grandchildNode.childCount());
        assertEquals(1000L, grandchildNode.getChild("child3.1").getLong());
        assertFalse(iter.hasNext());
        try {
            iter.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }
    }

    @Test
    public void unkeyedListQuery1() throws Exception {
        Query query = Query.parse("/" + UNKEYED_LIST_TEST_NAME);
        replaceData(query, "[" +
                "{\"child1\": \"foo\"}, " +
                "{\"child1\": \"foo\", \"child2\": 100, \"child3\": {\"child3.1\": 25}}, " +
                "{\"child1\": \"bar\", \"child2\": 20, \"child3\": {\"child3.1\": 1000}}" +
                "]");
        DataNode unkeyedListTestDataNode = makeLogicalDataNode(UNKEYED_LIST_TEST_NAME, null);
        checkExpectedResult(unkeyedListTestDataNode,
                "LogicalDataNodeTest.QueryUnkeyedList1");
    }

    @Test
    public void defaultValueForExcludedDataSource() throws Exception {
        Query query = Query.builder()
                .setBasePath(LocationPathExpression.parse("/" + DEFAULT_VALUE_TEST_NAME))
                .setIncludedStateType(Query.StateType.OPERATIONAL)
                .getQuery();
        replaceData(query, "{\"child2\": \"foo\"}");
        DataNodeSet dataNodeSet = treespace.queryData(query, null);
        checkExpectedResult(dataNodeSet, "LogicalDataNodeTest.DefaultValueForExcludedDataSource");
    }
}
