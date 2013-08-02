package net.bigdb.auth.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bigdb.auth.BigDBAuthTestUtils;
import net.bigdb.auth.BigDBGroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionJsonSerializationTest {
    protected static Logger logger =
            LoggerFactory.getLogger(SessionJsonSerializationTest.class);

    @SuppressWarnings("unchecked")
    @Test
    public void testSessionJsonWrite() throws IOException {
        Session session = BigDBAuthTestUtils.MOCK_SESSION;

        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(writer, session);

        String s = writer.toString();

        // validate
        Map<String, Object> m = new ObjectMapper().readValue(s, Map.class);
        assertEquals(m.get("id"), (int) session.getId());
        assertEquals(m.get("cookie"), session.getCookie());
        assertTrue(m.containsKey("cookie"));
        Map<String, Object> user = (Map<String, Object>) m.get("user");
        assertEquals(user.get("user"), session.getUser().getUser());
        assertEquals(user.get("fullName"), session.getUser().getFullName());

        assertTrue(user.containsKey("groups"));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) user.get("groups");

        Set<String> myGroupNames = new HashSet<String>();
        for(BigDBGroup g : session.getUser().getGroups())
            myGroupNames.add(g.getName());

        Set<String> readGroupNames = new HashSet<String>();
        for(Map<String, Object> groupMap: groups)
            readGroupNames.add((String) groupMap.get("name"));
        assertEquals(myGroupNames, readGroupNames);

        assertFalse(user.containsKey("admin"));
        assertFalse(user.containsKey("globalReader"));
        logger.info("result: "+writer.toString());


        assertEquals(m.get("created"), session.getCreated());
        assertEquals(m.get("lastTouched"), session.getLastTouched());
        assertEquals(m.get("lastAddress"), session.getLastAddress());
        assertEquals(m.get("touchCount"), session.getTouchCount());

    }

    @Test
    public void testSessionJsonRead() throws IOException {
        String s =
                "{\"id\":1,\"cookie\":\"ASlightlyLongAndMoreComplicatedCookie\"," +
                "\"user\":{\"user\":\"admin\",\"fullName\":\"full admin\"," +
                "\"groups\":[{\"name\":\"admin\"}, {\"name\":\"reader\"}]}," +
                "\"created\":1359485098183,\"lastTouched\":1234," +
                "\"lastAddress\":\"123.234.123.234\",\"touchCount\":1}";

            ObjectMapper mapper = new ObjectMapper();
            Session session = mapper.readValue(new StringReader(s), Session.class);
            assertEquals(session.getId(), 1);
            assertEquals(session.getCookie(), "ASlightlyLongAndMoreComplicatedCookie");
            assertTrue(session.getCreated() == 1359485098183L);
            assertTrue(session.getLastTouched() == 1234);
            assertEquals("123.234.123.234", session.getLastAddress());
            // the string above contains a string represenation of
            // the static ADMIN_USER. Will have to be updated if that changes
            assertEquals(session.getUser(), BigDBAuthTestUtils.ADMIN_USER);
    }

}
