grammar Bridje;

NIL : 'nil' ;
BOOLEAN : 'false' | 'true' ;

BIG_DECIMAL : [0-9]+ 'M' | [0-9]+ '.' [0-9]+ 'M' ;
BIG_INTEGER : [0-9]+ 'N' ;

FLOAT : [0-9]+ '.' [0-9]+ ;
INT : [0-9]+ ;

STRING : '"' ( ~'"' | '\\' '"' )* '"' ;

WHITESPACE : [ \r\n\t,] -> skip ;

LINE_COMMENT : ';' ~[\r\n]* -> skip ;

KEYWORD_DOT : ':' NAME '.' ;
QKEYWORD_DOT : ':' NAME '/' NAME '.';
KEYWORD : ':' NAME;
QKEYWORD : ':' NAME '/' NAME;

SYMBOL_DOT : NAME '.' ;
DOT_SYMBOL : '.' NAME ;

SYMBOL : '/' | NAME ;

QSYMBOL_DOT : NAME '/' NAME '.';
DOT_QSYMBOL : NAME '/.' NAME;

QSYMBOL : NAME '/' NAME ;

fragment
NAME: SYMBOL_HEAD SYMBOL_REST* ;

fragment
SYMBOL_HEAD
    : ~('0' .. '9'
        | '.' | '^' | '`' | '\'' | '"' | '#' | '~' | '@' | ':' | '/' | '%' | '(' | ')' | '[' | ']' | '{' | '}'
        | [ \r\n\t,]
        )
    ;

fragment
SYMBOL_REST
    : SYMBOL_HEAD
    | '0'..'9'
    | '.'
    ;

discard : '#_' discard* form ;

form
  : NIL # Nil
  | BOOLEAN # Bool
  | FLOAT # Float
  | INT # Int
  | STRING # String
  | BIG_DECIMAL # BigDecimal
  | BIG_INTEGER # BigInteger
  | SYMBOL # Symbol
  | QSYMBOL # QSymbol
  | SYMBOL_DOT # SymbolDot
  | DOT_SYMBOL # DotSymbol
  | QSYMBOL_DOT # QSymbolDot
  | DOT_QSYMBOL # DotQSymbol
  | KEYWORD # Keyword
  | KEYWORD_DOT # KeywordDot
  | QKEYWORD # QKeyword
  | QKEYWORD_DOT # QKeywordDot
  | '(' (discard | form)* ')' # List
  | '[' (discard | form)* ']' # Vector
  | '{' (discard | form)* '}' # Record
  | '#{' (discard | form)* '}' # Set
  | '\'' form # Quote
  | '~' form # Unquote
  | '~@' form # UnquoteSplicing
  ;

file
  : (discard | form)* EOF;