package org.projectfloodlight.db.query;

import java.util.List;

import org.projectfloodlight.db.ParserException;

public class XPathParsingException extends ParserException {
    
    private static final long serialVersionUID = 6591331000339084715L;
    
    public XPathParsingException(String message, List<String> parseErrors) {
        super(message, parseErrors);
    }
}
