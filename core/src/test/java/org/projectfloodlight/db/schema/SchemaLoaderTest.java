package org.projectfloodlight.db.schema;

import java.io.File;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.schema.ModuleIdentifier;
import org.projectfloodlight.db.schema.internal.SchemaImpl;
import org.projectfloodlight.db.test.MapperTest;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class SchemaLoaderTest extends MapperTest {
    
    protected SchemaImpl schema;
    protected static File dumpDirectory;
    protected static ObjectMapper mapper;
    
    private final File ROOT_TEST_DATA_DIRECTORY =
            new File("src/test/resources/org/projectfloodlight/db/schema/schemaLoaderTest");
    
    @Before
    public void setUp() {
        schema = new SchemaImpl();
        // Add the schema folder
        schema.getModuleLocator().addSearchPath(new File("schema"), true);
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        // Check if a dump directory has been specified
        Map<String, String> env = System.getenv();
        String dumpDirectoryName =
                env.get("BIGDB_REST_UNIT_TEST_DUMP_DIRECTORY");
        if (dumpDirectoryName != null && !dumpDirectoryName.isEmpty()) {
            dumpDirectory = new File(dumpDirectoryName);
            dumpDirectory.mkdirs();
        }
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
    }
    
    private void addSearchPath(String path, boolean recursive) {
        File searchPath = new File(ROOT_TEST_DATA_DIRECTORY, path);
        schema.getModuleLocator().addSearchPath(searchPath, recursive);
    }
    
    private void loadModule(String name, String revision,
            String directoryPath, boolean recursive)
            throws BigDBException {
        ModuleIdentifier moduleId = new ModuleIdentifier(name, revision);
        File directory = (directoryPath != null) ?
                new File(ROOT_TEST_DATA_DIRECTORY, directoryPath) : null;
        schema.loadModule(moduleId, directory, recursive);
    }
    
    private void loadModule(String name, String revision)
            throws BigDBException {
        loadModule(name, revision, null, false);
    }
    
    private void loadModule(String name)
            throws BigDBException {
        loadModule(name, null, null, false);
    }

    private void checkExpectedOutput(String testName) throws Exception
    {
        schema.finishLoading();
        checkExpectedResult(schema, testName);
    }
    
    @Test
    public void testSimple() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("Simple");
        checkExpectedOutput("Simple");
    }
    
    @Test
    public void testSimpleWithExplicitDirectory() throws Exception {
        loadModule("Simple", null, "test-schemas", false);
        checkExpectedOutput("Simple");
    }

    @Test
    public void testImportedModule() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("ImportedModule");
        checkExpectedOutput("ImportedModule");
    }
    
    @Test
    public void testNestedImportedModule() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("NestedImportedModule");
        checkExpectedOutput("NestedImportedModule");
    }
    
    @Test
    public void testSimpleRevision() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("Simple", "2012-08-01");
        checkExpectedOutput("SimpleRevision");
    }
    
    @Test
    public void testController() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("Controller");
        checkExpectedOutput("TestController");
    }
    
    @Test
    public void testTypedefResolution() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("TypedefResolution");
        checkExpectedOutput("TestTypedefResolution");
    }
    
    @Test
    public void testGrouping() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("GroupingTestModule");
        checkExpectedOutput("GroupingTestModule");
    }

    @Test
    public void testStringRestrictions() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("StringRestrictions");
        checkExpectedOutput("StringRestrictions");
    }
    
    @Test
    public void testExtensions() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("ExtensionTest");
        checkExpectedOutput("ExtensionTest");
    }    

    @Test
    public void testIntegerRestriction() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("IntegerRestrictionTest");
        checkExpectedOutput("IntegerRestrictionTest");
    }    

    @Test
    public void testUnionTypes() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("UnionTypeTest");
        checkExpectedOutput("UnionTypeTest");
    }
    
    @Test
    public void testIetfInetTypes() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("IetfInetTypesTest");
        checkExpectedOutput("IetfInetTypesTest");
    }
    
    @Test
    public void testIetfYangTypes() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("IetfYangTypesTest");
        checkExpectedOutput("IetfYangTypesTest");
    }
    @Test
    public void testMandatory() throws Exception {
        addSearchPath("test-schemas", true);
        loadModule("MandatoryTest");
        checkExpectedOutput("MandatoryTest");
    }

//    @Test
//    public void testCounterSchema() throws Exception {
//        addSearchPath("test-schemas", true);
//        loadModule("CounterSchemaTest");
//        checkExpectedOutput("CounterSchemaTest");
//    }
}
