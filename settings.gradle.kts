plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "bridje"
include("language", "lsp", "repl", "tree-sitter", "gradle-plugin")
