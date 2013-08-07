package org.projectfloodlight.db.rest;

import java.util.concurrent.ConcurrentMap;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthService;
import org.projectfloodlight.db.auth.RestAuditFilter;
import org.projectfloodlight.db.rest.auth.LoginAuthDisabledResource;
import org.projectfloodlight.db.rest.auth.LoginResource;
import org.projectfloodlight.db.service.Service;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBRestApplication extends Application {

    public static final String BIGDB_AUTH_SERVICE = "bigdb.authService";
    public static final String BIG_DB_SERVICE_ATTRIBUTE = "BigDBService";

    public final static int DEFAULT_SERVICE_PORT = 8082;
    public static final Object BIGDB_AUTH_CONFIG = "bigdb.authConfig";

    private static int port = DEFAULT_SERVICE_PORT;

    protected final static Logger logger =
            LoggerFactory.getLogger(BigDBRestApplication.class);

    public BigDBRestApplication() {
        super(new Context());
        setStatusService(new BigDBStatusService());
    }

    public static void setPort(int port) {
        BigDBRestApplication.port = port;
    }

    @Override
    public Restlet createInboundRoot() {
        ConcurrentMap<String, Object> attributes = getContext().getAttributes();
        Service bigDB = (Service) getContext().getAttributes().get(BIG_DB_SERVICE_ATTRIBUTE);

        AuthService authService = bigDB.getAuthService();
        if (authService != null ) {
            attributes.put(BIGDB_AUTH_SERVICE, authService);
        }

        Router routerV1 = new Router(getContext());
        routerV1.attach("schema/{treespace}", SchemaResource.class, Template.MODE_STARTS_WITH);
        routerV1.attach("resturi/{treespace}", RestUriResource.class, Template.MODE_STARTS_WITH);
        routerV1.attach("module/{treespace}", ModuleResource.class);
        routerV1.attach("module/{treespace}/{name}", ModuleResource.class);
        routerV1.attach("module/{treespace}/{name}/{revision}", ModuleResource.class);
        routerV1.attach("data/{treespace}", DataResource.class, Template.MODE_STARTS_WITH);

        Router authRouter = new Router(getContext());
        authRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);
        routerV1.attach("auth/", authRouter);

        routerV1.attach("auth/", new RestAuditFilter(authService, getContext(), authRouter));

        if(authService != null) {
            authRouter.attach("login", LoginResource.class, Template.MODE_STARTS_WITH);
        } else {
            authRouter.attach("login", LoginAuthDisabledResource.class);
        }

        Router rootRouter = new Router(getContext());
        rootRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);

        rootRouter.attach("/api/v1/", new RestAuditFilter(authService, getContext(), routerV1));

        return rootRouter;
    }

    public static Component run(Service service) throws BigDBException {
        try {
            BigDBRestApplication application = new BigDBRestApplication();
            application.getContext().getAttributes().put(
                    BIG_DB_SERVICE_ATTRIBUTE, service);

            final Component component = new Component();
            final Server server = new Server(Protocol.HTTP, port, component);
            component.getServers().add(server);
            component.getDefaultHost().attach(application);
            server.getContext().getParameters().add("useForwardedForHeader", "false");
            component.start();
            return component;
        }
        catch (Exception exc) {
            logger.error("Caught exception; exiting BigDB REST handler" +
                    exc.toString(), exc);
            // need to re-throw to propagate the error
            throw new BigDBException(
                        "Caught exception; exiting BigDB REST handler", exc);
        }
    }
}
