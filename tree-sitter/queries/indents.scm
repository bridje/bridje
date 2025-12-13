; lists indent by shiftwidth (2)
(list) @indent.begin
(call) @indent.begin
(method_call) @indent.begin
(block_call) @indent.begin
(block_body) @indent.begin

; vectors/maps/sets align after the bracket (1 space)
(vector) @indent.align
(map) @indent.align
(set) @indent.align

")" @indent.end
"]" @indent.end @indent.branch
"}" @indent.end @indent.branch
