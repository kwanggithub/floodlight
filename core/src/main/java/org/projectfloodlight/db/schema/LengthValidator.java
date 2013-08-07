package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;

public class LengthValidator extends RangeValidator<Long> {
   
    public LengthValidator() {
        super(0L, Long.MAX_VALUE, Type.LENGTH_VALIDATOR);
    }
    
    @Override
    public void validate(DataNode data) throws ValidationException {
        String value = null;
        try {
            value = data.getString();
        } catch (Exception e) {
            throw new ValidationException("Could not fetach the data.");
        }
        try {
            int l = value == null ? 0 : value.length();
            this.validate(Long.valueOf(l));
        } catch (BigDBException e) {
             throw new ValidationException("Length of string is out of range: " + value );
        }
    }
}
