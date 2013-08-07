package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;

public class DataSourceMappingPredicateException extends BigDBException {

    private static final long serialVersionUID = -7624544432818650958L;

    protected String predicate;
    
    public DataSourceMappingPredicateException(String predicate) {
        super(String.format("Invalid predicate: \"%s\"", predicate));
        this.predicate = predicate;
    }
    
    public String getPredicate() {
        return predicate;
    }
}
