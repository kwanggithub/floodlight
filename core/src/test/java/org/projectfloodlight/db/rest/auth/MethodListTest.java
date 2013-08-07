package org.projectfloodlight.db.rest.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.BigDBRestAPITestBase;

/** exercises the method list operational state
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class MethodListTest extends BigDBRestAPITestBase {

    /* adapted from LoginResourceRestAPITest */
    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        setupBaseClass();
    }

    @Test
    public void testQueryMethods() throws Exception {
        Query query;
        query = Query.parse("/core/aaa/method");
        DataNodeSet methodsNodes = 
                getBigDBTreespace().queryData(query, AuthContext.SYSTEM);
        HashSet<String> methods = new HashSet<String>();
        for (DataNode n : methodsNodes) {
            methods.add(n.getChild("name").getString());
        }
        assertTrue(methods.contains("local"));
        assertFalse(methods.contains("other"));
    }
}
