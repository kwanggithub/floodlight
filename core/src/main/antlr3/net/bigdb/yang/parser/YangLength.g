grammar YangLength;

options {
    language=Java;
    backtrack=true;
    memoize=true;
    k=10;
}

@header {
package net.bigdb.yang.parser;

import net.bigdb.yang.*;
}

@lexer::header {
package net.bigdb.yang.parser;
}

@members {
}

lengthDescriptor returns [List<LengthPart> result]
    : lp=lengthPart { $result = new ArrayList<LengthPart>(); $result.add($lp.result); } ('|' lp=lengthPart { $result.add($lp.result); })*
    ;

lengthPart returns [LengthPart result]
    : start=lengthBoundary { $result = new LengthPart($start.result); } ('..' end=lengthBoundary { $result.setEnd($end.result); } )?
    ;

lengthBoundary returns [LengthBoundary result]
    : 'min' { $result = new LengthBoundary("min"); }
    | 'max' { $result = new LengthBoundary("max"); }
    | INTEGER_VALUE { $result = new LengthBoundary(Long.valueOf($INTEGER_VALUE.text), $INTEGER_VALUE.text); }
    ;

INTEGER_VALUE : ('0'| ('1'..'9' '0'..'9'*));

WS      :  (' '|'\t'|'\r'|'\n')+ { $channel=HIDDEN; };
