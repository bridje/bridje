plugins {
    base
}

tasks.register<Exec>("generateGrammar") {
    group = "build"
    description = "Generate Tree-sitter parser from grammar.js"
    inputs.file("grammar.js")
    outputs.files(
        "src/parser.c",
        "src/node-types.json"
    )
    workingDir = file(".")
    commandLine("tree-sitter", "generate")
}

tasks.register<Exec>("buildTreeSitter") {
    group = "build"
    dependsOn("generateGrammar")

    val output = file("${layout.buildDirectory.get()}/lib/native/linux/x64/libtree_sitter_bridje.so")

    inputs.files(fileTree("src") {
        include("*.c")
    })

    outputs.file(output)

    commandLine = listOf(
        "clang",
        "-fPIC", "-shared",
        "-I", "src/tree_sitter",
        "-o", output.absolutePath,
        "src/parser.c"
    )
}


tasks.named("assemble") {
    group = "build"
    dependsOn("buildTreeSitter")
}

tasks.named<Delete>("clean") {
    delete(fileTree("src") {
        include("parser.c", "node-types.json", "scanner.c", "grammar.json")
    })
}

