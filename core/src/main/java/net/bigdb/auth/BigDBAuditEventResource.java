package net.bigdb.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigdb.BigDBException;
import net.bigdb.auth.application.ApplicationContext;
import net.bigdb.auth.application.ApplicationRegistry;
import net.bigdb.auth.session.Session;
import net.bigdb.data.DataNode;
import net.bigdb.data.annotation.BigDBInsert;
import net.bigdb.data.annotation.BigDBParam;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;

/** operational state for inserting audit events
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class BigDBAuditEventResource {

    protected final static Logger logger =
            LoggerFactory.getLogger(BigDBAuditEventResource.class);

    public static class AuthHook implements AuthorizationHook {

        private final ApplicationRegistry applicationAuthenticator;

        public AuthHook(ApplicationRegistry applicationAuthenticator) {
            this.applicationAuthenticator = applicationAuthenticator;
        }

        @Override
        public Result authorize(Context context) throws BigDBException {
            if (applicationAuthenticator.getApplication(context.getAuthContext().getRequest()) != null)
                return Result.ACCEPT;

            // session cookie is not enough to generate an application accounting record
            return Result.REJECT;
        }
    }

    private void doAudit(LocationPathExpression lpe, DataNode data, AuthContext authContext)
            throws BigDBException {

        if (data == null) {
            logger.warn("null data in POST");
            return;
        }

        DataNode node;
        AuditEvent.Builder builder;

        Session session = authContext.getSessionData();
        ApplicationContext app = authContext.getApplicationData();

        if (session != null) {
            builder = session.eventBuilder();
        } else if (app != null) {
            builder = app.eventBuilder();
        } else {
            logger.warn("no session info for event");
            builder = new AuditEvent.Builder();
        }

        node = data.getChild("event-type");
        if (node.isNull())
            builder.type("BigDBAuditEventResource");
        else
            builder.type(node.getString());

        for (DataNode n : data.getChild("attributes")) {
            DataNode k = n.getChild("attribute-key");
            DataNode v = n.getChild("attribute-value");
            if (!(k.isNull()) && !(v.isNull()))
                builder.avPair(k.getString(), v.getString());
            else if (!(k.isNull()))
                builder.avPair(k.getString(), "NULL");
            else
                logger.warn("audit event has NULL/missing attribute-key");
        }

        builder.build().commit();
    }

    @BigDBInsert
    public void createAuditEvent(@BigDBParam("location-path") LocationPathExpression lpe,
                                 @BigDBParam("mutation-data") DataNode data,
                                 @BigDBParam("auth-context") AuthContext authContext)
                                         throws BigDBException {
        for (DataNode updateNode : data) {
            doAudit(lpe, updateNode, authContext);
        }
    }
}
