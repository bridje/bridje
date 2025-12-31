local M = {}

function M.setup()
  M.setup_lsp()
  M.setup_conjure()
end

function M.setup_lsp()
  local lspconfig = require("lspconfig")
  local configs = require("lspconfig.configs")

  if not configs.bridje then
    configs.bridje = {
      default_config = {
        cmd = { "./gradlew", "--quiet", "--console=plain", ":bridjeLsp" },
        filetypes = { "bridje" },
        root_dir = lspconfig.util.root_pattern("build.gradle.kts", "settings.gradle.kts", ".git"),
      },
    }
  end

  lspconfig.bridje.setup({})
end

function M.setup_conjure()
  -- Map bridje filetype to our nREPL client
  vim.g["conjure#filetype#bridje"] = "conjure.client.bridje.nrepl"

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
