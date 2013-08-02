package net.bigdb.schema;

import net.bigdb.BigDBException;

public class DuplicateTypedefException extends BigDBException {

    private static final long serialVersionUID = 1L;

    public DuplicateTypedefException(String name) {
        super("Duplicate typedef error: " + name);
    }
}
