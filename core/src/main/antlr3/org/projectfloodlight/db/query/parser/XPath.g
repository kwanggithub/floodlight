grammar XPath;

options {
    language=Java;
    backtrack=true;
    memoize=true;
}

@header {
package org.projectfloodlight.db.query.parser;

import java.io.IOException;
import java.math.BigDecimal;
import org.projectfloodlight.db.expression.*;
import org.projectfloodlight.db.query.Step;
}

@lexer::header {
package org.projectfloodlight.db.query.parser;
}

@members {

private List<String> errors = new ArrayList<String>();
private VariableReplacer variableReplacer = new VariableReplacer.FailReplacer();

public void setVariableReplacer(VariableReplacer variableReplacer) {
    this.variableReplacer = variableReplacer;
}

public void displayRecognitionError(String[] tokenNames,
                                    RecognitionException e) {
    String hdr = getErrorHeader(e);
    String msg = getErrorMessage(e, tokenNames);
    errors.add(hdr + " " + msg);
}

public List<String> getErrors() {
    return errors;
}

protected String getQuotedStringContents(String quotedString) {
    if (quotedString == null) return "";
    if (quotedString.length() >= 2) {
        return quotedString.substring(1, quotedString.length() - 1);
    } else {
        return quotedString;
    }
}

public void recover(IntStream input, RecognitionException re) {
    throw new StopParsingException(re);
}

}

xPathExpr returns [Expression result]
    : expr { $result = $expr.result; }
    ;

locationPath returns [LocationPathExpression result]
    : relativeLocationPath { $result = $relativeLocationPath.result; }
    | absoluteLocationPathNoroot { $result = $absoluteLocationPathNoroot.result; }
    ;

absoluteLocationPathNoroot returns [LocationPathExpression result]
    : '/' r = potentiallyEmptyRelativeLocationPathBuilder { LocationPathExpression.Builder builder = $r.result; $result = builder.setAbsolute(true).getPath(); }
// Don't support double-slash currently 
//    | '//' relativeLocationPath
    ;

relativeLocationPath returns [LocationPathExpression result]
    : r = relativeLocationPathBuilder { LocationPathExpression.Builder builder = $r.result; $result = builder.getPath(); }
    ;

potentiallyEmptyRelativeLocationPathBuilder returns [LocationPathExpression.Builder result = new LocationPathExpression.Builder()]
// Don't support double slash currently
//    : step (('/'|'//') step)*
    : (s1 = step { $result.addStep($s1.result); } ('/' s2 = step { $result.addStep($s2.result); })* )?
    ;


relativeLocationPathBuilder returns [LocationPathExpression.Builder result = new LocationPathExpression.Builder()]
// Don't support double slash currently
//    : step (('/'|'//') step)*
    : s1 = step { $result.addStep($s1.result); } ('/' s2 = step { $result.addStep($s2.result); })*
    ;

step returns [Step result]
    @init { Step.Builder builder = new Step.Builder(); }
    @after { result  = builder.getStep(); }
    : (axisSpecifier { builder.setAxisName($axisSpecifier.result); })?
      nodeTest { builder.setName($nodeTest.result); }
      (predicate { builder.addPredicate($predicate.result); } )*
//    | '.'
// Don't support .. currently
//    | '..'
    ;

axisSpecifier returns [String result]
    : AxisName { $result = $AxisName.text; } '::'
// Don't have attributes (at least not in the XML sense) in BigDB
//    | '@'?
    ;

nodeTest returns [String result]
     : nameTest { $result = $nameTest.result; }
//    | NodeType '(' ')'
//    | 'processing-instruction' '(' Literal ')'
    ;

predicate returns [Expression result]
    : '[' expr { $result = $expr.result; } ']'
    ;

expr returns [Expression result]
    : orExpr { $result = $orExpr.result; }
    ;

primaryExpr returns [Expression result]
    : '(' expr { $result = $expr.result; } ')'
    | literalString { $result = new StringLiteralExpression($literalString.result); }
    | INTEGER { $result = new IntegerLiteralExpression(Long.parseLong($INTEGER.text)); }
    | DECIMAL  { $result = new DecimalLiteralExpression(new BigDecimal(($DECIMAL != null) && ($DECIMAL.text != null) ? $DECIMAL.text : "0")); }
    | functionCall { $result = $functionCall.result; }
    // ????????????
//    | NCName {$result = new StringLiteralExpression($NCName.text); }
// Don't support variables currently
    | v = variableReference { $result = variableReplacer.replace($v.result); }
    ;

functionCall returns [FunctionCallExpression result]
    : FunctionName { $result = new FunctionCallExpression($FunctionName.text);} 
      '(' ( e1 = expr { $result.addArgument($e1.result); } 
          ( ',' e2 = expr { $result.addArgument($e2.result); } )* )? ')'
    ;

unionExprNoRoot returns [Expression result = null]
@init { UnionExpression unionExpression = null; }
// Don't support union expressions currently
    : a1 = pathExprNoRoot { $result = $a1.result; } 
      ('|' a2 = pathExprNoRoot { if (unionExpression == null) { unionExpression = new UnionExpression(); unionExpression.addExpression($result); $result = unionExpression; } unionExpression.addExpression($a2.result); } )*
//    | '/' '|' unionExprNoRoot
    ;

pathExprNoRoot returns [Expression result]
    : locationPath { $result = $locationPath.result; }
// Don't support double-slash currently
//    | filterExpr (('/'|'//') relativeLocationPath)?
//    | filterExpr {$result = $filterExpr.result; } 
//      ('/' relativeLocationPath {$filterExpr.result.setPathExpression($relativeLocationPath.result);})?
    | primaryExpr { $result = $primaryExpr.result; }
    ;

//filterExpr returns [Expression result]
//@init { FilterExpression filterExpression = null; }
//    : primaryExpr {$result = $primaryExpr.result; } 
//      (predicate { if (filterExpression == null) { filterExpression = new FilterExpression(); filterExpression.setPrimaryExpression($result); $result = filterExpression; } filterExpression.addPredicate($predicate.result); })*
//    ;

// Another way to model this is to use a list of operands.
orExpr returns [Expression result]
    : a1 = andExpr { $result = $a1.result; }
      ('or' a2 = andExpr { $result = new BinaryOperatorExpression(BinaryOperatorExpression.Operator.OR, $result, $a2.result); })*
    ;

andExpr returns [Expression result]
    : a1 = equalityExpr { $result = $a1.result; }
      ('and' a2 = equalityExpr { $result = new BinaryOperatorExpression(BinaryOperatorExpression.Operator.AND, $result, $a2.result); })*
    ;
   
equalityExpr returns [Expression result]
@init { BinaryOperatorExpression.Operator op = null; }
    : a1 = relationalExpr { $result = $a1.result; }
      (('=' { op = BinaryOperatorExpression.Operator.EQ; } |
        '!=' { op = BinaryOperatorExpression.Operator.NE; }) 
      a2 = relationalExpr { $result = new BinaryOperatorExpression(op, $result, $a2.result); })*
    ;

relationalExpr returns [Expression result]
@init { BinaryOperatorExpression.Operator op = null; }
    : a1 = additiveExpr { $result = $a1.result; }
      (('<' { op = BinaryOperatorExpression.Operator.LT; } |
        '>' { op = BinaryOperatorExpression.Operator.GT; } |
        '<=' { op = BinaryOperatorExpression.Operator.LE; } |
        '>=' { op = BinaryOperatorExpression.Operator.GE; } ) 
      a2 = additiveExpr { $result = new BinaryOperatorExpression(op, $result, $a2.result); })*
    ;

additiveExpr returns [Expression result]
@init { BinaryOperatorExpression.Operator op = null; }
    : a1 = multiplicativeExpr { $result = $a1.result; }
      (('+' { op = BinaryOperatorExpression.Operator.PLUS; } |
        '-' { op = BinaryOperatorExpression.Operator.MINUS; }) 
      a2 = multiplicativeExpr { $result = new BinaryOperatorExpression(op, $result, $a2.result); })*
    ;

multiplicativeExpr returns [Expression result]
@init { BinaryOperatorExpression.Operator op = null; }
    : u1 = unaryExprNoRoot { $result = $u1.result; }
     (('*' { op = BinaryOperatorExpression.Operator.MULT; } |
       'div' { op = BinaryOperatorExpression.Operator.DIV; } |
       'mod' { op = BinaryOperatorExpression.Operator.MOD; } ) 
      u2 = unaryExprNoRoot { $result = new BinaryOperatorExpression(op, $result, $u2.result); })*
    ;

unaryExprNoRoot returns [Expression result]
    : '-' unionExprNoRoot { $result = new UnaryOperatorExpression(UnaryOperatorExpression.Operator.MINUS, $unionExprNoRoot.result); }
    | unionExprNoRoot { $result = $unionExprNoRoot.result; }
    ;

qName returns [String result]
    : n1 = nnCName { $result = $n1.result; } (':' n2 = nnCName { $result += ":" + $n2.result; })*
    ;

nnCName returns [String result]
    : nCName {$result = $nCName.result; }
    ;
    
//functionName returns [String result]    // Ignore NodeType since it doesn't really apply to BigDB
//    : qName { $result = $qName.result; }
//    ;

// Don't support variables currently
variableReference  returns [String result]
    : '$' qName { $result = $qName.result; }
    ;

nameTest returns [String result]
    : '*' { $result = "*"; }
    | qName { $result = $qName.result; }
    | nCName ':' '*' { $result = $nCName.result + ":" + "*"; }
    ;

nCName returns [String result]
    : NCName {$result = $NCName.text; }
    | AxisName { $result = $AxisName.text; }
    ;

//NodeType
//    : 'comment'
//    | 'text'
//    | 'processing-instruction'
//    | 'node'
//    ;

INTEGER
    : DIGITS
    ;
    
DECIMAL 
    : DIGITS '.' DIGITS?
    | '.' DIGITS
    ;
    
fragment
DIGITS
    : ('0'..'9')+
    ;

literalString returns [String result]
    : Literal { $result = getQuotedStringContents($Literal.text); }
    ;

// Not sure how many of the axis names we'll support initially
// Probably not very many. Maybe none?
AxisName
    : 'ancestor' 
    | 'ancestor-or-self' 
    | 'attribute'
    | 'child'
    | 'descendant'
    | 'descendant-or-self' 
    | 'following'
    | 'following-sibling'
    | 'namespace' 
    | 'parent' 
    | 'preceding'
    | 'preceding-sibling'
    | 'self' 
    ;

FunctionName
    : 'starts-with'
    ;

Literal
    :  '"' (~'"')* '"'
    |  '\'' ~'\''* '\''
    ;

WS
    : (' '|'\t'|'\n'|'\r')+ { $channel = HIDDEN; }
    ;

NCName
    : NCNameStartChar NCNameChar*
    ;

fragment
HexNumberChar
    : '0'..'9'
    | 'a'..'f'
    | 'A'..'F'
    ;
    
fragment
NCNameStartChar
    : 'A'..'Z'
    |  '_'
    | 'a'..'z'
    | '\u00C0'..'\u00D6'
    | '\u00D8'..'\u00F6'
    | '\u00F8'..'\u02FF'
    | '\u0370'..'\u037D'
    | '\u037F'..'\u1FFF'
    | '\u200C'..'\u200D'
    | '\u2070'..'\u218F'
    | '\u2C00'..'\u2FEF'
    | '\u3001'..'\uD7FF'
    | '\uF900'..'\uFDCF'
    | '\uFDF0'..'\uFFFD'
    ;

fragment
NCNameChar
    : NCNameStartChar
    | '-'
    | '.'
    | '0'..'9'
    | '\u00B7'
    | '\u0300'..'\u036F'
    | '\u203F'..'\u2040'
    ;