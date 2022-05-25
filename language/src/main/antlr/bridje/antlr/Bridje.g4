grammar Bridje;

NIL : 'nil' ;
BOOLEAN : 'false' | 'true' ;

BIG_DECIMAL : ([0-9]+) 'M' | ([0-9]+) '.' ([0-9]+) 'M' ;
BIG_INTEGER : ([0-9]+) 'N' ;

FLOAT : ([0-9]+) '.' ([0-9]+) ;
INT : ([0-9]+) ;

STRING : '"' ( ~'"' | '\\' '"' )* '"' ;

WHITESPACE : [ \r\n\t,] -> skip ;

LINE_COMMENT : ';' ~[\r\n]* -> skip ;

KEYWORD_DOT : ':' NAME '.' ;
KEYWORD : ':' NAME;

SYMBOL_DOT : NAME '.' ;
DOT_SYMBOL : '.' NAME ;

SYMBOL : '/' | NAME ;
NS_SYMBOL : NAME '/' NAME ;

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
  | NS_SYMBOL # NsSymbol
  | SYMBOL_DOT # SymbolDot
  | DOT_SYMBOL # DotSymbol
  | KEYWORD # Keyword
  | KEYWORD_DOT # KeywordDot
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