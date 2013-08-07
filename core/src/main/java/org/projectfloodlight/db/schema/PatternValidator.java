package org.projectfloodlight.db.schema;

import java.util.regex.Pattern;

import org.projectfloodlight.db.data.DataNode;

public class PatternValidator extends Validator {
    private String pattern;
    Pattern regex;
    
    public PatternValidator() {
        super(Type.PATTERN_VALIDATOR);
    }

    public PatternValidator(String pattern) {
        super(Type.PATTERN_VALIDATOR);
        assert pattern != null;
        this.setPattern(pattern);
    }
    
    @Override
    public int hashCode() {
        return this.getPattern().hashCode();
    }
    
    public final void setPattern(String pattern) {
        regex = Pattern.compile(pattern);
        this.pattern = pattern;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PatternValidator)) {
            return false;
        }
        PatternValidator v = (PatternValidator)o;
        return this.getPattern().equals(v.getPattern());
    }

    @Override
    public void validate(DataNode data) throws ValidationException {
        try {
            String value = data.getString();
            if (value == null) {
                throw new ValidationException(
                             "Cannot fetch value from data node: " +
                             data.toString());
            }
            if (!regex.matcher(value).matches()) {
                throw new ValidationException("Value :" + value + 
                                              " does not match" + 
                                              " pattern " + getPattern());
            }
        } catch (Exception e) {
            throw new ValidationException("Data validation failed for " + 
                                          data.toString());
        }
    }

    public String getPattern() {
        return pattern;
    }
}
