package net.bigdb.query;

import java.util.List;

import net.bigdb.ParserException;

public class XPathParsingException extends ParserException {
    
    private static final long serialVersionUID = 6591331000339084715L;
    
    public XPathParsingException(String message, List<String> parseErrors) {
        super(message, parseErrors);
    }
}
