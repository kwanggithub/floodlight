package org.projectfloodlight.db.auth;

import org.projectfloodlight.db.BigDBException;

/**
 * Exception thrown by the BigDB layer when an attempted operation fails due to
 * authorization failure (i.e., insufficient permissions).
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthorizationException extends BigDBException {

    private static final String AUTHORIZATION_ERROR_MESSAGE = "Authorization failed";

    private static final long serialVersionUID = 1L;

    public AuthorizationException() {
        super(AUTHORIZATION_ERROR_MESSAGE, BigDBException.Type.FORBIDDEN);
    }

    public AuthorizationException(String message) {
        super(buildMessage(AUTHORIZATION_ERROR_MESSAGE, message),
                BigDBException.Type.FORBIDDEN);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(buildMessage(AUTHORIZATION_ERROR_MESSAGE, message),
                BigDBException.Type.FORBIDDEN, cause);
    }

}
