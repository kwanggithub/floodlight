package org.projectfloodlight.db;

public class BigDBException extends Exception {

    public enum Type {

        MULTIPLE_CHOICES(300),
        MOVED_PERMANENTLY(301),
        SEE_OTHER(303),
        NOT_MODIFIED(304),
        TEMPORARY_REDIRECT(307),
        BAD_CLIENT_REQUEST(400),
        UNAUTHORIZED(401),
        FORBIDDEN(403),
        NOT_FOUND(404),
        METHOD_NOT_ALLOWED(405),
        NOT_ACCEPTABLE(406),
        REQUEST_TIMEOUT(408),
        CONFLICT(409),
        GONE(410),
        REQUEST_ENTITY_TOO_LARGE(413),
        UNSUPPORTED_MEDIA_TYPE(415),
        REQUESTED_RANGE_NOT_SATISFIABLE(416),
        INTERNAL_SERVER_ERROR(500),
        NOT_IMPLEMENTED(501),
        SERVICE_UNAVAILABLE(503);

        protected int code;

        private Type(int code) {
            this.code = code;
        }
    }

    private static final long serialVersionUID = -3270724596836822030L;

    protected final static String newLine = System.getProperty("line.separator");

    protected final Type errorType;

    protected static String buildMessage(String baseMessage,
            String detailMessage) {
        String message = baseMessage;
        if ((detailMessage != null) && !detailMessage.isEmpty())
            message = message + ": " + detailMessage;
        return message;
    }

    public BigDBException() {
        this("Unspecified BigDB error");
    }

    public BigDBException(String message) {
        // FIXME: BAD_CLIENT_REQUEST is not correct in all cases.
        // Need to fix up more uses to set the correct error type
        this(message, Type.BAD_CLIENT_REQUEST);
    }

    public BigDBException(String message, Type errorType) {
        super(message);
        this.errorType = errorType;
    }

    public BigDBException(String message, Throwable cause) {
        // FIXME: BAD_CLIENT_REQUEST is not correct in all cases.
        // Need to fix up more uses to set the correct error type
        this(message, Type.BAD_CLIENT_REQUEST, cause);
    }

    public BigDBException(String message, Type errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public Type getErrorType() {
        return errorType;
    }
}
