package net.bigdb.tools.docgen;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.tools.cli.BigDBGen;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BigDBDocGenTest {

    @Rule
    public TemporaryFolder basePath = new TemporaryFolder();
    public File tempFile = null;
    public File sampleQueryFile = null; 
    
    @Before
    public void setup() {
        tempFile = new File(basePath.getRoot(), "samplequeries-test.json");
        sampleQueryFile = new File(basePath.getRoot(), "samplequeries.json");
    }
    
    @Test
    public void testSaveQueries()
            throws BigDBException {
        HashMap<String, List<SampleQuery>> queries =
                new HashMap<String, List<SampleQuery>>();
        ArrayList<SampleQuery> al = new ArrayList<SampleQuery>();
        queries.put("/core/switch", al);
        al.add(new SampleQuery("/core/switch",
                               "/core/switch[dpid=\"00:00:00:00:00:00:00:05\"]",
                               "GET",
                               "",
                               "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchId.expected"));
        al.add(new SampleQuery("/core/switch",
                               "/core/switch[dpid=\"00:00:00:00:00:00:00:05\"]/port",
                               "GET",
                               "",
                                "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchIdPorts.expected"));

        SampleQueryManager.saveSampleQueries(tempFile.getAbsolutePath(), queries);
        // read back and verify
        Map<String, List<SampleQuery>> readQueries =
                SampleQueryManager.readSampleQueries(tempFile.getAbsolutePath());
        assertEquals(readQueries.size(), 1);
        for (Map.Entry<String, List<SampleQuery>> e : readQueries.entrySet()) {
            assertNotNull(queries.get(e.getKey()));
            List<SampleQuery> sqs = queries.get(e.getKey());
            for (int i = 0; i < sqs.size(); i++) {
                assertEquals(e.getValue().get(i).getQueryContainerUri(),
                                    queries.get(e.getKey()).get(i).getQueryContainerUri());
                assertEquals(e.getValue().get(i).getQueryResponseResourceName(),
                                    queries.get(e.getKey()).get(i).getQueryResponseResourceName());
                assertEquals(e.getValue().get(i).getQueryUri(),
                                    queries.get(e.getKey()).get(i).getQueryUri());
                String response = SampleQueryManager.readResponse(
                                    e.getValue().get(i).getQueryResponseResourceName());
                assertNotNull(response);
            }
        }
    }

    private boolean checkExistence(Map<String, List<SampleQuery>> queries,
                                   String containerUri, String queryUri,
                                   String response) {
        boolean exists = false;
        assertNotNull(queries.get(containerUri));
        List<SampleQuery> sqs = queries.get(containerUri);
        for (int i = 0; i < sqs.size(); i++) {
            if (sqs.get(i).getQueryContainerUri().equals(containerUri) &&
                    sqs.get(i).getQueryResponseResourceName().equals(response) &&
                    sqs.get(i).getQueryUri().equals(queryUri)) {
                exists = true;
                break;
            }
        }
        return exists;
    }

    @Test
    public void testAddSampleQuery() throws BigDBException {
        ArrayList<SampleQuery> al = new ArrayList<SampleQuery>();
        al.add(new SampleQuery("/core/switch",
                               "/core/switch[starts-with(dpid,\"00:00:00:00:00:00:00:05\")]",
                               "GET",
                               "",
                               "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchId.expected"));
        al.add(new SampleQuery("/core/switch-1",
                               "/core/switch[starts-with(dpid,\"00:00:00:00:00:00:00:05\")]/port",
                               "GET",
                               "",
                                "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchIdPorts.expected"));
        SampleQueryManager.addSampleQuery(tempFile.getAbsolutePath(),al);
        SampleQueryManager.addSampleQuery(tempFile.getAbsolutePath(), 
                                          "/core/switch-2",
                                          "/core/switch[starts-with(dpid,\"00:00:00:00:00:00:00:06\")]/port",
                                          "GET",
                                          "",
                "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchIdPorts.expected");
        Map<String, List<SampleQuery>> queries =
                SampleQueryManager.readSampleQueries(tempFile.getAbsolutePath());
        assertEquals(queries.size(), 3);
        assertTrue(checkExistence(queries, "/core/switch",
                                "/core/switch[starts-with(dpid,\"00:00:00:00:00:00:00:05\")]",
                                "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchId.expected"));
        assertTrue(checkExistence(queries, "/core/switch-1",
                               "/core/switch[starts-with(dpid,\"00:00:00:00:00:00:00:05\")]/port",
                                "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchIdPorts.expected"));
        assertTrue(checkExistence(queries, "/core/switch-2",
                                         "/core/switch[starts-with(dpid,\"00:00:00:00:00:00:00:06\")]/port",
                                         "/net/floodlightcontroller/core/bigdb/BigDBRestTestSwitchIdPorts.expected"));

    }

    @Test
    public void testGenerateDoc() throws BigDBException {
        BigDBGen b = new BigDBGen();
        b.addModuleSchema("floodlight", "2012-10-22");
        b.addModuleSchema("aaa");
        b.addModuleSchema("topology");
        b.generateDoc(sampleQueryFile.getAbsolutePath(), 
                      basePath.getRoot().getAbsolutePath());
    }


}
