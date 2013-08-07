package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeFactory;
import org.projectfloodlight.db.data.LeafDataNode;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;

/**
 * A  schema node to model the boolean type statement.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class BooleanTypeSchemaNode extends TypeSchemaNode {

    public BooleanTypeSchemaNode() {
        this(null, null);
    }

    public BooleanTypeSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, LeafType.BOOLEAN);
    }

    @Override
    public LeafDataNode parseDataValueString(DataNodeFactory factory,
            String valueString) throws BigDBException {
        boolean b;
        if (valueString != null && valueString.equals("true")) {
            b = true;
        } else if (valueString != null && valueString.equals("false")) {
            b = false;
        } else {
            throw new BigDBException(
                                     "Invalid boolean default value: " +
                                             valueString);
        }
        if (factory == null) {
            return new MemoryLeafDataNode(b);
        } else {
            return factory.createLeafDataNode(b);
        }
    }
}
