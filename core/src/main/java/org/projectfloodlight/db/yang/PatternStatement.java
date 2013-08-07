package org.projectfloodlight.db.yang;

public class PatternStatement extends RestrictionStatement {

    protected String pattern;
    
    public PatternStatement() {
        this(null);
    }
    
    public PatternStatement(String pattern) {
        this.pattern = pattern;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
