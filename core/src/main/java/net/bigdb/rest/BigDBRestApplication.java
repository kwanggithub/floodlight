package net.bigdb.rest;

import java.util.concurrent.ConcurrentMap;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthService;
import net.bigdb.auth.RestAuditFilter;
import net.bigdb.rest.auth.LoginAuthDisabledResource;
import net.bigdb.rest.auth.LoginResource;
import net.bigdb.service.Service;
import net.floodlightcontroller.quantum.QuantumHandleAttachment;
import net.floodlightcontroller.quantum.QuantumHandleNetwork;
import net.floodlightcontroller.quantum.QuantumHandlePort;
import net.floodlightcontroller.quantum.QuantumHandleTenant;

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

        //routerV1.attach("typedef/{treespace}/{module}, TypedefResource.class);
        //routerV1.attach("typedef/{treespace}/{module}/{revision}", TypedefResource.class);
        //routerV1.attach("typedef/{treespace}/{module}/{revision}/{typedef}", TypedefResource.class);

        // TODO - find a general way to plug in other URL paths and then remove this.
        Router quantumRouter = new Router(getContext());
        quantumRouter.attach("tenants", QuantumHandleTenant.class);
        quantumRouter.attach("tenants/", QuantumHandleTenant.class);
        quantumRouter.attach("tenants/{tenant}", QuantumHandleTenant.class);
        quantumRouter.attach("tenants/{tenant}/networks", QuantumHandleNetwork.class);
        quantumRouter.attach("tenants/{tenant}/networks/", QuantumHandleNetwork.class);
        quantumRouter.attach("tenants/{tenant}/networks/{network}", QuantumHandleNetwork.class);
        quantumRouter.attach("tenants/{tenant}/networks/{network}/", QuantumHandleNetwork.class);
        quantumRouter.attach("tenants/{tenant}/networks/{network}/ports", QuantumHandlePort.class);
        quantumRouter.attach("tenants/{tenant}/networks/{network}/ports/", QuantumHandlePort.class);
        quantumRouter.attach("tenants/{tenant}/networks/{network}/ports/{port}", QuantumHandlePort.class);
        quantumRouter.attach("tenants/{tenant}/networks/{network}/ports/{port}/", QuantumHandlePort.class);
        quantumRouter.attach("tenants/{tenant}/networks/{network}/ports/{port}/attachment", QuantumHandleAttachment.class);

        Router authRouter = new Router(getContext());
        authRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);

        Router rootRouter = new Router(getContext());
        rootRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);

        rootRouter.attach("/api/v1/", new RestAuditFilter(authService, getContext(), routerV1));
        rootRouter.attach("/auth/", new RestAuditFilter(authService, getContext(), authRouter));
        rootRouter.attach("/networkService/v1.1/", new RestAuditFilter(authService, getContext(), quantumRouter));

        if(authService != null) {
            authRouter.attach("login", LoginResource.class, Template.MODE_STARTS_WITH);
        } else {
            authRouter.attach("login", LoginAuthDisabledResource.class);
        }

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
