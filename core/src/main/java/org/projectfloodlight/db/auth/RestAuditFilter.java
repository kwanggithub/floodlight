package org.projectfloodlight.db.auth;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.session.Session;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.BigDBRestApplication;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Reference;
import org.restlet.routing.Filter;

/** Restlet filter to record requests into an accounting log
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class RestAuditFilter extends Filter {
    private AuthService authService;

    public RestAuditFilter(Context context) {
        super(context);
        authService = getAuthService();
    }

    public RestAuditFilter(Context context, Restlet next) {
        super(context, next);
        authService = getAuthService();
    }

    public RestAuditFilter(AuthService authService, Context context, Restlet next) {
        super(context, next);
        this.authService = authService;
    }

    /** retrieve the authService handle that was put there by BigDBRestApplication */
    private AuthService getAuthService() {
        if (authService != null)
            return authService;
        Object o = getContext().getAttributes().get(BigDBRestApplication.BIGDB_AUTH_SERVICE);
        if (o == null)
            return null;
        authService = (AuthService) o;
        return authService;
    }

    /** retrieve a session object for this REST request.
     *
     * Not all REST requests are authenticated (associated with a session cookie).
     *
     * @param request
     * @return a valid Session object, or None if the request is not authenticated
     */
    private Session getSessionForRequest(Request request) {

        // auth service may not be initialized yet
        AuthService authService = getAuthService();
        if (authService == null)
           return null;

        // unauthenticated request, no cookie
        String cookie = request.getCookies().getFirstValue("session_cookie");
        if (cookie == null)
            return null;

        // session was deleted in-flight
        Session session = authService.getSessionManager().getSessionForCookie(cookie);
        if (session == null)
            return null;

        return session;
    }

    /** Try to not double-account requests that come from external sources...
     *
     * @param request
     * @return
     */
    private boolean isIgnored(String saneUri, Request request) {

        if (!saneUri.endsWith("/core/aaa/audit-event"))
            return false;

        // XXX roth -- also maybe validate the json data here

        return true;
    }

    @Override
    protected int doHandle(Request request, Response response) {

        Session session = getSessionForRequest(request);
        AuditEvent.Builder builder;
        if (session == null)
            builder = new AuditEvent.Builder();
        else
            builder = session.eventBuilder();
        builder.type("RestAuditFilter.request");
        Reference ref = request.getOriginalRef();

        String saneUri;
        try {
            Query query = Query.parse(ref.getPath());
            saneUri = String.format("%s/%s",
                                    ref.getHostIdentifier(),
                                    query.getSimpleBasePath().toString());
        } catch (BigDBException e) {
            saneUri = String.format("%s%s", ref.getHostIdentifier(), ref.getPath());
        }
        builder.avPair("uri", saneUri);

        boolean ignore = isIgnored(saneUri, request);

        int code = super.doHandle(request, response);

        builder.avPair("code", String.format("%d", response.getStatus().getCode()));

        if (ignore)
            return code;

        builder.build().commit();
        return code;
    }
}
