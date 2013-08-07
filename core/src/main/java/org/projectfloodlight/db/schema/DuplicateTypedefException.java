package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

public class DuplicateTypedefException extends BigDBException {

    private static final long serialVersionUID = 1L;

    public DuplicateTypedefException(String name) {
        super("Duplicate typedef error: " + name);
    }
}
