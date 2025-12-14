; numbers
(int) @number
(float) @number
(bigint) @number
(bigdec) @number

; string
(string) @string

; comment
(comment) @comment

; symbols - general fallback
(symbol) @variable
(keyword) @string.special.symbol
(dot_symbol) @property

; call: highlight the symbol as function
(call
  (symbol) @function)

; block_call: highlight the symbol as function
(block_call
  (symbol) @function)

; method_call: highlight the dot_symbol as method
(method_call
  (dot_symbol) @function.method)

; field_access: highlight the dot_symbol as property
(field_access
  (dot_symbol) @property)

; block_call: highlight the symbol as keyword if it's a special form (higher priority)
(block_call
  (symbol) @keyword
  (#any-of? @keyword "def" "deftag" "let" "fn" "if" "case" "ns" "do" "loop" "recur" "try" "catch" "finally" "throw" "quote" "var" "import" "require"))

; call: highlight the symbol as keyword if it's a special form
(call
  (symbol) @keyword
  (#any-of? @keyword "def" "deftag" "let" "fn" "if" "case" "ns" "do" "loop" "recur" "try" "catch" "finally" "throw" "quote" "var" "import" "require"))

; fallback for standalone symbols that are keywords
((symbol) @keyword
  (#any-of? @keyword "def" "deftag" "let" "fn" "if" "case" "ns" "do" "loop" "recur" "try" "catch" "finally" "throw" "quote" "var" "import" "require"))

; punctuation
":" @punctuation.delimiter
"(" @punctuation.bracket
")" @punctuation.bracket
"[" @punctuation.bracket
"]" @punctuation.bracket
"{" @punctuation.bracket
"}" @punctuation.bracket
"#{" @punctuation.bracket
