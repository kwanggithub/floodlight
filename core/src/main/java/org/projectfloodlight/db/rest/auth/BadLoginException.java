package org.projectfloodlight.db.rest.auth;

import org.restlet.data.Status;

/**
 * Exception thrown by LoginRequest.validate() if the login data sent is not
 * well-formed / incomplete.
 * 
 * @see LoginRequest
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BadLoginException extends Exception {
    private static final long serialVersionUID = 1L;

    /** HTTP Status code to convey to the client */
    private final Status status;

    public BadLoginException(Status status, String msg) {
        super(msg);
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

}
