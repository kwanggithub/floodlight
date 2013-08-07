/**
 *    Copyright 2012, Big Switch Networks, Inc.
 *    Originally created by Kevin Wang, Bigswitch Networks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package org.projectfloodlight.db.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.test.MockFloodlightProvider;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.MockBigDBService;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.AuthService;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.DataNodeUtilities;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.BigDBRestApplication;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.tools.docgen.SampleQueryManager;
import org.projectfloodlight.db.util.StringUtils;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.InputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public abstract class BigDBRestAPITestBase {

    protected static Logger logger =
            LoggerFactory.getLogger(BigDBRestAPITestBase.class);
   
    protected static MockBigDBService dbService;
    protected static int port;
    public static String API_PATH = "/api/v1/data/controller";
    protected static String REST_SERVER = "";
    protected static String REST_URL;
    protected static AuthConfig authConfig;
    protected static FloodlightModuleContext moduleContext;
    
    protected static File dumpDirectory;
    protected static ObjectMapper mapper;

    @ClassRule
    public static TemporaryFolder basePath = new TemporaryFolder();
    
    public static File sampleQueryFile = null;

    private static MockFloodlightProvider mockFloodlightProvider;

    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper = new ObjectMapper(jsonFactory);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);

        Map<String, String> env = System.getenv();
        String dumpDirectoryName =
                env.get("BIGDB_REST_UNIT_TEST_DUMP_DIRECTORY");
        if (dumpDirectoryName != null && !dumpDirectoryName.isEmpty()) {
            dumpDirectory = new File(dumpDirectoryName);
            dumpDirectory.mkdirs();
        }
    }

    protected static MockBigDBService defaultService() throws BigDBException {
        MockBigDBService dbService = new MockBigDBService();
        dbService.addModuleSchema("floodlight", "2012-10-22");
        dbService.addModuleSchema("aaa");
        return dbService;
    }
    
    protected static AuthConfig defaultAuthConfig() throws Exception {
        return new AuthConfig("enabled=true,resetAdminPassword=adminpw")
            .setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);
    }
    
    protected static FloodlightModuleContext defaultModuleContext() {
        FloodlightModuleContext moduleContext = new FloodlightModuleContext();
        mockFloodlightProvider = new MockFloodlightProvider();
        moduleContext.addService(IFloodlightProviderService.class, 
                                 mockFloodlightProvider);
        moduleContext.addService(IBigDBService.class, dbService);
        return moduleContext;
    }

    public static void setupBaseClass() throws Exception {
        port = 11000;
        BigDBRestApplication.setPort(port);
        REST_SERVER = "http://localhost:" + Integer.toString(port);
        REST_URL = REST_SERVER + API_PATH;

        sampleQueryFile = new File(basePath.getRoot(), "samplequeries.json");
        
        if (authConfig == null)
            authConfig = defaultAuthConfig();
        
        dbService.setAuthConfig(authConfig);

        if (moduleContext == null) 
            moduleContext = defaultModuleContext();
                
        dbService.init(moduleContext);
        dbService.startUp(moduleContext);
        dbService.run();
    }
  
    @After
    public void teardownBase() throws Exception {
        AuthService as = dbService.getService().getAuthService();
        if (as != null)
            as.getSessionManager().purgeAllSessions();
    }
    
    @AfterClass
    public static void teardownBaseClass() throws Exception {
        if (dbService != null)
            dbService.stop();
        moduleContext = null;
        authConfig = null;
        port = 0;
        REST_SERVER = "";
        REST_URL = null;
    }

    public static Treespace getBigDBTreespace() throws Exception {
        return dbService.getService().getTreespace("controller");
    }

    public static MockFloodlightProvider getMockFloodlightProvider() {
        return mockFloodlightProvider;
    }
    
    protected String getResourcePackage(String nameExpected)
            throws ClassNotFoundException {
        StackTraceElement e[] = Thread.currentThread().getStackTrace();
        int i = 0;
        for (i = 0; i < e.length; i++) {
            if (e[i].getMethodName().startsWith("invoke")) {
                assertTrue(i > 0);
                break;
            }
        }
        i--;
        while (i >= 0) {
            URL u = Class.forName(e[i].getClassName()).getResource(nameExpected);
            if (u != null) {
                return Class.forName(e[i].getClassName()).getPackage().getName();
            }
            --i;
        }
        return null;
    }

    protected InputStream getExpectedInputStream(String nameExpected)
            throws ClassNotFoundException {
        StackTraceElement e[] = Thread.currentThread().getStackTrace();
        int i = 0;
        for (i = 0; i < e.length; i++) {
            if (e[i].getMethodName().startsWith("invoke")) {
                assertTrue(i > 0);
                break;
            }
        }
        i--;
        while (i >= 0) {
            InputStream expectedInputStream =
                    Class.forName(e[i].getClassName()).
                        getResourceAsStream(nameExpected);
            if (expectedInputStream != null) {
                return expectedInputStream;
            }
            --i;
        }
        return null;
    }

    /**
     * Comparing the whole content as a string may not be the ideal solution.
     * Better solution will be de-serializing
     * the json and look at more detailed results.
     * The result content in a resource file is still useful, e.g., for
     * documentation purpose.
     *
     * @param testName
     * @param client
     * @throws Exception
     */
    protected void checkExpectedOutput(String testName, String actualText, boolean toThrow)
            throws Exception {

        String nameExpected = testName + ".expected";
        if (dumpDirectory != null) {
            File outputFile = new File(dumpDirectory, nameExpected);
            FileWriter outputWriter = null;
            try {
                outputWriter = new FileWriter(outputFile);
                outputWriter.append(actualText);
            }
            finally {
                if (outputWriter != null)
                    outputWriter.close();
            }
        }
        if (!toThrow) return;
        InputStream expectedInputStream =
                this.getExpectedInputStream(nameExpected);
        if (expectedInputStream == null) {
            throw new Exception("BigDB REST test; " +
                    "missing expected result file: \"" + nameExpected + "\"");
        }
        String expectedText =
                StringUtils.readStringFromInputStream(expectedInputStream);

        if (!expectedText.equals(actualText)) {
            int chr = expectedText.charAt(expectedText.length() - 1);
            while ((chr == 10) && (expectedText.length() > 1))  {
                expectedText = expectedText.substring(0, expectedText.length() - 1);
                chr = expectedText.charAt(expectedText.length() - 1);
            }
            if (!expectedText.equals(actualText)) {
                logger.error("gotta error: got (from controller) " + testName);
                logger.error(actualText);
                logger.error("gotta error last:  " + chr);
                logger.error("gotta error: want " + testName);
                logger.error(expectedText);
            }
        }

        // We create two JsonNode objects here and compare them.
        // This makes it check that the actual JSON is equivalent
        // instead of just the strings being exactly the same.
        JsonNode eText = mapper.readTree(expectedText);
        JsonNode aText = mapper.readTree(actualText);
        assertEquals(mapper.writeValueAsString(eText), 
                     mapper.writeValueAsString(aText));
    }

    /**
     * Comparing the whole content as a string may not be the ideal solution.
     * Better solution will be de-serializing
     * the json and look at more detailed results.
     * The result content in a resource file is still useful, e.g., for
     * documentation purpose.
     *
     * @param testName
     * @param client
     * @throws Exception
     */
    protected void checkExpectedOutput(String testName, ClientResource client)
            throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();

        Representation representation = client.get();
        if(representation == null)
            fail("Test "+testName + " - client "+client + " did not return representation");
        representation.write(bo);

        String actualText = new String(bo.toByteArray(), Charset.defaultCharset());

        String nameExpected = testName + ".expected";
        if (dumpDirectory != null) {
            File outputFile = new File(dumpDirectory, nameExpected);
            FileWriter outputWriter = null;
            try {
                outputWriter = new FileWriter(outputFile);
                outputWriter.append(actualText);
            }
            finally {
                if (outputWriter != null)
                    outputWriter.close();
            }
        }

        InputStream expectedInputStream =
                this.getExpectedInputStream(nameExpected);
        if (expectedInputStream == null) {
            throw new Exception("BigDB REST test; " +
                    "missing expected result file: \"" + nameExpected + "\"");
        }
        String expectedText =
                StringUtils.readStringFromInputStream(expectedInputStream);

        if (!expectedText.equals(actualText)) {
            int chr = expectedText.charAt(expectedText.length() - 1);
            while ((chr == 10) && (expectedText.length() > 1))  {
                expectedText = expectedText.substring(0, expectedText.length() - 1);
                chr = expectedText.charAt(expectedText.length() - 1);
            }
            if (!expectedText.equals(actualText)) {
                logger.error("gotta error: got (from controller) " + testName);
                logger.error(actualText);
                logger.error("gotta error last:  " + chr);
                logger.error("gotta error: want " + testName);
                logger.error(expectedText);
            }
        }

        // We create two JsonNode objects here and compare them.
        // This makes it check that the actual JSON is equivalent
        // instead of just the strings being exactly the same.
        JsonNode eText = mapper.readTree(expectedText);
        JsonNode aText = mapper.readTree(actualText);
        assertEquals(eText, aText);
    }
    public static String removeURLParts(String uri, String toBeRemoved) {
        return uri.replace(REST_URL, "").
                replace("/" + toBeRemoved, "");
    }
    public static String removeURLParts(String uri, String r1, String r2) {
        return uri.replace("/" + r1, "").replace("/" + r2, "");
    }

    public void test(String testName, String uri) throws Exception {
        test(testName, uri, null);
    }
    public static <T> void testPostWithoutHttp(JacksonRepresentation<T> object,
            String uri) throws Exception {
        uri = uri.replace("/api/v1/data/controller", "");
        Query query = Query.parse(uri);
        Treespace treespace = getBigDBTreespace();
        assertNotNull(treespace);
        treespace.insertData(query, Treespace.DataFormat.JSON, object
                .getStream(), AuthContext.SYSTEM);
    }
    public static <T> void testPutWithoutHttp(JacksonRepresentation<T> object,
                            String uri)
            throws Exception {
        uri = uri.replace("/api/v1/data/controller", "");
        Query query = Query.parse(uri);
        Treespace treespace = getBigDBTreespace();
        assertNotNull(treespace);
        treespace.replaceData(query, Treespace.DataFormat.JSON,
                              object.getStream(), AuthContext.SYSTEM);
    }
    public <T> void testPut(JacksonRepresentation<T> object,
                            String uri)
            throws Exception {
        ClientResource client = new ClientResource(uri);
        try {
            client.put(object, MediaType.APPLICATION_JSON);
            Status s = client.getStatus();
            assertEquals(Status.SUCCESS_OK, s);
        } finally {
            client.release();
        }
    }
    public static void testDeleteWithoutHttp(String uri) throws Exception {
        uri = uri.replace("/api/v1/data/controller", "");
        Query query = Query.parse(uri);
        Treespace treespace = getBigDBTreespace();
        assertNotNull(treespace);
        treespace.deleteData(query, AuthContext.SYSTEM);
    }
    public static void testDelete(String uri) throws Exception {
        ClientResource client = new ClientResource(uri);
        try {
            client.delete();
            Status s = client.getStatus();
            assertEquals(Status.SUCCESS_OK, s);
        } finally {
            client.release();
        }
    }
    /**
     * utility function for derived class to call to initiate the test.
     * One extra thing this function will do is to register the test (uri and
     * response) to a list, which is used by the doc-generator to add these
     * uris and responses to the generated document as sample query.
     *
     * @param testName  The name of the test, the expected file name is from this.
     *                  attention needs to be paid to the cases of the string since
     *                  some of the build system is case-sensitive for file names.
     * @param uri       the URI to do the query.
     * @param containerUri   The location where the sample query should be shown in the
     *                       generated document. e.g., /core/switch for URI
     *                       /core/switch[dpid = "00:00:00:00:00:00:00:03"].
     *                       A null value means this test will not be shown as a sample query.
     * @throws Exception
     */
    /*
    protected void testPutWithoutHttp(String testName, String uri, String containerUri)
            throws Exception {
        String inputResourceName = testName + ".input";
        InputStream is = getExpectedInputStream(inputResourceName);
        uri = uri.replace("/api/v1/data/controller", "");
        Query query = Query.parse(uri);
        Treespace treespace = floodlightService.getbigDBTreespace();
        assertNotNull(treespace);
        treespace.replaceData(query, Treespace.DataFormat.JSON,
                              is, null);

        this.addSampleQuery(containerUri, uri, inputResourceName);
    }
*/
    /**
     * utility function for derived class to call to initiate the test.
     * One extra thing this function will do is to register the test (uri and
     * response) to a list, which is used by the doc-generator to add these
     * uris and responses to the generated document as sample query.
     *
     * @param testName  The name of the test, the expected file name is from this.
     *                  attention needs to be paid to the cases of the string since
     *                  some of the build system is case-sensitive for file names.
     * @param uri       the URI to do the query.
     * @param containerUri   The location where the sample query should be shown in the
     *                       generated document. e.g., /core/switch for URI
     *                       /core/switch[dpid = "00:00:00:00:00:00:00:03"].
     *                       A null value means this test will not be shown as a sample query.
     * @throws Exception
     */
    protected void testPut(String testName, String uri, String containerUri)
            throws Exception {
        String inputResourceName = testName + ".input";
        InputStream is = getExpectedInputStream(inputResourceName);
        InputRepresentation content = new InputRepresentation(is);
        ClientResource client = new ClientResource(uri);
        try {
            client.put(content, MediaType.APPLICATION_JSON);
            Status s = client.getStatus();
            assertEquals(Status.SUCCESS_OK, s);
        } finally {
            client.release();
        }

        this.addSampleQuery(containerUri, uri, inputResourceName);
    }

    /**
     * utility function for derived class to call to initiate the test.
     * One extra thing this function will do is to register the test (uri and
     * response) to a list, which is used by the doc-generator to add these
     * uris and responses to the generated document as sample query.
     *
     * @param testName  The name of the test, the expected file name is from this.
     *                  attention needs to be paid to the cases of the string since
     *                  some of the build system is case-sensitive for file names.
     * @param uri       the URI to do the query.
     * @param containerUri   The location where the sample query should be shown in the
     *                       generated document. e.g., /core/switch for URI
     *                       /core/switch[dpid = "00:00:00:00:00:00:00:03"].
     *                       A null value means this test will not be shown as a sample query.
     * @throws Exception
     */
    public void test(String testName, String uri, String containerUri)
            throws Exception {
        //String encodedUri = URLEncoder.encode(uri, "UTF-8");
        
        uri = uri.replace(REST_URL, "");
        Query query = Query.parse(uri);
        Treespace treespace = getBigDBTreespace();
        Schema schema = treespace.getSchema();
        assertNotNull(treespace);
        boolean isListResult =
                DataNodeUtilities.pathMatchesMultipleDataNodes(query
                        .getBasePath(), schema
                        .getSchemaNode(LocationPathExpression.ROOT_PATH));
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        Object resultObject = isListResult ? dataNodeSet : dataNodeSet.getSingleDataNode();
        assertNotNull(resultObject);
        String result = mapper.writeValueAsString(resultObject);
//        ClientResource client = new ClientResource(uri);
//        logger.info("Testing: uri " + uri);
        this.checkExpectedOutput(testName, result, true);
//        client.release();
        String nameExpected = testName + ".expected";
        this.addSampleQuery(containerUri, uri, nameExpected);
    }

    protected void addSampleQuery(String containerUri, String uri, String testName)
            throws ClassNotFoundException, BigDBException {
        if (containerUri != null) {
            String queryUri = Reference.decode(uri); //TODO: decode and remove prefix
            if (containerUri.startsWith(REST_URL)) {
                containerUri = containerUri.substring(REST_URL.length());
            }
            String nameExpected = testName;
            String packageName = this.getResourcePackage(nameExpected);
            String resourceName =
                    packageName == null ? nameExpected : "/" + packageName.replace('.', '/');
            resourceName += "/" + nameExpected;
            String responseResource = "";
            String inputResource = "";
            String operation = "GET";
            if (nameExpected.contains("input")) {
                inputResource = resourceName;
                operation = "PUT";
            } else {
                responseResource = resourceName;
            }
            SampleQueryManager.addSampleQuery(sampleQueryFile.getAbsolutePath(),
                                              containerUri,
                                              queryUri,
                                              operation,
                                              inputResource,
                                              responseResource);
        }
    }
}
