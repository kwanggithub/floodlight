package org.projectfloodlight.db.yang;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Statement {

    protected final static Logger logger = LoggerFactory.getLogger(Statement.class);

    public static enum Status { CURRENT, OBSOLETE, DEPRECATED };

    public static class Position {

        private final long line;
        private final long column;

        public Position(long line, long column) {
            this.line = line;
            this.column = column;
        }
        
        public long getLine() {
            return line;
        }
        
        public long getColumn() {
            return column;
        }
    }
    
    protected String source;
    protected Position start;
    protected Position end;

    // FIXME: robv: I think the value of the map here should be a list of
    // UnknownStatements, not a single one. Otherwise, we don't support
    // having multiple instances of the same extension statement, which seems
    // like it could useful/valid in some cases.
    // FIXME: Also, not sure if this belongs in the base Statement class,
    // since really only statements that can have a curly-bracketed body can
    // have child unknown statements. Should perhaps represent that in the
    // class hierarchy.
    protected final Map<String, UnknownStatement> unknownStatements =
            new HashMap<String, UnknownStatement>();

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
    }
    
    public Statement() {
    }
    
    public Statement(String source, Position start, Position end) {
        setSource(source);
        setRange(start, end);
    }
    
    public String getSource() {
        return source;
    }
    
    public Position getStart() {
        return start;
    }
    
    public Position getEnd() {
        return end;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public void setRange(Position start, Position end) {
        this.start = start;
        this.end = end;
    }

    public void addUnknownStatement(UnknownStatement ust) {
        this.unknownStatements.put(ust.getName(), ust);
    }
    
    public UnknownStatement getUnknownStatement(String name) {
        return this.unknownStatements.get(name);
    }
    
    public Map<String, UnknownStatement> getUnknownStatements() {
        return Collections.unmodifiableMap(this.unknownStatements);
    }

    @Override
    public String toString() {
        try {
            String result = mapper.writeValueAsString(this);
            return result;
        }
        catch (Exception e) {
            // Unless there's a bug we should never hit this
            logger.error("Error serializing YANG statement to JSON");
            return "<JSON serialization error>";
        }
    }
}
