grammar Form;

WHITESPACE: [\p{White_Space},] -> skip;

BOOLEAN : 'true' | 'false';

fragment SYMBOL_HEAD : ~( [0-9]
                          | '^' | '`' | '\'' | '"' | '#' | '~' | '@' | ':' | '/' | '%'
                          | '(' | ')' | '[' | ']' | '{' | '}'
                          | [\p{White_Space},]
                        ) ;

fragment SYMBOL_REST : SYMBOL_HEAD | [0-9] ;
fragment SYMBOL_PART : SYMBOL_HEAD SYMBOL_REST* ;

SYMBOL : ':'? (SYMBOL_PART '/')? SYMBOL_PART | '/' | '::' | '.' ;
QSYMBOL : ':'? SYMBOL_PART '/' SYMBOL_PART ;

INT: '-'? [0-9]+;
BIG_INT: INT [nN];

FLOAT: '-'? [0-9]+ ('.' [0-9]+)?;
BIG_FLOAT: FLOAT [mM];

STRING : '"' ( '\\"' | ~'"' )*? '"';

LINE_COMMENT: ';' (.*?) ([\n\r]+ | EOF) -> skip;

form : BOOLEAN # Boolean
     | STRING # String
     | SYMBOL # Symbol
     | QSYMBOL # QSymbol
     | BIG_INT # BigInt
     | INT # Int
     | BIG_FLOAT # BigFloat
     | FLOAT # Float
     | '(' form* ')' # List
     | '[' form* ']' # Vector
     | '#{' form* '}' # Set
     | '{' form* '}' # Record
     | '\'' form # Quote
     | '~' form # Unquote
     | '~@' form # UnquoteSplicing ;

file : form* EOF;
