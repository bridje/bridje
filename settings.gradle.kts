plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bridje"
include("language", "lsp", "repl", "tree-sitter", "gradle-plugin")
