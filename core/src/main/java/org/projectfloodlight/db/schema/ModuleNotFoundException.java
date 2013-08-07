package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

public class ModuleNotFoundException extends BigDBException {

    private static final long serialVersionUID = 1L;

    public ModuleNotFoundException(ModuleIdentifier moduleId) {
        super("Module not found: " + moduleId.toString());
    }
}
