package org.projectfloodlight.db.yang;

import java.util.ArrayList;
import java.util.List;

public class StringRestrictions {

    protected LengthStatement lengthStatement;
    protected List<PatternStatement> patternStatements = new ArrayList<PatternStatement>();
    
    public LengthStatement getLengthStatement() {
        return lengthStatement;
    }
    
    public List<PatternStatement> getPatternStatements() {
        return patternStatements;
    }
    
    public void setLengthStatement(LengthStatement lengthStatement) {
        this.lengthStatement = lengthStatement;
    }
    
    public void addPatternStatement(PatternStatement patternStatement) {
        this.patternStatements.add(patternStatement);
    }
}
