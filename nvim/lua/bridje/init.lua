local M = {}

local bridje_root = vim.fn.expand("~/src/james/bridje")
local parser_path = bridje_root .. "/tree-sitter/build/lib/native/linux/x64/libtree-sitter-bridje.so"

function M.setup()
  vim.treesitter.language.add("bridje", { path = parser_path })
  M.setup_lsp()
  M.setup_conjure()
end

function M.setup_lsp()
  local lspconfig = require("lspconfig")
  local configs = require("lspconfig.configs")

  if not configs.bridje then
    configs.bridje = {
      default_config = {
        cmd = {
          "java",
          "-Dpolyglot.engine.WarnInterpreterOnly=false",
          "-jar", bridje_root .. "/lsp/build/libs/bridje-lsp.jar",
        },
        filetypes = { "bridje" },
        root_dir = lspconfig.util.root_pattern("build.gradle.kts", "settings.gradle.kts", ".git"),
      },
    }
  end

  lspconfig.bridje.setup({})
end

function M.setup_conjure()
  -- Map bridje filetype to our client (must be set before Conjure loads)
  vim.g["conjure#filetype#bridje"] = "conjure.client.bridje.stdio"

  -- Append bridje to Conjure's filetypes and re-init mappings
  local cfg = require("conjure.config")
  local filetypes = cfg["get-in"]({ "filetypes" }) or {}
  if not vim.tbl_contains(filetypes, "bridje") then
    table.insert(filetypes, "bridje")
    cfg["assoc-in"]({ "filetypes" }, filetypes)
  end
  require("conjure.mapping").init()
end

return M
