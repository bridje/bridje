---
title: Coming from Java or Kotlin
description: Bridje for Java and Kotlin developers — what maps across, what doesn't, and why it's a Lisp underneath.
---

Bridje runs on the JVM, calls your Java libraries, and is built with Gradle — so a lot of the world around it will be familiar.
The language itself is a different shape: there are no classes, no statements and no operators, and — underneath the familiar-looking syntax — it's a Lisp.

This page maps what you know onto Bridje.

## Everything is an expression

There are no statements in Bridje, and no `return`.
`if`, `case`, `let` and `try` all produce values, and a function returns the value of its body:

```bridje
def: describe(n)
  if: gt(n, 0)
    "positive"
    "not positive"
```

Kotlin developers will recognise this from `if` and `when` as expressions; Bridje just applies it everywhere.

Blocks are marked by indentation rather than braces: a name followed by a colon — `def:`, `if:`, `let:` — opens one, and it runs for as long as the lines are indented under it.

## Calls, but no operators or methods

Calls look like they do in Java: `println("hi")`, `count(items)`.
What's missing is the rest of the expression syntax:

- **No operators.** `a + b` is `add(a, b)`; `a == b` is `eq(a, b)`; `a < b` is `lt(a, b)`.
  There's no precedence to learn, and nothing special about the built-in functions.
- **No methods, and no `a.b`.** Data doesn't carry behaviour: functions take data as parameters.
  Where you'd chain method calls, Bridje threads a value through functions with `->`:

  ```bridje
  // user.getAddress().getCity()
  ->: user .address .city
  ```

## Data classes become records and tags

| Java / Kotlin | Bridje |
| :-- | :-- |
| a class with fields; `data class`; `record` | a [record](/tour/records/) — or a [tag](/tour/tags-and-enums/) over one, for a named type |
| `copy(title = "x")` | `with(todo, .title "x")` |
| `sealed interface` + implementations; an `enum` | an [enum](/tour/tags-and-enums/#enums) |
| exhaustive `when` / `switch` over them | `case`, which the compiler checks is exhaustive |
| a getter | a key, which is a function: `.title(todo)` |
| an interface as a type | a record type: any value carrying the right keys fits |

A record's fields — its **keys** — are declared once, globally, with their types.
Any record carrying the right keys fits wherever those keys are asked for; there's no `implements` to write.

Values are immutable.
`with` returns a new record, much like Kotlin's `copy`.
There's no in-place mutation of Bridje values for now; where you need mutable state, a Java object — an `AtomicReference`, say — holds it.

## Nullability, as in Kotlin

Bridje's `nil` works much like Kotlin's `null`:

| Kotlin | Bridje |
| :-- | :-- |
| `String?` | `Str?` |
| non-null by default | non-null by default |
| `x?.let { ... } ?: other` | `ifLet: [y x] ... other` |
| `a?.b?.c` | `?>: a .?b .?c` |
| `x ?: default` | `orElse(x, default)` |

There's no `Optional`, and no `!!`.

## Types are inferred — all of them

Kotlin infers local variables; Bridje infers everything, parameters and return types included.
A function that reads `.title` from its parameter accepts anything with a `.title`, and the compiler checks every call.

When you want to state a type — as documentation, or to pin down an API — `decl:` declares it, and the definition is checked against it:

```bridje
decl: [a] firstOrElse([a], a) a
```

`[a]` introduces a type variable, as `<T>` does.

## Dependency injection becomes effects

Where you'd inject a `Clock` or a `Repository` through a constructor, Bridje has [effects](/tour/effects/): declared once with `defx:`, used like any other function, and provided — or replaced in a test — with `withFx:`.
There's no container, no annotations, and nothing to thread through constructors.

## Exceptions

Errors are thrown as **anomalies** — `NotFound`, `Incorrect`, `Forbidden` and so on — and caught by pattern-matching:

```bridje
try:
  java.lang.Integer/parseInt(s)
  catch: Host(e) 0
```

A Java exception arrives as a `Host` anomaly.

## Concurrency

`brj.concurrent` provides structured concurrency on virtual threads, in the spirit of Kotlin's coroutine scopes: `c/spawn` starts a task, `c/await` waits for its result, and `c/interrupt` cancels it along with the tasks it started.

## Using Java

Java classes are imported by namespace, and their members declared in Bridje so the compiler can check calls into them — see [the tour](/tour/namespaces-and-java/#java) and the [interop reference](/reference/interop/).

## Why a Lisp?

Every piece of Bridje code, underneath the sugar, is a list: `f(a, b)` is `(f a b)`, and a block is a list whose closing parenthesis is implied by indentation.
That uniformity is the point — any sub-expression can be pulled out, moved or wrapped without restructuring the code around it, and programs can generate code as easily as data, with [macros](/reference/macros/).
The syntax exists so that you don't have to learn a Lisp to get those benefits; [the rationale](/about/rationale/) says more.
