package org.projectfloodlight.db.service.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.data.annotation.BigDBParam;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.data.annotation.BigDBUpdate;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.ServiceImpl;
import org.projectfloodlight.db.test.MapperTest;

public class EmployeeDataSourceTest extends MapperTest {

    protected ServiceImpl service;
    protected Treespace treespace;

    static public class EmployeeIMState {

        private String employeeID;
        private String status;
        private String lastMessageTime;

        public EmployeeIMState(String employeeID, String status,
                String lastMessageTime) {
            this.employeeID = employeeID;
            this.status = status;
            this.lastMessageTime = lastMessageTime;
        }

        @BigDBProperty(value="employee-id")
        public String getEmployeeID() {
            return employeeID;
        }

        @BigDBProperty(value="im-status")
        public String getStatus() {
            return status;
        }

        @BigDBProperty(value="im-last-message-time")
        public String getLastMessageTime() {
            return lastMessageTime;
        }

        @BigDBProperty(value="employee-id")
        public void setEmployeeID(String employeeID) {
            this.employeeID = employeeID;
        }

        @BigDBProperty(value="im-status")
        public void setStatus(String status) {
            this.status = status;
        }

        @BigDBProperty(value="im-last-message-time")
        public void setLastMessageTime(String lastMessageTime) {
            this.lastMessageTime = lastMessageTime;
        }
    }

    static public class EmployeeIMStateDataSource extends ServerDataSource {

        private final Map<String, EmployeeIMState> employeeIMState =
                new TreeMap<String, EmployeeIMState>();

        public EmployeeIMStateDataSource(Schema schema) throws BigDBException {
            super("EmployeeIMState", schema);
        }

        @BigDBQuery
        @BigDBPath("employee")
        public Map<String, EmployeeIMState> getEmployeeIMState()
                throws BigDBException {
            return employeeIMState;
        }

        private static String UNSPECIFIED = "<unspecified>";

        private EmployeeIMState convertDataNodeToIMState(String employeeId, DataNode dataNode)
                throws BigDBException {
            if (dataNode == null)
                return null;
            DataNode employeeIMStatusDataNode = dataNode.getChild("im-status");
            String employeeIMStatus = UNSPECIFIED;
            if (!employeeIMStatusDataNode.isNull()) {
                assert employeeIMStatusDataNode.getNodeType() == DataNode.NodeType.LEAF;
                employeeIMStatus = employeeIMStatusDataNode.getString();
            }
            DataNode employeeIMLastMessageTimeDataNode =
                    dataNode.getChild("im-last-message-time");
            String employeeIMLastMessageTime = UNSPECIFIED;
            if (!employeeIMLastMessageTimeDataNode.isNull()) {
                assert employeeIMLastMessageTimeDataNode.getNodeType() == DataNode.NodeType.LEAF;
                employeeIMLastMessageTime = employeeIMLastMessageTimeDataNode.getString();
            }
            EmployeeIMState employeeIMState = new EmployeeIMState(
                    employeeId, employeeIMStatus, employeeIMLastMessageTime);
            return employeeIMState;
        }

        @BigDBUpdate
        @BigDBPath("employee")
        public void setEmployeeIMState(
                @BigDBParam("location-path") LocationPathExpression locationPath,
                @BigDBParam("mutation-data") DataNode data)
                        throws BigDBException {
            Step employeeStep = locationPath.getStep(0);
            String employeeId = employeeStep.getExactMatchPredicateString("employee-id");
            EmployeeIMState employeeIMStateUpdate =
                    convertDataNodeToIMState(employeeId, data);
            EmployeeIMState employeeIMStateActual = employeeIMState.get(employeeId);
            if (employeeIMStateActual != null) {
                String status = employeeIMStateUpdate.getStatus();
                if (!Objects.equals(status, UNSPECIFIED))
                    employeeIMStateActual.setStatus(status);
                String lastMessageTime = employeeIMStateUpdate.getLastMessageTime();
                if (!Objects.equals(lastMessageTime, UNSPECIFIED))
                    employeeIMStateActual.setLastMessageTime(lastMessageTime);
            } else {
                employeeIMState.put(employeeIMStateUpdate.getEmployeeID(), employeeIMStateUpdate);
            }
        }

        public void addEmployeeIMState(EmployeeIMState employee) {
            employeeIMState.put(employee.getEmployeeID(), employee);
        }
    }

    @Before
    public void initializeService() throws BigDBException {
        service = new ServiceImpl();
        service.initializeFromResource(
                "/org/projectfloodlight/db/service/internal/EmployeeTest.yaml");
        treespace = service.getTreespace("SimpleTest");
        EmployeeIMStateDataSource employeeIMStateDataSource =
                new EmployeeIMStateDataSource(treespace.getSchema());
        employeeIMStateDataSource.setTreespace(treespace);
        employeeIMStateDataSource.addEmployeeIMState(
                new EmployeeIMState("1", "Offline", "2012-09-12:10:03:21"));
        employeeIMStateDataSource.addEmployeeIMState(
                new EmployeeIMState("2", "Online", "2012-09-12:10:05:21"));
        treespace.registerDataSource(employeeIMStateDataSource);
    }

    @Test
    public void queryEmployees() throws Exception {
        Query query = Query.parse("/employee");
        DataNodeSet dataNodeSet = treespace.queryData(query, null);
        checkExpectedResult(dataNodeSet, "EmployeeDataSourceTest.QueryEmployees");
    }

    @Test
    public void replaceEmployee() throws Exception {
        Query query = Query.parse("/employee");
        String replaceData = "[{" +
                "\"employee-id\":\"1\"," +
                "\"first-name\": \"Joe\"," +
                "\"last-name\": \"Jackson\"," +
                "\"email-address\": [\"joe@foo.com\", \"jackson@foo.com\"]" +
                "}]";
        InputStream input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input, null);
        query = Query.parse("/employee");
        DataNodeSet dataNodeSet = treespace.queryData(query, null);
        checkExpectedResult(dataNodeSet, "EmployeeDataSourceTest.ReplaceEmployee");
    }

    @Test
    public void updateEmployee() throws Exception {
        Query query = Query.parse("/employee");
        String replaceData = "[{" +
                "\"employee-id\":\"1\"," +
                "\"first-name\": \"Joe\"," +
                "\"last-name\": \"Jackson\"," +
                "\"email-address\": [\"joe@foo.com\", \"jackson@foo.com\"]" +
                "}]";
        InputStream input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input, null);
        String updateData = "{" +
                "\"first-name\": \"Andrew\"," +
                "\"email-address\": [\"andrew@foo.com\", \"jackson@foo.com\"]" +
                "}";
        input = new ByteArrayInputStream(updateData.getBytes("UTF-8"));
        Query updateQuery = Query.parse("/employee[employee-id=\"1\"]");
        treespace.updateData(updateQuery, Treespace.DataFormat.JSON, input, null);
        DataNodeSet dataNodeSet = treespace.queryData(query, null);
        checkExpectedResult(dataNodeSet, "EmployeeDataSourceTest.UpdateEmployee");
    }

    @Test
    public void updateEmployeeIMState() throws Exception {
        Query query = Query.parse("/employee[employee-id=\"1\"]");
        String data = "{\"im-status\":\"Active\", \"im-last-message-time\":\"2013-01-12:07:03:21\"}";
        InputStream input = new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.updateData(query, Treespace.DataFormat.JSON, input, null);
        query = Query.parse("/employee");
        DataNodeSet dataNodeSet = treespace.queryData(query, null);
        checkExpectedResult(dataNodeSet,
                "EmployeeDataSourceTest.UpdateEmployeeIMState");
    }
}
