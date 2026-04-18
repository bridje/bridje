; numbers
(int) @number
(float) @number
(bigint) @number
(bigdec) @number

; string
(string) @string

; comment
(comment) @comment

; keywords
(keyword) @string.special.symbol

; symbols - general fallback
(symbol) @variable

; call: highlight the symbol as function
(call
  (symbol) @function)

; record_sugar: highlight the symbol as function (constructor)
(record_sugar
  (symbol) @function)

; block_call: highlight the symbol as function
(block_call
  (symbol) @function)

; block_call: highlight the symbol as keyword if it's a special form (higher priority)
(block_call
  (symbol) @keyword
  (#any-of? @keyword "def" "tag" "let" "fn" "if" "case" "ns" "do" "loop" "recur" "try" "catch" "finally" "throw" "quote" "var" "import" "require"))

; call: highlight the symbol as keyword if it's a special form
(call
  (symbol) @keyword
  (#any-of? @keyword "def" "tag" "let" "fn" "if" "case" "ns" "do" "loop" "recur" "try" "catch" "finally" "throw" "quote" "var" "import" "require"))

; fallback for standalone symbols that are keywords
((symbol) @keyword
  (#any-of? @keyword "def" "tag" "let" "fn" "if" "case" "ns" "do" "loop" "recur" "try" "catch" "finally" "throw" "quote" "var" "import" "require"))

; quote
(quote "'" @keyword)

; syntax-quote
(syntax_quote "`" @keyword)

; metadata
(metadata (keyword) @attribute)

; punctuation
":" @punctuation.delimiter
"(" @punctuation.bracket
")" @punctuation.bracket
"[" @punctuation.bracket
"]" @punctuation.bracket
"{" @punctuation.bracket
"}" @punctuation.bracket
"#{" @punctuation.bracket
