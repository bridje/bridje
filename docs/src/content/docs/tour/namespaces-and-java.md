---
title: Namespaces and Java
description: Splitting a program into namespaces, and calling Java libraries.
---

## Namespaces

Bridje code is split into **namespaces**, one per file.
A namespace's name mirrors its path: `todo.model` lives in `src/main/bridje/todo/model.brj`, and starts with an `ns:` form:

```bridje
ns: todo.model

decl: .doneBy Str

enum: Status
  tag: Open
  tag: Done{.doneBy}

decl: isDone(Status) Bool
def: isDone(status)
  case: status
    Done true
    false
```

Another namespace uses it by **requiring** it, under an alias:

```bridje
ns: todo.app
  require:
    todo:
      as(model, m)

def: main(args)
  println(m/isDone(m/Done{.doneBy "me"}))
```

The `require:` block groups namespaces by their prefix: under `todo:`, `as(model, m)` means "`todo.model`, known here as `m`".
Everything `todo.model` defines is then available through the alias — `m/isDone`, `m/Done`, and for keys, `m/.doneBy`.
If you don't need a different alias, the bare name will do: `require: brj: test` gives you `brj.test` as `test`.

Within a namespace, definitions are read top to bottom, and a definition can only use what's defined above it.
So a Bridje file reads from the bottom up: small helpers first, the functions that use them after.

## Java

Bridje runs on the JVM, and every Java library is available to it.
**Importing** a class gives it an alias, much as requiring a namespace does:

```bridje
ns: todo.app
  import:
    java.time:
      as(Instant, Instant)
```

Static methods are called through the alias:

```bridje
Instant/now()
```

For Bridje to type-check calls into Java, you declare the members you use, in Bridje's own syntax:

```bridje
decl: Instant/now() Instant
decl: Instant/.toEpochMilli() Int
```

`Instant/now()` is a static method taking nothing and returning an `Instant`.
`Instant/.toEpochMilli()` — with the dot — is an instance method, so it's called with the instance as its first argument:

```bridje
Instant/.toEpochMilli(Instant/now())
// => 1790530653092
```

The declarations are yours to get right: Bridje trusts them.
Some of the standard library, like `brj.time`, is exactly this — Java classes with Bridje declarations over the top.

## That's the tour

You've now seen most of what Bridje looks like: expressions and blocks, functions, records, tags and enums, effects, namespaces and Java.

From here:

- the [reference](/reference/syntax/) covers every form in detail;
- [About Bridje](/about/rationale/) explains the thinking behind the design;
- and [idioms](/about/idioms/) is a short guide to writing Bridje the way it's meant to be written.
