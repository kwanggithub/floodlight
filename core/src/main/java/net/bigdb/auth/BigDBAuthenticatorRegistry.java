package net.bigdb.auth;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.query.Query;
import net.bigdb.service.Service;

import org.restlet.data.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBAuthenticatorRegistry implements AuthenticatorMethod.Registry {

    private final Service service;
    private final ConcurrentMap<String, AuthenticatorMethod> methods;
    private final static Logger logger = LoggerFactory.getLogger(BigDBAuthenticatorRegistry.class);

    public BigDBAuthenticatorRegistry(Service service) {
        this.service = service;
        this.methods = new ConcurrentHashMap<String, AuthenticatorMethod>();
    }

    /** accumulate authenticator instances for chained authentication
     *
     * @param name friendly name for the authenticator,
     * should eventually be visible/configurable via the Cli
     *
     * @param method abstract authenticator method interface
     */
    public void registerAuthenticator(String name, AuthenticatorMethod method) {
        logger.debug("registering {} as {}", method.getClass(), name);
        methods.put(name, method);
    }

    /** find an authenticator by name
     *
     * @param authenticator's name, registered via {@code registerAuthenticator()}
     * @return valid authenticator instance, or null if none exists
     * @see #registerAuthenticator(String, Authenticator)
     */
    public AuthenticatorMethod getMethod(String name) {
        return methods.get(name);
    }

    /** Compare two authenticator/authorizer methods by priority
     *
     * @author Carl Roth <carl.roth@bigswitch.com>
     *
     */
    private static class CompareMethod 
        implements Comparator<DataNode>, Serializable{
        private static final long serialVersionUID = -3903554478016728937L;

        @Override
        public int compare(DataNode n1, DataNode n2) {
            try {

                long l1 = n1.getChild("priority").getLong();
                long l2 = n2.getChild("priority").getLong();
                if (l1 < l2)
                    return -1;
                if (l1 > l2)
                    return 1;

                String s1 = n1.getChild("name").getString();
                String s2 = n2.getChild("name").getString();
                return s1.compareTo(s2);
            } catch (BigDBException e) {
                return 0;
            }
        }
    }

    /** extract a sorted list of methods in the given config collection
     *
     * @param bigDBPath
     *   location of collection in the treespace
     * @return
     *   list of valid method names, filtered by presence in this collection,
     *   sorted by priority
     * @throws AuthenticationException
     */
    private Iterable<String> getMethodNames(String bigDBPath) throws AuthenticationException {
        try {
            Query query = Query.parse(bigDBPath);
            DataNodeSet dataNodeSet = this.service.getTreespace("controller")
                    .queryData(query, AuthContext.SYSTEM);

            ArrayList<String> names = new ArrayList<String>();
            ArrayList<DataNode> l = new ArrayList<DataNode>();
            for(DataNode node: dataNodeSet)
                l.add(node);

            Collections.sort(l, new CompareMethod());
            for(DataNode e : l) {
                String methodName = e.getChild("name").getString();
                names.add(methodName);
            }

            // at least one method (the local one) should be available
            if (names.isEmpty())
                names.add(LocalAuthenticator.NAME);

            return names;
        } catch (BigDBException e) {
            throw new AuthenticationException(Status.CONNECTOR_ERROR_INTERNAL,
                                              "Failed to query bigDB", e.getMessage());
        }
    }

    @Override
    public Iterable<String> getMethodNames() throws AuthenticationException {
        return methods.keySet();
    }

    @Override
    public Iterable<AuthenticatorMethod> getAuthnMethods() throws AuthenticationException {
        ArrayList<AuthenticatorMethod> l = new ArrayList<AuthenticatorMethod>();
        for (String s : getMethodNames("/core/aaa/authenticator")) {
            AuthenticatorMethod m = methods.get(s);
            // skip method names that are not in our registry
            // XXX roth -- should prevent them from going into BigDB via custom data source
            if (m == null) {
                logger.warn("method {} has no registered AuthenticatorMethod", s);
                continue;
            }
            l.add(m);
        }
        return l;
    }

    @Override
    public Iterable<AuthenticatorMethod> getAuthzMethods() throws AuthenticationException {
        ArrayList<AuthenticatorMethod> l = new ArrayList<AuthenticatorMethod>();
        for (String s : getMethodNames("/core/aaa/authorizer")) {
            AuthenticatorMethod m = methods.get(s);
            // skip method names that are not in our registry
            // XXX roth -- should prevent them from going into BigDB via custom data source
            if (m == null) {
                logger.warn("method {} has no registered AuthenticatorMethod", s);
                continue;
            }
            l.add(m);
        }
        return l;
    }

}
