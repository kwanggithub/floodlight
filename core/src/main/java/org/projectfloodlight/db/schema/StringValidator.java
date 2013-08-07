package org.projectfloodlight.db.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.yang.LengthPart;
import org.projectfloodlight.db.yang.RangeBoundary;
import org.projectfloodlight.db.yang.RangePart;

public class StringValidator extends Validator {
    
    private LengthValidator lengthValidator = new LengthValidator();
    private Map<String, PatternValidator> patterns = 
            new HashMap<String, PatternValidator>();
    
    public StringValidator() {
        super(Type.STRING_VALIDATOR);
    }
    
    public LengthValidator getLengthValidator() {
        return this.lengthValidator;
    }
    
    public Collection<PatternValidator> getPatternValidators() {
        return this.patterns.values();
    }
    
    public void addPattern(String pattern) {
        if (!patterns.containsKey(pattern)) {
            patterns.put(pattern, new PatternValidator(pattern));
        }
    }
    
    @Override
    public StringValidator clone() {
        StringValidator sv = (StringValidator) super.clone();
        sv.lengthValidator = (LengthValidator) lengthValidator.clone();
        sv.patterns = new HashMap<String, PatternValidator>();
        for (Map.Entry<String, PatternValidator> entry : patterns.entrySet()) {
            sv.patterns.put(entry.getKey(), (PatternValidator)
                    entry.getValue().clone());
        }
        return sv;
    }
    
    @Override
    public void validate(DataNode data) throws ValidationException {
        try {
            this.lengthValidator.validate(data);
            for (PatternValidator pv : patterns.values()) {
                pv.validate(data);
            }
        } catch (Exception e) {
            throw new ValidationException("Data validation failed for " + 
                                          data.toString());
        }
    }
    
    public void add(Collection<LengthPart> lengthParts) 
            throws BigDBException {
        if (lengthParts == null || lengthParts.size() == 0) {
            return;
        }
        Collection<RangePart> rps = new ArrayList<RangePart>();
        for (LengthPart lp : lengthParts) {
            rps.add(new RangePart(new RangeBoundary(lp.getStart().getStringValue()),
                                  new RangeBoundary(lp.getEnd().getStringValue())));
        }
        lengthValidator.checkAndSet(rps);
    }
}
