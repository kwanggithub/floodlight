package org.projectfloodlight.db.rest.auth;

import org.projectfloodlight.db.auth.AuthorizationException;

public class AuthenticationRequiredException extends AuthorizationException {

    private static final long serialVersionUID = 1L;

    public AuthenticationRequiredException() {
        super();
    }

    public AuthenticationRequiredException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public AuthenticationRequiredException(String arg0) {
        super(arg0);
    }

}
