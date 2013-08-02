package net.bigdb.schema;

import net.bigdb.BigDBException;

public class SchemaNodeConfigurableConflictException extends BigDBException {

    private static final long serialVersionUID = -1369986914121540923L;
    
    public SchemaNodeConfigurableConflictException(SchemaNode parent, SchemaNode child) {
        super("Schema node [" + child.getName() + "]" + " has configurability setting" +
              " that conflicts with its parent [" + parent.getName() + "].");
    }    
}
