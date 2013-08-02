package net.bigdb.data;

import java.util.regex.Pattern;

import net.bigdb.BigDBException;
import net.bigdb.schema.SchemaNode;

public class DataSourceMapping {

    private interface Predicate {
        boolean matches(SchemaNode schemaNode);
    }
    
    private static class NegatePredicate implements Predicate {
        
        protected Predicate predicate;
        
        public NegatePredicate(Predicate predicate) {
            this.predicate = predicate;
        }
        
        public boolean matches(SchemaNode schemaNode) {
            boolean childMatches = predicate.matches(schemaNode);
            return !childMatches;
        }
    }
    
    private static class AttributePredicate implements Predicate {
        
        private String attributeName;
        
        public AttributePredicate(String attributeName) {
            this.attributeName = attributeName;
        }
        
        public boolean matches(SchemaNode schemaNode) {
            return schemaNode.getBooleanAttributeValue(attributeName, false);
        }
    }
    
    protected Predicate predicate;
    protected enum TargetType { DATA_SOURCE_NAME, ATTRIBUTE_NAME };
    protected TargetType targetType;
    protected String target;
    
    // TODO: figure out whether "-" should be allowed
    static Pattern attributeNamePattern =
            Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    
    public DataSourceMapping(String predicateString, String targetString)
            throws BigDBException {
        if (predicateString != null && !predicateString.isEmpty()) {
            String trimmedPredicateString = predicateString.trim();
            boolean negate = false;
            if (trimmedPredicateString.startsWith("!")) {
                negate = true;
                trimmedPredicateString = trimmedPredicateString.substring(1).trim();
            }
            
            // FIXME: Implement real predicate parsing that handles
            // more complex expression logic. Currently this assumes that
            // the predicate is simply a single attribute name and that the
            // attribute has a boolean value, i.e. either "true" or "false".
            if (!attributeNamePattern.matcher(trimmedPredicateString).matches())
                throw new DataSourceMappingPredicateException(predicateString);

            Predicate predicate = new AttributePredicate(trimmedPredicateString);
            if (negate)
                predicate = new NegatePredicate(predicate);
            
            this.predicate = predicate;
        }
        
        target = targetString.trim();
        targetType = TargetType.DATA_SOURCE_NAME;
        if (target.startsWith("$")) {
            target = target.substring(1).trim();
            targetType = TargetType.ATTRIBUTE_NAME;
        }
        if (target.isEmpty())
            target = null;
    }
    
    public boolean matches(SchemaNode schemaNode) {
        // A null predicate is treated as the default mapping so we always
        // return true in that case.
        return (predicate == null) || predicate.matches(schemaNode);
    }
    
    public String getDataSource(SchemaNode schemaNode) {
        if (targetType == TargetType.DATA_SOURCE_NAME)
            return target;
        
        return schemaNode.getAttribute(target);
    }
}
