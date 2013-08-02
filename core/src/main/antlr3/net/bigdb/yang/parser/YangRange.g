grammar YangRange;

options {
    language=Java;
    backtrack=true;
    memoize=true;
    k=10;
}

@header {
package net.bigdb.yang.parser;

import java.math.BigDecimal;
import net.bigdb.yang.*;
}

@lexer::header {
package net.bigdb.yang.parser;
}

@members {
}

rangeDescriptor returns [List<RangePart> result]
    : rp=rangePart { $result = new ArrayList<RangePart>(); $result.add($rp.result); } ('|' rp=rangePart { $result.add($rp.result); })*
    ;
   
rangePart returns [RangePart result]
    : start=rangeBoundary { $result = new RangePart($start.result); } ('..' end=rangeBoundary { $result.setEnd($end.result); } )?
    ;

rangeBoundary returns [RangeBoundary result]
    : 'min' { $result = new RangeBoundary("min"); }
    | 'max' { $result = new RangeBoundary("max"); }
    | INTEGER_VALUE { $result = new RangeBoundary(Long.valueOf($INTEGER_VALUE.text), $INTEGER_VALUE.text); }
// FIXME: Decimal support causes problems with ranges which I need to figure out and fix
//    | DECIMAL_VALUE { $result = new RangeBoundary(new BigDecimal($DECIMAL_VALUE.text), $INTEGER_VALUE.text); }
    ;

INTEGER_VALUE : ('0'| ('1'..'9' '0'..'9'*));

WS      :  (' '|'\t'|'\r'|'\n')+ { $channel=HIDDEN; };
