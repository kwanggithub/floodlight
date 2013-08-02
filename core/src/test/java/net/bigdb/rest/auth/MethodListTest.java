package net.bigdb.rest.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import net.bigdb.auth.AuthContext;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.query.Query;
import net.bigdb.rest.BigDBRestAPITestBase;

import org.junit.BeforeClass;
import org.junit.Test;

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
