package org.projectfloodlight.db.service.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataSource;
import org.projectfloodlight.db.data.DiffContainerDataNode;
import org.projectfloodlight.db.data.DiffKeyedListDataNode;
import org.projectfloodlight.db.data.IndexSpecifier;
import org.projectfloodlight.db.data.IndexValue;
import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.data.DataNode.DictionaryEntry;
import org.projectfloodlight.db.data.DataNode.KeyedListEntry;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.parser.XPathParserUtils;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.schema.SchemaNodeNotFoundException;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.ServiceImpl;
import org.projectfloodlight.db.service.internal.TreespaceImpl;
import org.projectfloodlight.db.test.MapperTest;

public class DiffDataNodeTest extends MapperTest {

    private ServiceImpl service;
    private TreespaceImpl treespace;
    private SchemaNode rootSchemaNode;
    
    private SchemaNode containerTestSchemaNode;
    private DataNode container1;
    private DataNode container2;
    private DataNode container3;

    private SchemaNode listTestSchemaNode;
    private DataNode list1;
    private DataNode list2;
    private DataNode list3;
    private DataNode list4;

    private static final String CONTAINER_TEST_NAME = "container-test";
    private static final String LIST_TEST_NAME = "list-test";

    @Before
    public void initializeService() throws BigDBException {
        service = new ServiceImpl();
        service.setAuthConfig(new AuthConfig().setParam(
                AuthConfig.AUTH_ENABLED, false));
        service.initializeFromResource("/org/projectfloodlight/db/service/internal/DiffDataNodeTest.yaml");
        treespace = (TreespaceImpl) service.getTreespace("DiffDataNodeTest");
        LocationPathExpression rootPath =
                XPathParserUtils.parseSingleLocationPathExpression("/", null);
        rootSchemaNode = treespace.getSchema().getSchemaNode(rootPath);
    }

    private void initializeContainerTest() throws Exception {

        containerTestSchemaNode = rootSchemaNode.getChildSchemaNode(CONTAINER_TEST_NAME);

        Query query = Query.parse("/" + CONTAINER_TEST_NAME);
        replaceData(query, "{\"child1\": 5, \"child3\": [\"foo\"], " +
                "\"child4\": {\"child4.2\": 100}}");
        container1 = getDataNode(query);

        // Insert child2 and modify child4.2
        updateData(query, "{\"child2\": 222, \"child4\": {\"child4.2\": 500}}");
        container2 = getDataNode(query);

        // Modify child1, delete child3, insert child4.1
        updateData(query, "{\"child1\": 10, \"child3\": null, \"child4\": {\"child4.1\": \"test\"}}");
        container3 = getDataNode(query);
    }

    private void initializeListTest() throws Exception {

        listTestSchemaNode = rootSchemaNode.getChildSchemaNode(LIST_TEST_NAME);

        Query query = Query.parse("/" + LIST_TEST_NAME);
        replaceData(query, "[" +
                "{\"child1\": \"foo\", \"child2\": 1, \"child3\": \"test1\"}, " +
                "{\"child1\": \"bar\", \"child2\": 20, \"child3\": \"abc\", \"child4\": {\"child4.1\": 25, \"child4.2\": \"boo\"}}" +
                "]");
        DataNode rootDataNode = getDataNode(Query.ROOT_QUERY);
        list1 = rootDataNode.getChild(LIST_TEST_NAME);

        // Insert a list element
        insertData(query, "{\"child1\": \"test\", \"child2\": 100, \"child3\": \"abcd\"}");
        rootDataNode = getDataNode(Query.ROOT_QUERY);
        list2 = rootDataNode.getChild(LIST_TEST_NAME);

        // Delete a list element
        Query deleteQuery = Query.parse("/" + LIST_TEST_NAME + "[child1=\"bar\"][child2=20]");
        deleteData(deleteQuery);
        rootDataNode = getDataNode(Query.ROOT_QUERY);
        list3 = rootDataNode.getChild(LIST_TEST_NAME);

        // Modify a list element
        Query updateQuery = Query.parse("/" + LIST_TEST_NAME + "[child1=\"foo\"][child2=1]");
        updateData(updateQuery, "{\"child3\": \"xyz\"}");
        rootDataNode = getDataNode(Query.ROOT_QUERY);
        list4 = rootDataNode.getChild(LIST_TEST_NAME);
    }

    private void insertData(Query query, String data) throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.insertData(query, Treespace.DataFormat.JSON, inputStream, null);
    }

    private void replaceData(Query query, String data) throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, inputStream, null);
    }

    private void updateData(Query query, String data) throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.updateData(query, Treespace.DataFormat.JSON, inputStream, null);
    }

    private void deleteData(Query query) throws BigDBException {
        treespace.deleteData(query, null);
    }

    private DataNode getDataNode(Query query) throws BigDBException {
        // FIXME: This code is kludgy. It will be improved once data nodes are
        // immutable and we have mutation operation directly on data nodes.
        // But for now we access the state directly from the config data source.
        DataSource configDataSource = treespace.dataSources.values().iterator().next();
        Iterable<DataNodeWithPath> results =
                configDataSource.queryData(query, AuthContext.SYSTEM);
        return results.iterator().next().getDataNode();
    }

    @Test
    public void containerAttributes() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container2, container3);

        // Check data node attributes
        assertThat(diffDataNode.isDictionary(), is(true));
        assertThat(diffDataNode.isArray(), is(false));
        assertThat(diffDataNode.isNull(), is(false));
        assertThat(diffDataNode.isKeyedList(), is(false));
        assertThat(diffDataNode.isScalar(), is(false));
        assertThat(diffDataNode.hasChildren(), is(true));
        assertThat(diffDataNode.getNodeType(), is(DataNode.NodeType.CONTAINER));
    }

    @Test
    public void containerGetChild() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container2, container3);

        // Get the children
        assertThat(diffDataNode.childCount(), equalTo(3));
        DataNode childNode = diffDataNode.getChild("child1");
        assertThat(childNode.getLong(), equalTo(10L));
        childNode = diffDataNode.getChild("child3");
        assertThat(childNode.isNull(), is(true));
        assertThat(childNode, equalTo(DataNode.DELETED));
        childNode = diffDataNode.getChild("child4");
        assertThat(childNode.getNodeType(), equalTo(DataNode.NodeType.CONTAINER));
        assertThat(childNode.isDictionary(), is(true));
        assertThat(childNode.childCount(), equalTo(1));
        DataNode grandchildNode = childNode.getChild("child4.1");
        assertThat(grandchildNode.getString(), equalTo("test"));
    }

    @Test
    public void containerChildNames() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container2, container3);
        Set<String> childNames = diffDataNode.getChildNames();
        assertThat(childNames.size(), is(3));
        assertThat(childNames, CoreMatchers.hasItems("child1", "child3", "child4"));
    }

    @Test(expected=SchemaNodeNotFoundException.class)
    public void containerBadChildName() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container2, container3);

        // Try to get a child that doesn't exist
        diffDataNode.getChild("bad-child-name");
    }

    @Test
    public void containerDictionaryEntryIterator() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container2, container3);

        // Iterate over the dictionary/container entries
        Iterator<DictionaryEntry> entryIter = diffDataNode.getDictionaryEntries().iterator();
        assertThat(entryIter.hasNext(), is(true));
        DictionaryEntry entry = entryIter.next();
        assertThat(entry.getName(), equalTo("child1"));
        assertThat(entry.getDataNode().getLong(), equalTo(10L));
        entry = entryIter.next();
        assertThat(entry.getName(), equalTo("child3"));
        assertThat(entry.getDataNode(), equalTo(DataNode.DELETED));
        entry = entryIter.next();
        assertThat(entry.getName(), equalTo("child4"));
        assertThat(entry.getDataNode().childCount(), equalTo(1));
        entry = entry.getDataNode().getDictionaryEntries().iterator().next();
        assertThat(entry.getName(), equalTo("child4.1"));
        assertThat(entry.getDataNode().getString(), equalTo("test"));
        assertThat(entryIter.hasNext(), is(false));
        try {
            entryIter.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }
    }

    @Test
    public void containerIterator() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container2, container3);

        // Test iteration
        Iterator<DataNode> iter = diffDataNode.iterator();
        assertThat(iter.hasNext(), is(true));
        DataNode childNode = iter.next();
        assertThat(childNode.getLong(), equalTo(10L));
        childNode = iter.next();
        assertThat(childNode.isNull(), is(true));
        assertThat(childNode, equalTo(DataNode.DELETED));
        childNode = iter.next();
        assertThat(childNode.getNodeType(), equalTo(DataNode.NodeType.CONTAINER));
        assertThat(childNode.isDictionary(), is(true));
        assertThat(childNode.childCount(), equalTo(1));
        DataNode grandchildNode = childNode.getChild("child4.1");
        assertThat(grandchildNode.getString(), equalTo("test"));
        assertThat(iter.hasNext(), is(false));
        try {
            iter.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }
    }

    @Test
    public void container1() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container1, container2);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.Container1");
    }

    @Test
    public void container2() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container2, container3);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.Container2");
    }

    @Test
    public void container3() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container1, container3);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.Container3");
    }

    @Test
    public void container4() throws Exception {
        initializeContainerTest();
        DataNode diffDataNode = new DiffContainerDataNode(
                containerTestSchemaNode, container3, container1);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.Container4");
    }

    @Test
    public void listAttributes() throws Exception {

        initializeListTest();

        DataNode diffDataNode = new DiffKeyedListDataNode(listTestSchemaNode, list1, list4);
        assertThat(diffDataNode.getNodeType(), equalTo(DataNode.NodeType.LIST));

        assertThat(diffDataNode.isDictionary(), is(false));
        assertThat(diffDataNode.isArray(), is(false));
        assertThat(diffDataNode.isNull(), is(false));
        assertThat(diffDataNode.isKeyedList(), is(true));
        assertThat(diffDataNode.isScalar(), is(false));
        assertThat(diffDataNode.hasChildren(), is(true));
    }

    private IndexValue getListKeyValue(IndexSpecifier keySpecifier,
            String child1Value, long child2Value) throws BigDBException {
        IndexValue.Builder indexValueBuilder = new IndexValue.Builder(keySpecifier);
        indexValueBuilder.addValue("child1", new MemoryLeafDataNode(child1Value));
        indexValueBuilder.addValue("child2", new MemoryLeafDataNode(child2Value));
        return indexValueBuilder.getIndexValue();
    }

    @Test
    public void listGetChild() throws Exception {
        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list1, list4);

        assertThat(diffDataNode.childCount(), is(3));

        // Make sure that getting the key specifier works
        IndexSpecifier keySpecifier = diffDataNode.getKeySpecifier();
        assertThat(keySpecifier.toString(), equalTo("child1,child2:u"));

        // Create the key value for the first node
        IndexValue keyValue = getListKeyValue(keySpecifier, "bar", 20);
        DataNode childNode = diffDataNode.getChild(keyValue);
        assertThat(childNode.isNull(), is(true));
        assertThat(childNode, equalTo(DataNode.DELETED));

        keyValue = getListKeyValue(keySpecifier, "foo", 1);
        assertThat(diffDataNode.hasChild(keyValue), is(true));
        childNode = diffDataNode.getChild(keyValue);
        assertThat(childNode.getNodeType(), is(DataNode.NodeType.LIST_ELEMENT));
        assertThat(childNode.childCount(), is(1));
        DataNode grandchildNode = childNode.getChild("child3");
        assertThat(grandchildNode.getString(), equalTo("xyz"));

        keyValue = getListKeyValue(keySpecifier, "test", 100);
        childNode = diffDataNode.getChild(keyValue);
        assertThat(childNode.childCount(), is(3));
        grandchildNode = childNode.getChild("child1");
        assertThat(grandchildNode.getString(), equalTo("test"));
        grandchildNode = childNode.getChild("child2");
        assertThat(grandchildNode.getLong(), equalTo(100L));
        grandchildNode = childNode.getChild("child3");
        assertThat(grandchildNode.getString(), equalTo("abcd"));

        // Make sure that a bad key value returns NULL
        keyValue = getListKeyValue(keySpecifier, "bogus", -256);
        assertThat(diffDataNode.hasChild(keyValue), is(false));
        childNode = diffDataNode.getChild(keyValue);
        assertThat(childNode.isNull(), is(true));
    }

    @Test
    public void listKeyedListEntryIterator() throws Exception {

        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list1, list4);

        Iterator<KeyedListEntry> iter = diffDataNode.getKeyedListEntries().iterator();
        assertThat(iter.hasNext(), is(true));
        KeyedListEntry entry = iter.next();
        assertThat(entry.getKeyValue().toString(), is("bar|20"));
        assertThat(entry.getDataNode().isNull(), is(true));
        entry = iter.next();
        assertThat(entry.getKeyValue().toString(), is("foo|1"));
        assertThat(entry.getDataNode().childCount(), is(1));
        assertThat(entry.getDataNode().getChild("child3").getString(), is("xyz"));
        entry = iter.next();
        assertThat(entry.getKeyValue().toString(), is("test|100"));
        assertThat(entry.getDataNode().childCount(), is(3));
        assertThat(entry.getDataNode().getChild("child1").getString(), is("test"));
        assertThat(entry.getDataNode().getChild("child2").getLong(), is(100L));
        assertThat(entry.getDataNode().getChild("child3").getString(), is("abcd"));
        assertThat(iter.hasNext(), is(false));
        try {
            iter.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }
    }

    @Test
    public void listIterator() throws Exception {

        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list1, list4);

        // Test iterator
        Iterator<DataNode> iter = diffDataNode.iterator();
        assertThat(iter.hasNext(), is(true));
        DataNode childNode = iter.next();
        assertThat(childNode.isNull(), is(true));
        childNode = iter.next();
        assertThat(childNode.childCount(), is(1));
        assertThat(childNode.getChild("child3").getString(), is("xyz"));
        childNode = iter.next();
        assertThat(childNode.childCount(), is(3));
        assertThat(childNode.getChild("child1").getString(), is("test"));
        assertThat(childNode.getChild("child2").getLong(), is(100L));
        assertThat(childNode.getChild("child3").getString(), is("abcd"));
        assertThat(iter.hasNext(), is(false));
        try {
            iter.next();
            fail("Expected NoSuchElementException");
        }
        catch (NoSuchElementException e) {
        }
    }

    @Test
    public void list1() throws Exception {
        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list1, list2);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.List1");
    }

    @Test
    public void list2() throws Exception {
        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list2, list3);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.List2");
    }

    @Test
    public void list3() throws Exception {
        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list1, list3);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.List3");
    }

    @Test
    public void list4() throws Exception {
        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list3, list1);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.List4");
    }

    @Test
    public void list5() throws Exception {
        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list1, list4);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.List5");
    }

    @Test
    public void list6() throws Exception {
        initializeListTest();
        DataNode diffDataNode = new DiffKeyedListDataNode(
                listTestSchemaNode, list4, list2);
        checkExpectedResult(diffDataNode, "DiffDataNodeTest.List6");
    }
}
