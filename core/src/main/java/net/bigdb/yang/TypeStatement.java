package net.bigdb.yang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeStatement extends Statement implements Nameable {
    
    protected String prefix;
    protected String name;
    protected NumericalRestrictions numericalRestrictions;
    protected StringRestrictions stringRestrictions;
    protected Map<String, EnumStatement> enumSpecification;
    // only used for union type
    protected List<TypeStatement> unionTypes = 
            new ArrayList<TypeStatement>();
    
    public TypeStatement() {
        this(null, null);
    }
    
    public TypeStatement(String prefix, String name) {
        this.prefix = prefix;
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    public NumericalRestrictions getNumericalRestrictions() {
        return numericalRestrictions;
    }

    public StringRestrictions getStringRestrictions() {
        return stringRestrictions;
    }
    
    public Map<String, EnumStatement> getEnumSpecification() {
        return enumSpecification;
    }
    
    public void setNumericalRestrictions(NumericalRestrictions numericalRestrictions) {
        this.numericalRestrictions = numericalRestrictions;
    }
    
    public void setStringRestrictions(StringRestrictions stringRestrictions) {
        this.stringRestrictions = stringRestrictions;
    }
    
    public void addEnum(EnumStatement enumStatement) {
        if (enumSpecification == null)
            enumSpecification = new HashMap<String, EnumStatement>();
        
        // Set the value if one wasn't specified explicitly.
        // The value is set to 1 greater than the largest value that's been
        // set so far, or 0 if it's the first one.
        if (enumStatement.getValue() == null) {
            long v = 0;
            for (EnumStatement ev : this.enumSpecification.values()) {
                if (v <= ev.getValue())
                    v = ev.getValue() + 1;
            }
            enumStatement.setValue(v);
        }
        
        enumSpecification.put(enumStatement.getName(), enumStatement);
    }
    
    public void addUnionTypeStatement(TypeStatement typeStatement) 
            throws InvalidStatementException {
        if (!name.equals("union")) {
            throw new InvalidStatementException(typeStatement.getName(), name);
        }
        this.unionTypes.add(typeStatement);
    }
    
    public List<TypeStatement> getUnionTypeStatements() {
        return Collections.unmodifiableList(this.unionTypes);
    }
}
