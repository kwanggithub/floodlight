package net.bigdb;

import java.util.List;

import net.bigdb.BigDBException;

public class ParserException extends BigDBException {

    private static final long serialVersionUID = 6591331000339084713L;

    private List<String> errors;

    public ParserException(String message, List<String> parseErrors) {
        super(message);
        errors = parseErrors;
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getMessage());
        sb.append(";");
        sb.append(newLine);
        if (errors != null) {
            for (String s : this.errors) {
                sb.append(s);
                sb.append(";");
                sb.append(newLine);
            }
        }
        return sb.toString();
    }

}
