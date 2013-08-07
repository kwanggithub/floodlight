package org.projectfloodlight.db.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;

public class CompoundValidator extends Validator {
    
    protected List<Validator> validators = new ArrayList<Validator>();
    
    public CompoundValidator() {
        super(Type.COMPOUND_VALIDATOR);
    }
    
    public CompoundValidator(Set<Validator> validators) {
        super(Type.COMPOUND_VALIDATOR);
        if (validators != null)
            this.validators.addAll(validators);
    }
    
    public void add(Validator validator) {
        validators.add(validator);
    }
    
    public void remove(int index) {
        validators.remove(index);
    }
    
    public void remove(Validator validator) {
        validators.remove(validator);
    }
    
    public List<Validator> getValidators() {
        return Collections.unmodifiableList(validators);
    }
    
    public void clear() {
        validators.clear();
    }
    
    public void validate(DataNode data) throws BigDBException {
        for (Validator validator: validators) {
            validator.validate(data);
        }
    }
    
    @Override
    public CompoundValidator clone() {
        CompoundValidator cv = (CompoundValidator) super.clone();
        cv.validators = new ArrayList<Validator>();
        for (Validator validator : this.validators) {
            cv.add(validator.clone());
        }
        return cv;
    }
}
