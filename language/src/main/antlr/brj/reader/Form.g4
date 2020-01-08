grammar Form;

@header {
package brj.reader;
}

WHITESPACE: [\p{White_Space},] -> skip;

BOOLEAN : 'true' | 'false';

INT: '-'? [0-9]+;
BIG_INT: INT 'N';

FLOAT: '-'? [0-9]+ ('.' [0-9]+)?;
BIG_FLOAT: FLOAT 'M';

STRING : '"' ( '\\"' | ~'"' )*? '"';

fragment SYMBOL_HEAD : ~( [0-9]
                          | '`' | '\'' | '"' | '~' | ':' | '/' | ';'
                          | '(' | ')' | '[' | ']' | '{' | '}'
                          | [\p{White_Space}\p{Upper},]
                        ) ;

fragment SYMBOL_REST : SYMBOL_HEAD | [0-9\p{Upper}] ;

LOWER_SYM : SYMBOL_HEAD SYMBOL_REST*;
UPPER_SYM : [\p{Upper}] SYMBOL_REST*;

LINE_COMMENT: ';' (.*?) ([\n\r]+ | EOF) -> skip;

discardForm : '(#_' (form | discardForm)* ')';

sym : (LOWER_SYM | '/' | '::' ) # IdSym
    | UPPER_SYM # TypeSym
    | ':' LOWER_SYM # RecordSym
    | ':' UPPER_SYM # VariantSym;

nsSym : LOWER_SYM # NsIdSym
      | UPPER_SYM # NsTypeSym;

qsym : nsSym '/' (LOWER_SYM | '/' | '::' ) # QIdSym
     | nsSym '/' UPPER_SYM # QTypeSym
     | ':' nsSym '/' LOWER_SYM # QRecordSym
     | ':' nsSym '/' UPPER_SYM # QVariantSym;


form : BOOLEAN # Boolean
     | STRING # String
     | BIG_INT # BigInt
     | INT # Int
     | BIG_FLOAT # BigFloat
     | FLOAT # Float

     | qsym # QSymbol
     | sym # Symbol

     | '(' (form | discardForm)* ')' # List
     | '[' (form | discardForm)* ']' # Vector
     | '#{' (form | discardForm)* '}' # Set
     | '{' (form | discardForm) * '}' # Record
     | '\'' form # Quote
     | '`' form # SyntaxQuote
     | '~' form # Unquote
     | '~@' form # UnquoteSplicing ;

file : (form | discardForm)* EOF;
