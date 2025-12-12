(int) @number
(float) @number
(bigint) @number
(bigdec) @number
(string) @string

((symbol) @keyword
  (#any-of? @keyword "def" "let" "fn" "if" "ns"))

(symbol) @variable
(keyword) @constant
(dot_symbol) @property

(comment) @comment

"(" @punctuation.bracket
")" @punctuation.bracket
"[" @punctuation.bracket
"]" @punctuation.bracket
"{" @punctuation.bracket
"}" @punctuation.bracket
"#{" @punctuation.bracket
