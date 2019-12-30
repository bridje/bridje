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

nsSym : LOWER_SYM # NsIdSym
      | UPPER_SYM # NsTypeSym;

localSym : (LOWER_SYM | '/' | '::' ) # IdSym
         | UPPER_SYM # TypeSym
         | ':' LOWER_SYM # RecordSym
         | ':' UPPER_SYM # VariantSym;

form : BOOLEAN # Boolean
     | STRING # String
     | BIG_INT # BigInt
     | INT # Int
     | BIG_FLOAT # BigFloat
     | FLOAT # Float

     | localSym # Symbol
     | nsSym '/' localSym # QSymbol

     | '(' (form | discardForm)* ')' # List
     | '[' (form | discardForm)* ']' # Vector
     | '#{' (form | discardForm)* '}' # Set
     | '{' (form | discardForm) * '}' # Record
     | '\'' form # Quote
     | '`' form # SyntaxQuote
     | '~' form # Unquote
     | '~@' form # UnquoteSplicing ;

file : (form | discardForm)* EOF;
