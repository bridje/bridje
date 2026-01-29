# AGENTS.md - Bridje Codebase Guide

This document provides guidance for AI coding agents working on the Bridje codebase.

## Project Overview

Bridje is a statically-typed programming language that runs on GraalVM/Truffle, inspired by Clojure, Kotlin, Haskell, and Erlang. It features LISP-like semantics with a C-style syntax.

**Tech Stack**: Kotlin 2.3.0, JVM 22, GraalVM Truffle, Gradle (Kotlin DSL)

## Project Structure

```
bridje/
├── language/          # Core Truffle-based language implementation
│   └── src/main/kotlin/brj/
│       ├── analyser/  # Semantic analysis (Analyser, Expr types)
│       ├── nodes/     # Truffle AST nodes (InvokeNode, LetNode, etc.)
│       ├── runtime/   # Runtime objects (BridjeFunction, BridjeRecord)
│       ├── builtins/  # Built-in functions
│       ├── Reader.kt, Emitter.kt, Form.kt
├── lsp/               # Language Server Protocol implementation
├── repl/              # nREPL server
├── tree-sitter/       # Tree-sitter grammar (grammar.js)
├── gradle-plugin/     # Gradle plugin for Bridje projects
├── vscode/            # VSCode extension
├── emacs/             # Emacs major mode
└── nvim/              # Neovim plugin
```

## Build Commands

```bash
./gradlew build              # Build everything
./gradlew assemble           # Build without tests
./gradlew clean build        # Clean build
```

## Test Commands

```bash
./gradlew test                                              # Run all tests
./gradlew :language:test                                    # Run module tests
./gradlew :language:test --tests "brj.DefTest"              # Single test class
./gradlew :language:test --tests "brj.DefTest.def value"    # Single test method
./gradlew :language:test --tests "brj.DefTest.*def*fn*"     # Wildcard for backtick names
./gradlew :tree-sitter:testTreeSitter                       # Tree-sitter grammar tests
./gradlew :tree-sitter:generateGrammar                      # Regenerate parser from grammar.js
./gradlew :tree-sitter:buildTreeSitter                      # Build native .so library
```

## Code Style Guidelines

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Classes/Interfaces | PascalCase | `BridjeContext`, `NsEnv` |
| Core runtime types | `Bridje` prefix | `BridjeFunction`, `BridjeRecord` |
| AST nodes | `Node` suffix | `InvokeNode`, `LetNode` |
| Expression types | `Expr` suffix | `CallExpr`, `LetExpr` |
| Form types | `Form` suffix | `ListForm`, `SymbolForm` |
| Functions | camelCase verbs | `analyseValueExpr`, `emitExpr` |
| Variables | camelCase | `fnExpr`, `bodyForms` |
| Constants | SCREAMING_SNAKE | `MAX_EXPANSION_DEPTH` |
| Type parameters | Single capitals | `<E, T>`, `<U>` |

Common abbreviations: `ctx` (context), `fn` (function), `els` (elements), `loc` (location), `ns` (namespace)

### Import Style
- Wildcard imports for same-project packages: `import brj.*`
- Wildcard imports for heavily-used external packages: `import org.eclipse.lsp4j.*`
- Group related imports together (no strict ordering enforced)
- Java stdlib imports typically last

### Type Patterns

**Sealed types for ADTs** - Use exhaustively in `when` expressions:
```kotlin
sealed interface ValueExpr : Expr
data class IntExpr(val value: Long, override val loc: SourceSection?) : ValueExpr
```

**Data classes** - Prefer for immutable value types:
```kotlin
data class Analyser(
    private val ctx: BridjeContext,
    private val locals: Map<String, LocalVar> = emptyMap(),
)
```

**Type aliases** - Use for domain-specific readability: `typealias Requires = Map<String, NsEnv>`

### Error Handling

**Custom Result type** - Railway-oriented programming with `flatMap`/`map`:
```kotlin
internal sealed class Result<out E, out T> {
    data class Ok<T>(val value: T) : Result<Nothing, T>()
    data class Err<E>(val error: E) : Result<E, Nothing>()
}
```

**Error accumulation** - Analyser collects errors rather than failing fast
**Silent exception catching** - Use underscore: `catch (_: Exception) { null }`

### Truffle/GraalVM Patterns

**Interop annotations** - Required for polyglot objects:
```kotlin
@ExportLibrary(InteropLibrary::class)
class BridjeFunction(private val callTarget: RootCallTarget) : TruffleObject {
    @ExportMessage fun isExecutable() = true
    @ExportMessage fun execute(arguments: Array<Any?>): Any? = callTarget.call(*arguments)
}
```

**Node pattern** - Base class for AST nodes:
```kotlin
@TypeSystemReference(BridjeTypes::class)
abstract class BridjeNode(private val loc: SourceSection? = null) : Node() {
    abstract fun execute(frame: VirtualFrame): Any?
}
```

### Kotlin Idioms

- **Immutable state with copy()** for data classes
- **Extension functions** for DSL-like APIs: `fun Source.readForms(): Sequence<Form>`
- **when expressions** for exhaustive pattern matching on sealed types

### Testing Patterns

Tests use JUnit 5 with backtick method names:
```kotlin
class DefTest {
    @Test
    fun `def value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: x 42
              x
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }
}
```

Test helper in `EvalTest.kt`:
```kotlin
internal inline fun <R> withContext(f: (Context) -> R): R
internal fun Context.evalBridje(src: String) = eval("bridje", src)
```

### Documentation Style
- Minimal comments - code should be self-documenting
- No KDoc - rely on descriptive naming and type signatures
- Use `TODO()` for unimplemented features
- `@Suppress("UNUSED_PARAMETER")` with implicit purpose

## Dependencies

Key dependencies (see `gradle/libs.versions.toml`):
- GraalVM Truffle API (24.2.1)
- Kotlin Coroutines (1.10.2)
- JTreeSitter (0.25.1)
- LSP4J (0.21.1)
- Clikt (5.0.3) for CLI
- JUnit Jupiter (5.9.0)

## CI/CD

GitHub Actions runs on push/PR to main:
- Uses GraalVM 22
- Runs `./gradlew build --no-daemon`
- Requires tree-sitter CLI installed

## Quick Reference

- Source extension: `.brj` for Bridje files
- License: MPL-2.0
- Main entry: `brj.MainKt`
- Language ID: `bridje`
