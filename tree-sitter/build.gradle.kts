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

    val output = file("${layout.buildDirectory.get()}/lib/native/linux/x64/libtree-sitter-bridje.so")

    inputs.files(fileTree("src") {
        include("*.c")
    })

    outputs.file(output)

    commandLine = listOf(
        "clang",
        "-fPIC", "-shared",
        "-I", "src",
        "-o", output.absolutePath,
        "src/parser.c",
        "src/scanner.c"
    )
}

tasks.register<Copy>("copyQueries") {
    group = "build"
    description = "Copy Tree-sitter queries to the build directory"
    from("queries")
    into("${layout.buildDirectory.get()}/lib/native/linux/x64/queries/bridje")
    include("*.scm")
}

tasks.register<Exec>("testTreeSitter") {
    group = "verification"
    description = "Run Tree-sitter grammar tests"
    dependsOn("generateGrammar")

    inputs.files(fileTree("test/corpus") {
        include("*.txt")
    })
    inputs.file("grammar.js")
    inputs.file("src/scanner.c")

    workingDir = file(".")
    commandLine("tree-sitter", "test")
}

tasks.named("assemble") {
    group = "build"
    dependsOn("buildTreeSitter", "copyQueries")
}

tasks.named("check") {
    dependsOn("testTreeSitter")
}

tasks.named<Delete>("clean") {
    delete(fileTree("src") {
        include("parser.c", "node-types.json", "grammar.json")
    })
}

