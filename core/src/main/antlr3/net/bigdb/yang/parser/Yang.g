grammar Yang;

options {
    language=Java;
    backtrack=true;
    memoize=true;
    k=10;
}

@header {
package net.bigdb.yang.parser;

import java.io.IOException;
//import org.antlr.runtime.*;
import java.math.BigDecimal;
import net.bigdb.yang.*;
import net.bigdb.yang.Statement.Status;
}

@lexer::header {
package net.bigdb.yang.parser;
}

@members {

private List<String> errors = new ArrayList<String>();

public void displayRecognitionError(String[] tokenNames,
                                    RecognitionException e) {
    String hdr = getErrorHeader(e);
    String msg = getErrorMessage(e, tokenNames);
    errors.add(hdr + " " + msg);
}

public List<String> getErrors() {
    return errors;
}
    
protected void validateIdentifier(String s) {
}

protected void validateInteger(String s) {
}

protected String getSingleQuotedStringContents(String quotedString) {
    if (quotedString == null) return "";
    return quotedString.substring(1, quotedString.length() - 1);
}

protected String getDoubleQuotedStringContents(String quotedString) {
    if (quotedString == null) return "";
    StringBuilder builder = new StringBuilder();
    boolean inLeadingWhitespace = true;
    int endIndex = quotedString.length() - 1;
    for (int i = 1; i < endIndex; i++) {
        char c = quotedString.charAt(i);
        if ((c == '\r') || (c == '\n')) {
            if (!inLeadingWhitespace) {
                // FIXME: Do we need to handle different line ending styles here?
                builder.append('\n');
            }
            inLeadingWhitespace = true;
        } else if (!inLeadingWhitespace || !((c == ' ') || (c == '\t'))) {
            inLeadingWhitespace = false;
            if ((c == '\\') && (i < endIndex - 1)) {
                i++;
                c = quotedString.charAt(i);
                switch (c) {
                case 't':
                    c = '\t';
                    break;
                case 'n':
                    c = '\n';
                    break;
                case 'r':
                    c = '\r';
                    break;
                case '"':
                    c = '"';
                    break;
                case '\\':
                    break;
                default:
                    // FIXME: Should we treat this as an error?
                    break;
                }
            }
            builder.append(c);
        }
    }
    return builder.toString();
}

/*
protected String getStringLiteralValue(String stringLiteral) {
    StringBuilder builder = new StringBuilder();
    char[] chars = stringLiteral.toCharArray();
    for (int i = 1; i < chars.length - 1; i++) {
        char c = chars[i];
        if (c == '\\') {
            i++;
            c = chars[i];
        }
        builder.append(c);
    }
    return builder.toString();
}
*/

protected LengthStatement makeLengthStatement(String s) throws RecognitionException {
    LengthStatement lengthStatement = new LengthStatement();
    //String value = getStringLiteralValue(s);
    String value = s;
    ANTLRStringStream input = new ANTLRStringStream(value);
    YangLengthLexer lexer = new YangLengthLexer(input);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    YangLengthParser parser = new YangLengthParser(tokens);
    List<LengthPart> lengthParts = parser.lengthDescriptor();
    lengthStatement.setLengthParts(lengthParts);
    return lengthStatement;
}

protected RangeStatement makeRangeStatement(String s) throws RecognitionException {
    
    RangeStatement rangeStatement = new RangeStatement();
    //String value = getStringLiteralValue(s);
    String value = s;
    ANTLRStringStream input = new ANTLRStringStream(value);
    YangRangeLexer lexer = new YangRangeLexer(input);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    YangRangeParser parser = new YangRangeParser(tokens);
    
    List<RangePart> rangeParts = parser.rangeDescriptor();
    rangeStatement.setRangeParts(rangeParts);
    //RangePart rangePart = parser.rangePart();
    return rangeStatement;
}

}

moduleStatement returns [ModuleStatement module]
    :  'module' identifier { $module = new ModuleStatement($identifier.text); }
       '{'
           statementSep[$module]*
           moduleHeaderStatement[$module]*
           linkageStatement[$module]*
           metaStatement[$module]* 
           (revisionStatement  {$module.addRevision($revisionStatement.result); })*
           bodyStatement[$module]*
       '}';

submoduleStatement returns [SubmoduleStatement submodule]
    :  'submodule' identifier { $submodule = new SubmoduleStatement($identifier.text); }
       '{'
           submoduleHeaderStatement[$submodule]+
           linkageStatement[$submodule]+
           metaStatement[$submodule]+
           bodyStatement[$submodule]+
       '}';

moduleHeaderStatement[ModuleStatement module]
    :  yangVersionStatement    { $module.setYangVersion($yangVersionStatement.result); }
    |  namespaceStatement      { $module.setNamespace($namespaceStatement.result); }
    |  prefixStatement         { $module.setPrefix($prefixStatement.result); }
    ;

submoduleHeaderStatement[SubmoduleStatement submodule]
    : yangVersionStatement  { $submodule.setYangVersion($yangVersionStatement.result); }
    | belongsToStatement    { $submodule.setBelongsTo($belongsToStatement.result); }
    ;
    
yangVersionStatement returns [String result]
    : 'yang-version' string ';' { $result = $string.result; }
    ;

namespaceStatement returns [String result]
    : 'namespace' string ';' { $result = $string.result; }
    ;

prefixStatement returns [String result]
    : 'prefix' string ';' { $result = $string.result; }
    ;

belongsToStatement returns [BelongsToStatement result]
    :   'belongs-to' string { $result = new BelongsToStatement($string.result); }
        '{'
            prefixStatement { $result.setPrefix($prefixStatement.result); }
        '}'
    ;
    
linkageStatement[ModuleStatementCommon module]
    : importStatement      { $module.addImport($importStatement.result); }
    | includeStatement     { $module.addInclude($includeStatement.result); }
    ;

importStatement returns [ImportStatement result]
    :  'import' identifier { $result = new ImportStatement($identifier.text); }
       '{'
           prefixStatement         { $result.setPrefix($prefixStatement.result); }
           revisionDateStatement?  { $result.setRevisionDate($revisionDateStatement.result); }
       '}'
    ;

revisionDateStatement returns [String result]
    : 'revision-date' string ';' { $result = $string.result; }
    ;

revisionStatement returns [RevisionStatement result]
    : 'revision' string { $result = new RevisionStatement($string.result); }
      (';'|('{' revisionBodyStatement[$result]* '}'))
    ;

// ignore revision details
revisionBodyStatement[RevisionStatement revisionStatement]
    : descriptionStatement { $revisionStatement.setDescription($descriptionStatement.result); }
    | referenceStatement { $revisionStatement.setReference($referenceStatement.result); }
    ; 

includeStatement returns [IncludeStatement result]
    :  'include' identifier { $result = new IncludeStatement($identifier.text); }
       (';'|('{' revisionDateStatement? { $result.setRevisionDate($revisionDateStatement.result); } '}'))
    ;
    
metaStatement[ModuleStatementCommon module]
    : organizationStatement    { $module.setOrganization($organizationStatement.result); }
    | contactStatement         { $module.setContact($contactStatement.result); }
    | descriptionStatement     { $module.setDescription($descriptionStatement.result); }
    | referenceStatement       { $module.setReference($referenceStatement.result); }
    ;

organizationStatement returns [String result]
    : 'organization' string ';' { $result = $string.result; }
    ;

contactStatement returns [String result]
    : 'contact' string ';' { $result = $string.result; }
    ;

descriptionStatement returns [String result]
    : 'description' string ';' { $result = $string.result; }
    ;

referenceStatement returns [String result]
    : 'reference' string ';' { $result = $string.result; }
    ;
    
bodyStatement[ModuleStatementCommon module]
    : extensionStatement       { $module.addExtension($extensionStatement.result);}
    | dataDefinitionStatement  { $module.addDataStatement($dataDefinitionStatement.result); }
    | typedefStatement         { $module.addTypedef($typedefStatement.result); }
    | groupingStatement        { $module.addGroupingStatement($groupingStatement.result);}
    ;

extensionStatement returns [ExtensionStatement result]
    : 'extension' identifier { $result = new ExtensionStatement($identifier.text); }
       (';' | '{' extensionBodyStatement[$result]* '}')
    ; 

extensionBodyStatement[ExtensionStatement extension]       
    :   statementSep[$extension]
    |   argumentStatement       { $extension.setArgument($argumentStatement.result); }
    |   statusStatement         { $extension.setStatus($statusStatement.result); }
    |   descriptionStatement    { $extension.setDescription($descriptionStatement.result); }
    |   referenceStatement      { $extension.setReference($referenceStatement.result); }
    ;

argumentStatement returns [String result]
    :   'argument' identifier ';' { $result = $identifier.text; }
    ;

typedefStatement returns [TypedefStatement result]
    :   'typedef' identifier { $result = new TypedefStatement($identifier.text); }
        '{'
            typedefBodyStatement[$result]*
        '}'
    ;

typedefBodyStatement[TypedefStatement typedef]
    :   typeStatement           { $typedef.setType($typeStatement.result); }
    |   unitsStatement          { $typedef.setUnits($unitsStatement.result); }
    |   defaultStatement        { $typedef.setDefault($defaultStatement.result); }
    |   statusStatement         { $typedef.setStatus($statusStatement.result); }
    |   descriptionStatement    { $typedef.setDescription($descriptionStatement.result); }
    |   referenceStatement      { $typedef.setReference($referenceStatement.result); }
    ;

// Only support grouping defined in module level
// No support for nested grouping
groupingStatement returns [GroupingStatement result]
    :  'grouping' identifier { $result = new GroupingStatement($identifier.text); }
       (';'|('{' groupingBodyStatement[$result]* '}'))
    ;

groupingBodyStatement[GroupingStatement grouping]
    : statementSep[$grouping]
    | statusStatement         { $grouping.setStatus($statusStatement.result); }
    | descriptionStatement    { $grouping.setDescription($descriptionStatement.result); }
    | referenceStatement      { $grouping.setReference($referenceStatement.result); }
    | dataDefinitionStatement { $grouping.addChildStatement($dataDefinitionStatement.result); }
    ;
    
dataDefinitionStatement returns [DataStatement result]
    : containerStatement   { $result = $containerStatement.result; }
    | leafStatement        { $result = $leafStatement.result; }
    | leafListStatement    { $result = $leafListStatement.result; }
    | listStatement        { $result = $listStatement.result; }
//    | choiceStatement
//    | anyxmlStatement
    | usesStatement        { $result = $usesStatement.result; }
    ;

containerStatement returns [ContainerStatement result]
    :  'container' identifier { $result = new ContainerStatement($identifier.text); }
       (';'|('{' containerBodyStatement[$result]* '}'))
    ;

containerBodyStatement[ContainerStatement container]
    : statementSep[$container]
    | whenStatement            { $container.setWhen($whenStatement.result); }
    | presenceStatement        { $container.setPresence($presenceStatement.result); }
    | configStatement          { $container.setConfig($configStatement.result); }
    | statusStatement          { $container.setStatus($statusStatement.result); }
    | descriptionStatement     { $container.setDescription($descriptionStatement.result); }
    | referenceStatement       { $container.setReference($referenceStatement.result); }
    | dataDefinitionStatement  { $container.addChildStatement($dataDefinitionStatement.result); }
    ;

leafStatement returns [LeafStatement result]
    :   'leaf' identifier { $result = new LeafStatement($identifier.text); }
        '{' leafBodyStatement[$result]* '}'
    ;
    
leafBodyStatement[LeafStatement leaf]
    : statementSep[$leaf]
    | whenStatement         { $leaf.setWhen($whenStatement.result); }
    | typeStatement         { $leaf.setType($typeStatement.result); }
    | unitsStatement        { $leaf.setUnits($unitsStatement.result); }
    | defaultStatement      { $leaf.setDefault($defaultStatement.result); }
    | configStatement       { $leaf.setConfig($configStatement.result); }
    | mandatoryStatement    { $leaf.setMandatory($mandatoryStatement.result); }
    | statusStatement       { $leaf.setStatus($statusStatement.result); }
    | descriptionStatement  { $leaf.setDescription($descriptionStatement.result); }
    | referenceStatement    { $leaf.setReference($referenceStatement.result); }
    ;

leafListStatement returns [LeafListStatement result]
    :   'leaf-list' identifier { $result = new LeafListStatement($identifier.text); }
        '{' leafListBodyStatement[$result]+ '}'
    ;

leafListBodyStatement[LeafListStatement leafList]
    : statementSep[$leafList]
    | whenStatement             { $leafList.setWhen($whenStatement.result); }
    | typeStatement             { $leafList.setType($typeStatement.result); }
    | unitsStatement            { $leafList.setUnits($unitsStatement.result); }
    | configStatement           { $leafList.setConfig($configStatement.result); }
    | minElementsStatement      { $leafList.setMinElements($minElementsStatement.result); }
    | maxElementsStatement      { $leafList.setMaxElements($maxElementsStatement.result); }
    | statusStatement           { $leafList.setStatus($statusStatement.result); }
    | descriptionStatement      { $leafList.setDescription($descriptionStatement.result); }
    | referenceStatement        { $leafList.setReference($referenceStatement.result); }
    ;

listStatement returns [ListStatement result]
    :   'list' identifier { $result = new ListStatement($identifier.text); }
        '{' listBodyStatement[$result]+ '}'
    ;
    
listBodyStatement[ListStatement list]
    : statementSep[$list]
    | whenStatement             { $list.setWhen($whenStatement.result); }
    | keyStatement              { $list.setKey($keyStatement.result); }
    | configStatement           { $list.setConfig($configStatement.result); }
    | minElementsStatement      { $list.setMinElements($minElementsStatement.result); }
    | maxElementsStatement      { $list.setMaxElements($maxElementsStatement.result); }
    | statusStatement           { $list.setStatus($statusStatement.result); }
    | descriptionStatement      { $list.setDescription($descriptionStatement.result); }
    | referenceStatement        { $list.setReference($referenceStatement.result); }
    | dataDefinitionStatement   { $list.addChildStatement($dataDefinitionStatement.result); }
    ;

usesStatement returns [UsesStatement result = new UsesStatement()]
    :   'uses' (prefix=identifier ':' { $result.setPrefix($prefix.text); })? 
                name=identifier { $result.setName($name.text); }
        (';' | ('{' usesBodyStatement[$result]+ '}'))
    ;

usesBodyStatement[UsesStatement uses]
    : whenStatement             { $uses.setWhen($whenStatement.result); }
//  | ifFeatureStatement
    | statusStatement           { $uses.setStatus($statusStatement.result); }
    | descriptionStatement      { $uses.setDescription($descriptionStatement.result); }
    | referenceStatement        { $uses.setReference($referenceStatement.result); }
//  | refineStatement
//  | usesAugmentStatement
    ;
    
whenStatement returns [WhenStatement result]
    :  'when' string { $result = new WhenStatement($string.result); }
       (';'|('{' whenBodyStatement[$result]* '}'))
    ;

whenBodyStatement[WhenStatement when]
    : descriptionStatement { $when.setDescription($descriptionStatement.result); }
    | referenceStatement   { $when.setReference($referenceStatement.result); }
    ;

/*
prefixPart returns [String result]
    : (identifier ':' { $result = $identifier.text; })
    ;
    
prefixedIdentifier returns [TypeStatement result = new TypeStatement()]
    : (prefixPart { $result.setPrefix($prefixPart.result); })? identifier { $result.setName($identifier.text); }
    ;

prefixToken returns [String result]
    : identifier { $result = $identifier.text; } ':'
    ;

prefixedIdentifier returns [TypeStatement result = new TypeStatement()]
    :   pp=identifier { $result.setPrefix($pp.text); }
    ;
    
*/
  
prefixedIdentifier returns [TypeStatement result = new TypeStatement()]
    :   (pp=identifier { $result.setPrefix($pp.text); } ':')? np=identifier { $result.setName($np.text); }
    ;
  
typeStatement returns [TypeStatement result = new TypeStatement()]
    :   'type' (prefix=identifier ':' { $result.setPrefix($prefix.text); })? name=identifier { $result.setName($name.text); }
        (';'|('{' statementSep[result]? typeBodyStatement[$result] '}'))
    ;

typeBodyStatement[TypeStatement typeStatement]
    :   numericalRestrictions   { $typeStatement.setNumericalRestrictions($numericalRestrictions.result); }
    |   stringRestrictions      { $typeStatement.setStringRestrictions($stringRestrictions.result); }
    |   enumSpecification[$typeStatement]
    |   unionSpecification[$typeStatement]
// TBD: Support all statements
    ;

unionSpecification[TypeStatement type]
    :   (typeStatement {$type.addUnionTypeStatement($typeStatement.result); })+
    ;
    
enumSpecification[TypeStatement typeStatement]
    :   (enumStatement  {$typeStatement.addEnum($enumStatement.result); })+ // [$typeStatement])+
    ;

enumStatement returns [EnumStatement result = new EnumStatement()]
    :   'enum' string { $result.setName($string.result); }
        (';' | '{' enumBodyStatement[$result]* '}')
    ;

enumBodyStatement [EnumStatement result]
    :   statementSep[$result]
    |   'value' INTEGER_VALUE ';' {$result.setValue(Long.valueOf($INTEGER_VALUE.text));} //statementSep[$result])
    |   statusStatement { $result.setStatus($statusStatement.result); } //statementSep[$result])
    |   descriptionStatement { $result.setDescription($descriptionStatement.result); } //statementSep[$result])
    |   referenceStatement { $result.setReference($referenceStatement.result); } //statementSep[$result])
    ;   

numericalRestrictions returns [NumericalRestrictions result]
    :   rangeStatement { $result = new NumericalRestrictions(); $result.setRangeStatement($rangeStatement.result); }
    ;

rangeStatement returns [RangeStatement result]
    :   'range' string { $result = makeRangeStatement($string.result); }
        (';'|('{' restrictionBodyStatement[$result]* '}'))
    ;
    
restrictionBodyStatement[RestrictionStatement restriction]
    :   errorMessageStatement   { $restriction.setErrorMessage($errorMessageStatement.result); }
    |   errorAppTagStatement    { $restriction.setErrorAppTag($errorAppTagStatement.result); }
    |   descriptionStatement    { $restriction.setDescription($descriptionStatement.result); }
    |   referenceStatement      { $restriction.setReference($referenceStatement.result); }
    ;

errorMessageStatement returns [String result ]
    : 'error-message' string ';' { $result = $string.result; }
    ;

errorAppTagStatement returns [String result]
    : 'error-app-tag' string ';' 
    ;
    
stringRestrictions returns [StringRestrictions result]
@init { $result = new StringRestrictions(); }
    : stringRestrictionsBody[$result]+
    ;

stringRestrictionsBody[StringRestrictions stringRestrictions]
    : lengthStatement { $stringRestrictions.setLengthStatement($lengthStatement.result); }
    | patternStatement { $stringRestrictions.addPatternStatement($patternStatement.result); }
    ;
    
lengthStatement returns [LengthStatement result]
    :   'length' string { $result = makeLengthStatement($string.result); }
        (';'|('{' restrictionBodyStatement[$result]* '}'))
    ;
   
patternStatement returns [PatternStatement result]
    :   'pattern' string { $result = new PatternStatement($string.result); }
        (';'|('{' restrictionBodyStatement[$result]* '}'))
    ;

unitsStatement returns [String result]
    :   'units' string ';' { $result = $string.result; }
    ;

defaultStatement returns [String result]
    :   'default' string ';' { $result = $string.result; }
    ;

statusStatement returns [Status result]
    :   'status' statusArgument ';' { $result = $statusArgument.result; }
    ;
    
statusArgument returns [Status result]
    : 'current'     { $result = Status.CURRENT; }
    | 'obsolete'    { $result = Status.OBSOLETE; }
    | 'deprecated'  { $result = Status.DEPRECATED; }
    ;

presenceStatement returns [String result]
    : 'presence' string ';'   { $result = $string.result; }
    ;

configStatement returns [boolean result]
    : 'config' booleanConstant ';' { $result = $booleanConstant.result; }
    ;

mandatoryStatement returns [boolean result]
    : 'mandatory' booleanConstant ';' { $result = $booleanConstant.result; }
    ;

minElementsStatement returns [long result]
    : 'min-elements' INTEGER_VALUE ';' { $result = Long.valueOf($INTEGER_VALUE.text); }
    ;

maxElementsStatement returns [long result]
    : 'max-elements' INTEGER_VALUE ';' { $result = Long.valueOf($INTEGER_VALUE.text); }
    ;

keyStatement returns [String result]
    : 'key' string ';' { $result = $string.result; }
    ;
 

/*
rangeDescriptor returns [List<RangePart> result]
    : rp=rangePart { $result = new ArrayList<RangePart>(); $result.add($rp.result); } ('|' rp=rangePart { $result.add($rp.result); })*
    ;
   
rangePart returns [RangePart result]
    : start=rangeBoundary { $result = new RangePart($start.result); } ('..' end=rangeBoundary { $result.setEnd($end.result); } )?
    ;

rangeBoundary returns [RangeBoundary result]
    : MIN_KEYWORD { $result = new RangeBoundary("min"); }
    | MAX_KEYWORD { $result = new RangeBoundary("max"); }
    | INTEGER_VALUE { $result = new RangeBoundary(Long.valueOf($INTEGER_VALUE.text)); }
// FIXME: Decimal support causes problems with ranges which I need to figure out and fix
//    | DECIMAL_VALUE { $result = new RangeBoundary(new BigDecimal($DECIMAL_VALUE.text)); }
    ;

lengthDescriptor returns [List<LengthPart> result]
    : lp=lengthPart { $result = new ArrayList<LengthPart>(); $result.add($lp.result); } ('|' lp=lengthPart { $result.add($lp.result); })*
    ;

lengthPart returns [LengthPart result]
    : start=lengthBoundary { $result = new LengthPart($start.result); } ('..' end=lengthBoundary { $result.setEnd($end.result); } )?
    ;

lengthBoundary returns [LengthBoundary result]
    : MIN_KEYWORD { $result = new LengthBoundary("min"); }
    | MAX_KEYWORD { $result = new LengthBoundary("max"); }
    | INTEGER_VALUE { $result = new LengthBoundary(Long.valueOf($INTEGER_VALUE.text)); }
    ;
*/

identifierRefArg
    : (identifier ':')?identifier
    ;
    
booleanConstant returns [boolean result]
    : 'true'   { $result = true; }
    | 'false'  { $result = false; }
    ;

string returns [String result]
    : DOUBLE_QUOTED_STRING { $result = getDoubleQuotedStringContents($DOUBLE_QUOTED_STRING.text); }
    | SINGLE_QUOTED_STRING { $result = getSingleQuotedStringContents($SINGLE_QUOTED_STRING.text); }
    | UNQUOTED_STRING { $result = $UNQUOTED_STRING.text; }
    | IDENTIFIER { $result = $IDENTIFIER.text; }
    | INTEGER_VALUE { $result = $INTEGER_VALUE.text; }
    | keyword { $result = $keyword.text; }
    ;

unknownStatement returns [UnknownStatement result = new UnknownStatement()]
    :   prefix=identifier ':' { $result.setPrefix($prefix.text); } name=identifier { $result.setName($name.text); } 
        ( string {$result.setArg($string.result);} )? ';' 
    ;

statementSep[Statement statement]
    : WS 
    | unknownStatement { $statement.addUnknownStatement($unknownStatement.result); }
    ;

identifier
    : IDENTIFIER
    | keyword
    ;

keyword
    : 'anyxml'
    | 'argument'
    | 'augment'
    | 'base'
    | 'belongs-to'
    | 'bit'
    | 'case'
    | 'choice'
    | 'config'
    | 'contact'
    | 'container'
    | 'current'
    | 'default'
    | 'deprecated'
    | 'description'
    | 'deviate'
    | 'deviation'
    | 'enum'
    | 'error-app-tag'
    | 'error-message'
    | 'extension'
    | 'false'
    | 'feature'
    | 'fraction-digits'
    | 'grouping'
    | 'identify'
    | 'if-feature'
    | 'import'
    | 'include'
    | 'input'
    | 'key'
    | 'leaf'
    | 'leaf-list'
    | 'length'
    | 'list'
    | 'mandatory'
    | 'max'
    | 'max-elements'
    | 'min'
    | 'min-elements'
    | 'module'
    | 'must'
    | 'namespace'
    | 'notification'
    | 'obsolete'
    | 'ordered-by'
    | 'organization'
    | 'output'
    | 'path'
    | 'pattern'
    | 'position'
    | 'prefix'
    | 'presence'
    | 'range'
    | 'reference'
    | 'refine'
    | 'require-instance'
    | 'revision'
    | 'revision-date'
    | 'rpc'
    | 'status'
    | 'submodule'
    | 'true'
    | 'type'
    | 'typedef'
    | 'unique'
    | 'units'
    | 'uses'
    | 'value'
    | 'when'
    | 'yang-version'
    | 'yin-element'
    ;
    
//identifier returns [String result]
//    : UNQUOTED_STRING { validateIdentifier($UNQUOTED_STRING.text); $result = $UNQUOTED_STRING.text; }
//    ;

COMMENT
    :   '/*' (options {greedy=false;} : . )* '*/' { skip(); }
    ;

LINE_COMMENT
    :   '//' ~('\n'|'\r')*  ('\r\n' | '\r' | '\n') { skip(); }
    // a line comment could appear at the end of the file without CR/LF
    |   '//' ~('\n'|'\r')* { skip(); }    
    ;



//INTEGER_VALUE returns [String result]
//    : UNQUOTED_STRING { validateInteger($UNQUOTED_STRING.text); $result = $UNQUOTED_STRING.text; }
//    ;
    
//MIN_KEYWORD : 'min';
//MAX_KEYWORD : 'max';

// Need to add back in this " | '.'" but that currently causes parsing issues with range descriptors like "min..5000"
// The whole thing is consumed as a single identifier rather than "min" being a separate token.

IDENTIFIER
    : (ALPHA|'_')(ALPHA|'0'..'9'|'_'|'-'|'.')*
    ;

//INTEGER_VALUE : '-'? NON_NEGATIVE_INTEGER_VALUE;
INTEGER_VALUE : '-'? ('0'| ('1'..'9' '0'..'9'*));
//INTEGER_VALUE : '0'..'9'+;

DOUBLE_QUOTED_STRING
    :  '"' ( DOUBLE_QUOTED_ESCAPE | ('\r' | '\n' | '\t' | ' ' | '!' | '#'..'[' | ']'..'~') )* '"'
    ;

SINGLE_QUOTED_STRING
    :   '\'' ('\r'|'\n'|'\t'|' '..'&'|'('..'~')* '\''
    ;

UNQUOTED_STRING
    :  ('!'..'9'|'<'..'z'|'|'|'~')+
    ;
    
//NON_NEGATIVE_INTEGER_VALUE : '0' | ('1'..'9' '0'..'9'*);

//POSITIVE_INTEGER_VALUE : '1'..'9' '0'..'9'+;

// FIXME: Decimal support causes problems with ranges which I need to figure out and fix
//DECIMAL_VALUE : '-'? ('0'| ('1'..'9' '0'..'9'*)) '.' '0'..'9'+;

WS      :  (' '|'\t'|'\r'|'\n')+ { $channel=HIDDEN; };

fragment
ZERO_INTEGER_VALUE : '0'..'9'+;
    
fragment
ALPHA    : ('A'..'Z'|'a'..'z');

fragment
DIGIT   : '0'..'9';

fragment
DOUBLE_QUOTED_ESCAPE    :   '\\' ('t'|'n'|'r'|'"'|'\\');
