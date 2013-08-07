package org.projectfloodlight.db.service.internal;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.MutationListener;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.schema.ValidationException;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.ServiceImpl;
import org.projectfloodlight.db.test.MapperTest;

import com.google.common.collect.ImmutableList;

public class BasicTest extends MapperTest {

    protected ServiceImpl service;
    protected Treespace treespace;

    @Before
    public void initializeService() throws BigDBException {
        service = new ServiceImpl();
        service.setAuthConfig(new AuthConfig().setParam(AuthConfig.AUTH_ENABLED, false));
        service.initializeFromResource(
                "/org/projectfloodlight/db/service/internal/BasicTest.yaml");
        treespace = service.getTreespace("BasicTest");
    }

    protected static class Job {
        protected String employerId;
        protected String employeeId;
        protected String title;

        public Job(String employerId, String employeeId, String title) {
            this.employerId = employerId;
            this.employeeId = employeeId;
            this.title = title;
        }

        @JsonProperty("employer-id")
        public String getEmployerId() {
            return employerId;
        }

        @JsonProperty("employee-id")
        public String getEmployeeId() {
            return employeeId;
        }

        public String getTitle() {
            return title;
        }

        @JsonProperty("employer-id")
        public void setEmployerId(String employerId) {
            this.employerId = employerId;
        }

        @JsonProperty("employee-id")
        public void setEmployeeId(String employeeId) {
            this.employeeId = employeeId;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    protected static class Address {
        protected String street;
        protected String city;
        protected String state;
        protected String zipCode;

        public Address(String street, String city, String state, String zipCode) {
            this.street = street;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
        }

        public String getStreet() {
            return street;
        }

        public String getCity() {
            return city;
        }

        public String getState() {
            return state;
        }

        @JsonProperty("zip-code")
        public String getZipCode() {
            return zipCode;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public void setState(String state) {
            this.state = state;
        }

        @JsonProperty("zip-code")
        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }
    }

    protected static class Person {
        protected String id;
        protected String firstName;
        protected String lastName;
        protected List<String> nicknames;
        protected List<Job> jobs;
        protected List<Address> addresses;

        public Person(String id, String firstName, String lastName,
                List<String> nicknames, List<Job> jobs, List<Address> addresses) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.nicknames = new ArrayList<String>();
            if (nicknames != null)
                this.nicknames.addAll(nicknames);
            this.addresses = new ArrayList<Address>();
            if (addresses != null)
                this.addresses.addAll(addresses);
            this.jobs = new ArrayList<Job>();
            if (jobs != null)
                this.jobs.addAll(jobs);
        }

        public String getId() {
            return id;
        }

        @JsonProperty("first-name")
        public String getFirstName() {
            return firstName;
        }

        @JsonProperty("last-name")
        public String getLastName() {
            return lastName;
        }

        @JsonProperty("nickname")
        public List<String> getNicknames() {
            return nicknames;
        }

        @JsonProperty("job")
        public List<Job> getJobs() {
            return jobs;
        }

        @JsonProperty("address")
        public List<Address> getAddresses() {
            return addresses;
        }

        public void setId(String id) {
            this.id = id;
        }

        @JsonProperty("first-name")
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        @JsonProperty("last-name")
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        @JsonProperty("nickname")
        public void setNicknames(List<String> nicknames) {
            this.nicknames = nicknames;
        }

        @JsonProperty("job")
        public void setJobs(List<Job> jobs) {
            this.jobs = jobs;
        }

        @JsonProperty("address")
        public void setAddresses(List<Address> addresses) {
            this.addresses = addresses;
        }
    }

    static class Group {
        protected String name;

        public Group(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
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

    private void addPersons(List<Person> persons) throws Exception {
        String personData = mapper.writeValueAsString(persons);
        Query query = Query.parse("/person");
        insertData(query, personData);
    }

    public void initPersons() throws Exception {
        List<Person> persons = new ArrayList<Person>();

        List<String> nicknames = new ArrayList<String>();
        nicknames.add("JJ");
        List<Job> jobs = new ArrayList<Job>();
        jobs.add(new Job("Acme", "1", "sales"));
        jobs.add(new Job("BBB", "123-45", "director"));
        List<Address> addresses = new ArrayList<Address>();
        addresses.add(new Address("300 Main St.", "Chicago", "IL", "33333"));
        addresses.add(new Address("3245 Wall St.", "New York", "NY", "01235"));
        Person person = new Person("123-45-2345", "John", "Johnson",
                nicknames, jobs, addresses);
        persons.add(person);

        nicknames = new ArrayList<String>();
        nicknames.add("Katie");
        nicknames.add("Kate");
        addresses = new ArrayList<Address>();
        addresses.add(new Address("1234 Evelyn Ave.", "Mountain View", "CA", "94567"));
        addresses.add(new Address("356 University Ave.", "Palo Alto", "CA", "93015"));
        person = new Person("123-45-1111", "Katherine", "Smith",
                nicknames, null, addresses);
        persons.add(person);

        nicknames = new ArrayList<String>();
        nicknames.add("Jim");
        jobs = new ArrayList<Job>();
        jobs.add(new Job("Acme", "2", "engineer"));
        jobs.add(new Job("Boo", "1234", "vp"));
        List<Address> addresses1 = new ArrayList<Address>();
        addresses1.add(new Address("300 White Ave.", "San Francisco", "CA", "94789"));
        person = new Person("333-44-5555", "James", "Jacobs",
                nicknames, jobs, addresses);
        persons.add(person);

        nicknames = new ArrayList<String>();
        nicknames.add("Jim");
        person = new Person("111-22-1234", "James", "Madison", nicknames, null, null);
        persons.add(person);

        addPersons(persons);
    }

    @Test
    public void queryList() throws Exception {
        initPersons();
        Query query = Query.parse("/person");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryList");
    }

    @Test
    public void queryListElement() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.QueryListElement");
    }

    @Test
    public void queryListElements() throws Exception {
        initPersons();
        Query query = Query.parse("/person[first-name=\"James\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryListElements");
    }

    @Test
    public void queryListElementsByLeafList() throws Exception {
        initPersons();
        Query query = Query.parse("/person[nickname=\"Jim\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryListElementsByLeafList");
    }

    @Test
    public void queryListElementsByNestedList() throws Exception {
        initPersons();
        Query query = Query.parse("/person[address/state=\"CA\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryListElementsByNestedList");
    }

    @Test
    public void queryKeyStartsWith() throws Exception {
        initPersons();
        Query query = Query.parse("/person[starts-with(id,\"1\")]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryKeyStartsWith");
    }

    @Test
    public void queryStartsWith() throws Exception {
        initPersons();
        Query query = Query.parse("/person[starts-with(first-name,\"J\")]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryStartsWith");
    }

    @Test
    public void queryNestedList() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-2345\"]/job");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryNestedList");
    }

    @Test
    public void queryCompoundKey() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-2345\"]/job[employer-id=\"Acme\"][employee-id=\"1\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.QueryCompoundKey");
    }

    @Test
    public void replaceListWithSingleElement() throws Exception {
        initPersons();
        Person person = new Person("555-66-7777", "Steve", "Abbott", null, null, null);
        String personData = mapper.writeValueAsString(person);
        Query query = Query.parse("/person");
        replaceData(query, personData);
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.ReplaceListWithSingleElement");
    }

    @Test
    public void replaceListWithList() throws Exception {
        initPersons();
        Person person1 = new Person("555-66-7777", "Steve", "Abbott", null, null, null);
        Person person2 = new Person("666-77-8888", "Ron", "Bell", null, null, null);
        List<Person> newPersons = ImmutableList.of(person1, person2);
        String personData = mapper.writeValueAsString(newPersons);
        Query query = Query.parse("/person");
        replaceData(query, personData);
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.ReplaceListWithList");
    }

    @Test
    public void replaceSingleElement() throws Exception {
        initPersons();
        Person person = new Person("123-45-2345", "Steve", "Abbott", null, null, null);
        String personData = mapper.writeValueAsString(person);
        Query query = Query.parse("/person[id=\"123-45-2345\"]");
        replaceData(query, personData);
        query = Query.parse("/person");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.ReplaceSingleElement");
    }

    @Test
    public void replaceElementWithNothing() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-2345\"]");
        replaceData(query, "[]");
        query = Query.parse("/person");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.ReplaceElementWithNothing");
    }

    @Test
    public void insertConflictingElement() throws Exception {
        initPersons();
        Person person = new Person("123-45-2345", "Steve", "Abbott", null, null, null);
        String personData = mapper.writeValueAsString(person);
        Query query = Query.parse("/person");
        try {
            insertData(query, personData);
            fail("Expected conflicting element error");
        }
        catch (BigDBException e) {
            assertEquals(BigDBException.Type.CONFLICT, e.getErrorType());
        }
    }

    @Test
    public void replaceListWithOnlyKey() throws Exception {
        List<Group> groups = new ArrayList<Group>();
        groups.add(new Group("test"));
        groups.add(new Group("foo"));
        Query query = Query.parse("/group");
        String groupData = mapper.writeValueAsString(groups);
        replaceData(query, groupData);
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.ReplaceListWithOnlyKey");
    }

    @Test
    public void createSingleListElement() throws Exception {
        Person person = new Person("555-66-7777", "Steve", "Abbott", null, null, null);
        String personData = mapper.writeValueAsString(person);
        Query query = Query.parse("/person");
        insertData(query, personData);
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.CreateSingleListElement");
    }

    @Test
    public void updateLeafList() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]/nickname");
        String nicknameData = "[\"Kat\", \"Katie\"]";
        replaceData(query, nicknameData);
        query = Query.parse("/person[id=\"123-45-1111\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.UpdateLeafList");
    }

    @Test
    public void updateLeaf() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]/first-name");
        replaceData(query, "\"Catherine\"");
        query = Query.parse("/person[id=\"123-45-1111\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.UpdateLeaf");
    }

    @Test
    public void updateNestedSingle() throws Exception {
        Query query = Query.parse("/update-test");
        replaceData(query, "[{\"child1\":\"abc\",\"child2\":{\"child21\":\"foo\",\"child22\":100},\"child3\":\"big\"}," +
                "{\"child1\":\"def\",\"child2\":{\"child21\":\"bar\",\"child22\":200},\"child3\":\"small\"}]");
        Query updateQuery = Query.parse("/update-test[child1=\"abc\"]");
        updateData(updateQuery, "{\"child2\":{\"child22\":50},\"child3\":\"medium\"}");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.UpdateNestedSingle");
    }

    @Test
    public void updateNestedMultiple() throws Exception {
        Query query = Query.parse("/update-test");
        replaceData(query, "[{\"child1\":\"abc\",\"child2\":{\"child21\":\"foo\",\"child22\":100},\"child3\":\"big\"}," +
                "{\"child1\":\"def\",\"child2\":{\"child21\":\"bar\",\"child22\":200},\"child3\":\"small\"}]");
        updateData(query, "{\"child2\":{\"child22\":50},\"child3\":\"medium\"}");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.UpdateNestedMultiple");
    }

    @Test
    public void updateMultipleFields() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]");
        List<String> nicknames = new ArrayList<String>();
        nicknames.add("Cathy");
        Person person = new Person(null, "Catherine", "Johnson",
                nicknames, null, null);
        String updateData = mapper.writeValueAsString(person);
        updateData(query, updateData);
        query = Query.parse("/person[id=\"123-45-1111\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.UpdateMultipleFields");
    }

    @Test
    public void insertCompoundKey() throws Exception {
        Query query = Query.parse("/external-group");
        String insertData = "{\"name\":\"foo\"}";
        insertData(query, insertData);
        query = Query.parse("/external-group[name=\"foo\"]/external-user");
        insertData = "[{\"company-id\":\"acme\",\"user-id\":\"coyote\"}]";
        insertData(query, insertData);
        query = Query.parse("/external-group");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.InsertCompoundKey");
    }

    @Test
    public void queryCaseInsensitive() throws Exception {
        Query query = Query.parse("/external-group");
        String insertData = "{\"name\":\"foo\", \"external-user\":[{\"company-id\":\"acme\",\"user-id\":\"CoYoTe\"}]}";
        insertData(query, insertData);
        query = Query.parse("/external-group[name=\"foo\"]/external-user[user-id=\"COYOTE\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryCaseInsensitive");
    }

    @Test
    public void deleteList() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]/address");
        treespace.deleteData(query, null);
        query = Query.parse("/person[id=\"123-45-1111\"]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.DeleteList");
    }

    /* Disable this for now, since we've disable the DataNodeNotFoundException behavior
    @Test
    public void deleteListElement() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]");
        treespace.deleteData(query, null);
        try {
            query = Query.parse("/person[id=\"123-45-1111\"]");
            treespace.queryData(query, null);
            fail("Found object that should have been deleted");
        }
        catch (BigDBException e) {
            // Expected error here
            assertTrue(e instanceof DataNodeNotFoundException);
        }
    }
    */

    @Test
    public void mutationListener() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]/address");
        class TestMutationListener implements MutationListener {
            boolean listenerInvoked = false;
            @Override
            public void dataNodesMutated(Set<Query> mutatedNodes,
                    Operation operation, AuthContext authContext)
                    throws BigDBException {
                assertThat(mutatedNodes.size(), is(2));
                Set<String> mutatedNodeStrings = new TreeSet<String>();
                for (Query query: mutatedNodes) {
                    mutatedNodeStrings.add(query.getBasePath().toString());
                }
                assertThat(mutatedNodeStrings, hasItems(
                        "/person[id=\"123-45-1111\"]/address[street=\"356 University Ave.\"][city=\"Palo Alto\"][state=\"CA\"][zip-code=\"93015\"]",
                        "/person[id=\"123-45-1111\"]/address[street=\"1234 Evelyn Ave.\"][city=\"Mountain View\"][state=\"CA\"][zip-code=\"94567\"]"));
                assertThat(operation, is(Operation.DELETE));
                assertThat(authContext, is(AuthContext.SYSTEM));
                listenerInvoked = true;
            }
            public boolean getListenerInvoked() {
                return listenerInvoked;
            }
        };
        TestMutationListener listener = new TestMutationListener();
        treespace.registerMutationListener(query, true, listener);
        treespace.deleteData(query, AuthContext.SYSTEM);
        assertThat(listener.getListenerInvoked(), is(true));
    }

    @Test
    public void deleteFieldWithNull() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]");
        updateData(query, "{\"last-name\":null}");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.DeleteFieldWithNull");
    }

    @Test
    public void updateKeyField() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]");
        try {
            updateData(query, "{\"id\":\"444-44-4444\"}");
            fail("Expected failure trying to change key of list element");
        }
        catch (BigDBException e) {
            assertThat(e.getMessage(), containsString("id"));
        }
    }

    @Test
    public void deleteLeafListWithNull() throws Exception {
        initPersons();
        Query query = Query.parse("/person[id=\"123-45-1111\"]");
        updateData(query, "{\"nickname\":null}");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result.getSingleDataNode(),
                "BasicTest.DeleteLeafListWithNull");
    }

    @Test
    public void extensionsInGrouping() throws Exception {
        Query query = Query.parse("/person/first-name");
        SchemaNode schemaNode = treespace.getSchema().getSchemaNode(query.getBasePath());
        String columnHeaderAttribute = schemaNode.getAttribute("column-header");
        assertNotNull(columnHeaderAttribute);
        assertEquals(columnHeaderAttribute, "First Name");
    }

    private void doAllowedEmptyTest(Query query) throws Exception {
        updateData(query, "\"abcd\"");
        DataNodeSet result = treespace.queryData(query, null);
        assertEquals(result.getSingleDataNode().getString(), "abcd");

        // Verify that we can set it to a valid value
        updateData(query, "\"\"");
        result = treespace.queryData(query, null);
        assertEquals(result.getSingleDataNode().getString(), "");

        // Verify that we can't set it to non-empty invalid value
        try {
            updateData(query, "\"xyz\"");
            fail();
        }
        catch (ValidationException e) {
            // Expected exception, so ignore
        }
    }

    @Test
    public void allowedEmptyLeaf() throws Exception {
        // Verify that we can set it to a valid non-empty value
        Query query = Query.parse("/allow-empty-leaf");
        doAllowedEmptyTest(query);
    }

    @Test
    public void allowedEmptyType() throws Exception {
        // Verify that we can set it to a valid non-empty value
        Query query = Query.parse("/allow-empty-type");
        doAllowedEmptyTest(query);
    }

    private void addPrioritizedListElement(int priority, String name) throws Exception {
        Query query = Query.parse("/prioritized-list");
        insertData(query, String.format("{\"priority\": %d, \"name\": \"%s\"}", priority, name));
    }

    private void deletePrioritizedListElementByPriority(int priority) throws Exception {
        String queryString = String.format("/prioritized-list[priority=%d]", priority);
        Query query = Query.parse(queryString);
        treespace.deleteData(query, null);
    }

    private void deletePrioritizedListElementByName(String name) throws Exception {
        Query query = Query.parse("/prioritized-list[name=$name]", "name", name);
        treespace.deleteData(query, null);
    }

    private void initPrioritizedList() throws Exception {
        addPrioritizedListElement(100, "foo");
        addPrioritizedListElement(200, "bar");
        addPrioritizedListElement(50, "test");
    }

    @Test
    public void queryPrioritizedList() throws Exception {
        initPrioritizedList();
        Query query = Query.parse("/prioritized-list");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryPrioritizedList");
    }

    @Test
    public void deletePrioritizedListByName() throws Exception {
        initPrioritizedList();
        deletePrioritizedListElementByName("foo");
        Query query = Query.parse("/prioritized-list");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.DeletePrioritizedListByName");
    }

    @Test
    public void deletePrioritizedListByPriority() throws Exception {
        initPrioritizedList();
        deletePrioritizedListElementByPriority(100);
        Query query = Query.parse("/prioritized-list");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.DeletePrioritizedListByPriority");
    }

    @Test
    public void leafDefaultValue() throws Exception {
        Query query = Query.parse("/default-values/leaf-default");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.LeafDefaultValue");
    }

    @Test
    public void typedefDefaultValue() throws Exception {
        Query query = Query.parse("/default-values/typedef-default");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.TypedefDefaultValue");
    }

    @Test
    public void typedefDefaultValueOverride() throws Exception {
        Query query = Query.parse("/default-values/typedef-default-override");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.TypedefDefaultValueOverride");
    }

    @Test
    public void queryEmptyContainer1() throws Exception {
        Query query = Query.parse("/empty-container");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer1");
    }

    @Test
    public void queryEmptyContainer2() throws Exception {
        Query query = Query.parse("/empty-container/child1");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer2");
    }

    @Test
    public void queryEmptyContainer3() throws Exception {
        Query query = Query.parse("/empty-container/child1/child12");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer3");
    }

    @Test
    public void queryEmptyContainer4() throws Exception {
        Query query = Query.parse("/empty-container/child2");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer4");
    }

    @Test
    public void queryEmptyContainer5() throws Exception {
        Query query = Query.parse("/empty-container/child2/child21");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer5");
    }

    @Test
    public void queryEmptyContainer6() throws Exception {
        Query query = Query.parse("/empty-container/child3");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer6");
    }

    @Test
    public void queryEmptyContainer7() throws Exception {
        Query query = Query.parse("/empty-container/child3/child32");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer7");
    }

    @Test
    public void queryEmptyContainer8() throws Exception {
        Query query = Query.parse("/empty-container/child3/child33");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer8");
    }

    @Test
    public void queryEmptyContainer9() throws Exception {
        Query query = Query.parse("/empty-container/child3");
        replaceData(query, "[{\"child31\": \"abc\"}, {\"child31\": \"def\"}]");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer9");
    }

    @Test
    public void queryEmptyContainer10() throws Exception {
        Query query = Query.parse("/empty-container/child3");
        replaceData(query, "[{\"child31\": \"abc\"}, {\"child31\": \"def\"}]");
        query = Query.parse("/empty-container/child3/child32");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer10");
    }

    @Test
    public void queryEmptyContainer11() throws Exception {
        Query query = Query.parse("/empty-container/child3");
        replaceData(query, "[{\"child31\": \"abc\"}, {\"child31\": \"def\"}]");
        query = Query.parse("/empty-container/child3/child33");
        DataNodeSet result = treespace.queryData(query, null);
        checkExpectedResult(result, "BasicTest.QueryEmptyContainer11");
    }
}
