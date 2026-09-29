---
title: Editor setup
description: Syntax highlighting, the language server and a connected REPL, in Neovim, Emacs and VS Code.
---

Bridje's editor support comes in three parts:

- **a [tree-sitter](https://tree-sitter.github.io/) grammar**, for highlighting and structural editing;
- **a language server**, started by the Bridje Gradle plugin as `./gradlew bridjeLsp`, which reports errors as you type;
- **an [nREPL](https://nrepl.org/) server**, started as `./gradlew bridjeRepl`, which your editor connects to through the `.nrepl-port` file it writes.

The language server and REPL are run through your project's Gradle wrapper, so they always use the Bridje version your project does.
If your project doesn't have a wrapper yet, `gradle wrapper` creates one.

## Neovim

Neovim is the most complete integration today.
With [lazy.nvim](https://lazy.folke.io/):

```lua
{ "bridje/bridje" }
```

The Bridje repository carries its own lazy.nvim spec, which pulls in the rest:

- **the tree-sitter grammar**, installed through [nvim-treesitter](https://github.com/nvim-treesitter/nvim-treesitter);
- **the language server**, configured through [nvim-lspconfig](https://github.com/neovim/nvim-lspconfig), for any `.brj` file in a Gradle project;
- **a [Conjure](https://github.com/Olical/conjure) client**, so the usual Conjure mappings evaluate Bridje forms against the REPL.

Start the REPL with `./gradlew bridjeRepl` in a terminal, open a `.brj` file in the same project, and Conjure connects to it.

## Emacs

`emacs/bridje-mode.el` in the Bridje repository provides `bridje-ts-mode`, a tree-sitter mode with [lsp-mode](https://emacs-lsp.github.io/lsp-mode/) integration.
It isn't packaged yet, and expects the grammar to be built from a checkout of the repository.

## VS Code

`vscode/` in the Bridje repository is a minimal language-server client.
It isn't published to the marketplace yet, and currently expects a locally built language server.
