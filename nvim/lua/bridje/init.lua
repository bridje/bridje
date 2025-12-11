local M = {}

local bridje_root = vim.fn.expand("~/src/james/bridje")
local parser_path = bridje_root .. "/tree-sitter/build/lib/native/linux/x64/libtree-sitter-bridje.so"

function M.setup()
  vim.treesitter.language.add("bridje", { path = parser_path })
  M.setup_lsp()
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

function M.eval_buffer()
  local bufnr = vim.api.nvim_get_current_buf()
  local uri = vim.uri_from_bufnr(bufnr)
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local code = table.concat(lines, "\n")

  local clients = vim.lsp.get_clients({ bufnr = bufnr, name = "bridje" })
  if #clients == 0 then
    vim.notify("Bridje LSP not attached", vim.log.levels.ERROR)
    return
  end

  local client = clients[1]
  client:request("workspace/executeCommand", {
    command = "bridje/eval",
    arguments = { { uri = uri, code = code } },
  }, function(err, result)
    if err then
      vim.notify("Eval error: " .. vim.inspect(err), vim.log.levels.ERROR)
    else
      vim.notify("=> " .. tostring(result), vim.log.levels.INFO)
    end
  end, bufnr)
end

function M.eval_form()
  local bufnr = vim.api.nvim_get_current_buf()
  local uri = vim.uri_from_bufnr(bufnr)

  local node = vim.treesitter.get_node()
  if not node then
    vim.notify("No node at cursor", vim.log.levels.WARN)
    return
  end

  while node:parent() and node:parent():type() ~= "source_file" do
    node = node:parent()
  end

  local start_row, start_col, end_row, end_col = node:range()
  local lines = vim.api.nvim_buf_get_text(bufnr, start_row, start_col, end_row, end_col, {})
  local code = table.concat(lines, "\n")

  local clients = vim.lsp.get_clients({ bufnr = bufnr, name = "bridje" })
  if #clients == 0 then
    vim.notify("Bridje LSP not attached", vim.log.levels.ERROR)
    return
  end

  local client = clients[1]
  client:request("workspace/executeCommand", {
    command = "bridje/eval",
    arguments = { { uri = uri, code = code } },
  }, function(err, result)
    if err then
      vim.notify("Eval error: " .. vim.inspect(err), vim.log.levels.ERROR)
    else
      vim.notify("=> " .. tostring(result), vim.log.levels.INFO)
    end
  end, bufnr)
end

vim.api.nvim_create_autocmd("FileType", {
  pattern = "bridje",
  callback = function(args)
    local opts = { buffer = args.buf }
    vim.keymap.set("n", "<localleader>eb", M.eval_buffer, vim.tbl_extend("force", opts, { desc = "Eval buffer" }))
    vim.keymap.set("n", "<localleader>er", M.eval_form, vim.tbl_extend("force", opts, { desc = "Eval root form" }))
  end,
})

return M
