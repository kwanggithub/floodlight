package org.projectfloodlight.db.yang;


// unchecked exception to make antlr happy when generating parser.
public class InvalidStatementException extends RuntimeException {

    private static final long serialVersionUID = 38448248381310309L;

  
    public InvalidStatementException(String statement, String parentStatement) {
        super("Invalid statement: " + statement + 
              " specified in statement " + parentStatement);
    }
}
