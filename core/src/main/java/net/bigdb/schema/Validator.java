package net.bigdb.schema;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;

public abstract class Validator implements Cloneable {
    
    public enum Type {
        STRING_VALIDATOR,
        RANGE_VALIDATOR,
        LENGTH_VALIDATOR,
        PATTERN_VALIDATOR,
        ENUMERATION_VALIDATOR,
        COMPOUND_VALIDATOR
    }
    
    private Type type;
    
    public Validator(Type type) {
        this.type = type;
    }
    
    public Type getType() {
        return this.type;
    }

    public abstract void validate(DataNode data) throws BigDBException;

    @Override
    public Validator clone() {
        try {
            Validator validator = (Validator) super.clone();
            return validator;
        }
        catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
