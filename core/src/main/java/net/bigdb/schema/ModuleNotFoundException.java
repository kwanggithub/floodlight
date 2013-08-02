package net.bigdb.schema;

import net.bigdb.BigDBException;

public class ModuleNotFoundException extends BigDBException {

    private static final long serialVersionUID = 1L;

    public ModuleNotFoundException(ModuleIdentifier moduleId) {
        super("Module not found: " + moduleId.toString());
    }
}
