package net.bigdb.schema;

import net.bigdb.BigDBException;

public class ModulePrefixNotFoundException extends BigDBException {

    private static final long serialVersionUID = 1L;

    public ModulePrefixNotFoundException(String prefix) {
        super("Module prefix not found: " + prefix);
    }
}
