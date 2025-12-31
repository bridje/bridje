local function register_parser()
  local ok, parsers = pcall(require, "nvim-treesitter.parsers")
  if ok then
    parsers.bridje = {
      install_info = {
        url = "https://github.com/bridje/bridje",
        location = "tree-sitter",
        generate = true,
        generate_from_json = false,
        queries = "tree-sitter/queries",
      },
    }
  end
end

return {
  { "neovim/nvim-lspconfig", lazy = true },
  { "Olical/conjure", lazy = true },
  {
    "bridje/bridje",
    ft = "bridje",
    init = function()
      vim.filetype.add({ extension = { brj = "bridje" } })
      register_parser()
      -- Re-register after TSUpdate (install reloads the parsers table)
      vim.api.nvim_create_autocmd("User", {
        pattern = "TSUpdate",
        callback = register_parser,
      })
    end,
    build = function()
      register_parser()
      require("nvim-treesitter").install("bridje")
    end,
    config = function(plugin)
      vim.opt.runtimepath:append(plugin.dir .. "/nvim")
      require("bridje").setup()
    end,
  },
}
