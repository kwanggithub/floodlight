package net.bigdb.auth;

import org.restlet.data.Status;

/**
 * an exception class that conveys the reason for an authentication failure.
 * Supports an internal message (that's being logged) and the 'normal' message
 * that is sent to the user.
 * <p>
 * NOTE: Throw this exception for an 'exceptional' condition (e.g., a failed
 * communication with the upstream authentication server). Not for a regular
 * authentication failure.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthenticationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Status status;

    private final String internalMessage;

    public AuthenticationException(Status status, String msg, String internalMessage) {
        super(msg);
        this.status = status;
        this.internalMessage = internalMessage;
    }

    public String getInternalMessage() {
        return internalMessage;
    }

    public Status getStatus() {
        return status;
    }

}
