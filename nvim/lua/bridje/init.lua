local M = {}

-- Path to the tree-sitter parser .so file
local parser_path = vim.fn.expand("~/src/james/bridje/tree-sitter/build/lib/native/linux/x64/libtree-sitter-bridje.so")

function M.setup()
  -- Register the tree-sitter parser
  vim.treesitter.language.add("bridje", { path = parser_path })
end

return M
