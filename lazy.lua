return {
  { "neovim/nvim-lspconfig", lazy = true },
  { "Olical/conjure", lazy = true },
  {
    "bridje/bridje",
    ft = "bridje",
    init = function()
      vim.filetype.add({ extension = { brj = "bridje" } })
    end,
    config = function(plugin)
      vim.opt.runtimepath:append(plugin.dir .. "/nvim")
      require("bridje").setup()
    end,
  },
}
