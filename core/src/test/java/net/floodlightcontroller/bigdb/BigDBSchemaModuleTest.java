package net.floodlightcontroller.bigdb;

import net.bigdb.rest.BigDBRestAPITestBase;

import org.junit.BeforeClass;
import org.junit.Test;

public final class BigDBSchemaModuleTest extends BigDBRestAPITestBase {
    
    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        setupBaseClass();
    }
    
    @Test
    public void testSchema() throws Exception {
        // TODO: remove this test for now and will renable it later near release
        //this.test("bigDBRestTestSchema", "http://localhost:8082/api/v1/schema/controller");
    }

    @Test
    public void testModule() throws Exception {
        // TODO: remove this test for now and will renable it later near release
        // String REST_SERVER = floodlightService.getRestServerUrl();
        // String MODULE_URI = REST_SERVER + "/api/v1/module/controller";
        // this.test("bigDBRestTestModule", MODULE_URI);
    }

}
