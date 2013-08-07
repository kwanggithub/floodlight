package org.projectfloodlight.db.service.internal;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.ServiceImpl;
import org.projectfloodlight.db.test.MapperTest;

public class UnkeyedListTest extends MapperTest {

    public static class TestListElement {

        private final String name;
        private final long counter;

        public TestListElement(String name, long counter) {
            this.name = name;
            this.counter = counter;
        }

        public String getName() {
            return name;
        }

        public long getCounter() {
            return counter;
        }
    }

    static public class UnkeyedListDataSource extends ServerDataSource {

        private final List<TestListElement> list = new ArrayList<TestListElement>();

        public UnkeyedListDataSource(Schema schema) throws BigDBException  {
            super("unkeyed-list", schema);
        }

        @BigDBQuery
        @BigDBPath("unkeyed-list")
        public List<TestListElement> getUnkeyedList()
                throws BigDBException {
            return list;
        }
    }


    @Test
    public void testUnkeyedList() throws Exception {
        ServiceImpl service = new ServiceImpl();
        service.initializeFromResource("/org/projectfloodlight/db/service/internal/UnkeyedListTest.yaml");
        Treespace treespace = service.getTreespace("UnkeyedListTest");
        UnkeyedListDataSource dataSource = new UnkeyedListDataSource(treespace.getSchema());
        dataSource.setTreespace(treespace);
        Query query = Query.parse("/unkeyed-list");
        TestListElement element = new TestListElement("foo", 4);
        dataSource.getUnkeyedList().add(element);
        element = new TestListElement("bar", 14);
        dataSource.getUnkeyedList().add(element);
        treespace.registerDataSource(dataSource);
        DataNodeSet dataNodeSet = treespace.queryData(query, null);
        checkExpectedResult(dataNodeSet, "TestUnkeyedList");
    }
}
