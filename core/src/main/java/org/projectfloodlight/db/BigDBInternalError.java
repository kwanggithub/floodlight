package org.projectfloodlight.db;

/**
 * Exception class for situations that indicate an internal error in BigDB.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class BigDBInternalError extends RuntimeException {

    private static final long serialVersionUID = 5060190247965230883L;

    public BigDBInternalError() {
    }

    private static String makeMessage(String message) {
        String returnMessage = "Internal BigDB Error";
        if (message != null) {
            returnMessage = returnMessage + ": " + message;
        }
        return returnMessage;
    }

    public BigDBInternalError(String message) {
        super(makeMessage(message));
    }

    public BigDBInternalError(Throwable throwable) {
        super(makeMessage(null), throwable);
    }

    public BigDBInternalError(String message, Throwable throwable) {
        super(makeMessage(message), throwable);
    }
}
