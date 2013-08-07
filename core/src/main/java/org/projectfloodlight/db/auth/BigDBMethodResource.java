package org.projectfloodlight.db.auth;

import java.util.ArrayList;
import java.util.Iterator;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.FloodlightResource;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBQuery;

/** Dynamic BigDBResource exposing the currently-defined Aaa method names.
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class BigDBMethodResource extends FloodlightResource {

    public static class Method {
        private final String name;
        public Method(String name) {
            this.name = name;
        }
        @BigDBProperty(value="name")
        public String getName() {
            return name;
        }
    }

    private final AuthenticatorMethod.Registry registry;

    public BigDBMethodResource(AuthenticatorMethod.Registry registry) {
        this.registry = registry;
    }

    @BigDBQuery
    public Iterator<Method> getMethods() throws BigDBException {
        try {
            ArrayList<Method> l = new ArrayList<Method>();
            for (String s : registry.getMethodNames()) {
                l.add(new Method(s));
            }
            return l.iterator();
        } catch (AuthenticationException e) {
            throw new BigDBException(e.getMessage());
        }
    }

}
