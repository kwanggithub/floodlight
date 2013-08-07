package org.projectfloodlight.db.query.parser;

public class StopParsingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StopParsingException() {
    }

    public StopParsingException(String message) {
        super(message);
    }

    public StopParsingException(Throwable cause) {
        super(cause);
    }

    public StopParsingException(String message, Throwable cause) {
        super(message, cause);
    }

}
