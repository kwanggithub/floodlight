package org.projectfloodlight.db.auth;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import javax.annotation.Nullable;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.query.Query;
import org.restlet.data.Cookie;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/** HTTP Client for Bgidb. Wrapper around restlet's client resource. Supports BigDB's concept of
 * an authenticated session.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthenticatedRestClient {
    private final static Logger logger =
            LoggerFactory.getLogger(AuthenticatedRestClient.class);

    /** session cookie of the current authenticated session, or null, if no session is known */
    @Nullable
    private String sessionCookie;

    /** base URL of the server running bigdb, e.g., http://localhost:8082 */
    private final URI baseUri;

    /** base Data URL of Bigdb, i.e., baseUrl including the prefix /api/v1/data/controller */
    private final URI bigDbBaseDataUri;

    /** Construct a new client.
     * @param baseUri the base uri that the bigdb server is listening on (e.g., 'http://localhost:8082')
     */
    public AuthenticatedRestClient(URI baseUri) {
        this.baseUri = baseUri;
        this.bigDbBaseDataUri = baseUri.resolve("/api/v1/data/controller/");
    }

    /** login to BigDB using the supplied username and passwords */
    public String login(String user, String password) throws JsonParseException, JsonMappingException, IOException {
        ClientResource client = new ClientResource(baseUri.resolve("/api/v1/auth/login"));

        Representation response = client.post(ImmutableMap.of("user", user, "password", password), MediaType.APPLICATION_JSON);
        ObjectMapper mapper = new ObjectMapper();

        Map<String, String> map = mapper.readValue(response.getReader(), mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));

        sessionCookie = map.get("session_cookie");
        if(sessionCookie == null)
            throw new RuntimeException("no session cookie returned");

        client.release();
        return sessionCookie;
    }

    public Representation get(String uri) throws ResourceException, BigDBException {
        return getClient(uri).get();
    }

    public Representation post(String uri, Representation repr) throws ResourceException, BigDBException {
        return getClient(uri).post(repr);
    }

    public Representation delete(String uri) throws ResourceException, BigDBException {
        return getClient(uri).delete();
    }

    private ClientResource getClient(String uri) throws BigDBException {
        if(uri.startsWith("/"))
            uri = uri.substring(1);
        logger.debug("baseUri: {} relative: {}", baseUri, uri);
        URI relativeUri;
        // this is a hack and won't work in the generic case. Would need a true parser to distinguish between
        relativeUri = Query.parse(uri).toURI();
        ClientResource client = new ClientResource(bigDbBaseDataUri.resolve(relativeUri));
        if(!Strings.isNullOrEmpty(sessionCookie))
            client.getCookies().add(new Cookie("session_cookie", sessionCookie));
        return client;
    }
}
