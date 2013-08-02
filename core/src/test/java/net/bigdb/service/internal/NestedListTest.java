package net.bigdb.service.internal;

import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeSet;
import net.bigdb.data.ServerDataSource;
import net.bigdb.data.annotation.BigDBPath;
import net.bigdb.data.annotation.BigDBQuery;
import net.bigdb.query.Query;
import net.bigdb.schema.Schema;
import net.bigdb.service.Treespace;
import net.bigdb.test.MapperTest;

import org.junit.Test;

public class NestedListTest extends MapperTest {

    public static class InnerListElement {

        private final String id;

        public InnerListElement(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class MiddleListElement {
        private final String id;
        private final List<InnerListElement> innerList =
                new ArrayList<InnerListElement>();

        public MiddleListElement(String id, List<InnerListElement> innerList) {
            this.id = id;
            if (innerList != null)
                this.innerList.addAll(innerList);
        }

        public String getId() {
            return id;
        }

        public List<InnerListElement> getInnerList() {
            return innerList;
        }
    }

    public static class OuterListElement {
        private final String id;
        private final List<MiddleListElement> middleList =
                new ArrayList<MiddleListElement>();

        public OuterListElement(String id, List<MiddleListElement> middleList) {
            this.id = id;
            if (middleList != null)
                this.middleList.addAll(middleList);
        }

        public String getId() {
            return id;
        }

        public List<MiddleListElement> getMiddleList() {
            return middleList;
        }
    }

    static public class NestedListDataSource extends ServerDataSource {

        private final List<OuterListElement> outerList = new ArrayList<OuterListElement>();

        public NestedListDataSource(Schema schema) throws BigDBException  {
            super("nested-list", schema);
        }

        @BigDBQuery
        @BigDBPath("outer-list")
        public List<OuterListElement> getOuterList()
                throws BigDBException {
            return outerList;
        }
    }


    @Test
    public void testNestedList() throws Exception {
        ServiceImpl service = new ServiceImpl();
        service.initializeFromResource(
                "/net/bigdb/service/internal/NestedListTest.yaml");
        Treespace treespace = service.getTreespace("NestedListTest");
        NestedListDataSource dataSource = new NestedListDataSource(treespace.getSchema());
        dataSource.setTreespace(treespace);
        Query query = Query.parse("/outer-list");
        InnerListElement innerListElement = new InnerListElement("abc");
        MiddleListElement middleListElement = new MiddleListElement("cde", null);
        middleListElement.innerList.add(innerListElement);
        OuterListElement outerListElement = new OuterListElement("ghi", null);
        outerListElement.middleList.add(middleListElement);
        dataSource.getOuterList().add(outerListElement);
        treespace.registerDataSource(dataSource);
        DataNodeSet dataNodeSet = treespace.queryData(query, null);
        checkExpectedResult(dataNodeSet, "TestNestedList");
    }
}
