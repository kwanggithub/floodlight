package net.bigdb.schema;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;

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
