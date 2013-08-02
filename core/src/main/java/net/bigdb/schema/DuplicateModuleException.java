package net.bigdb.schema;

import net.bigdb.BigDBException;

public class DuplicateModuleException extends BigDBException {

    private static final long serialVersionUID = 1L;

    public DuplicateModuleException(ModuleIdentifier moduleId) {
        super("Duplicate module error: " + moduleId.toString());
    }
}
