package org.projectfloodlight.db.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;

public class EnumValidator extends Validator {
    Map<String, Long> nameValue = new HashMap<String, Long>();
    Map<Long, String> valueName = new HashMap<Long, String>();

    public EnumValidator() {
        super(Type.ENUMERATION_VALIDATOR);
    }

    @Override
    public void validate(DataNode data) throws ValidationException {
        try {
            if (data.getObject() instanceof String) {
                String v = data.getString();
                if (!this.nameValue.containsKey(v)) {
                    // Didn't fall within any of the ranges, so we throw a
                    // validation exception.
                    throw new ValidationException(
                          "Value is not a valid enum: " + v);
                }
            } else {
                Long v = data.getLong();
                if (!valueName.containsKey(v)) {
                    // Didn't fall within any of the ranges, so we throw a
                    // validation exception.
                    throw new ValidationException(
                          String.format("Value \"%s\" is not a valid enum", v));
                }
            }
        } catch (BigDBException e) {
            throw new ValidationException("Could not fetch the data.");            
        }
    }
    
    public void addValue(String name, Long value) {
        this.valueName.put(value, name);
        this.nameValue.put(name, value);
    }

    public Map<String, Long> getNames() {
        return Collections.unmodifiableMap(this.nameValue);
    }

    @Override
    public EnumValidator clone() {
        EnumValidator ev = (EnumValidator) super.clone();
        ev.nameValue = new HashMap<String, Long>();
        ev.valueName = new HashMap<Long, String>();
        for (Map.Entry<String, Long> entry : nameValue.entrySet()) {
            ev.addValue(entry.getKey(), entry.getValue());
        }
        return ev;
    }
}
