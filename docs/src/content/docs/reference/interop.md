---
title: Host interop
description: Importing Java classes, declaring and calling their members, other GraalVM languages, and how Bridje values look from outside.
---

## Importing

`import:` in the [ns form](/reference/namespaces/#the-ns-form) gives a host class an alias:

```bridje
ns: example
  import:
    java.time:
      as(Instant, I)
    java.util:
      ArrayList
```

The alias names both a **type** — `I` in a declaration — and a **namespace** of the class's members.

A class can also be named in full without importing it: `java.lang.Math/abs(-3)`.

## Declaring members

A member's declaration gives the checker its type; Bridje trusts it.

`decl: Alias/method(P1, P2) R`
: A static method.

`decl: Alias/.method(P1, P2) R`
: An instance method. It's called with the receiver as its first argument, so its type is `Fn([Alias, P1, P2] R)`.

`decl: Alias/FIELD T`
: A static field.

`decl: Alias/.field T`
: An instance field.

`decl: [a] Alias/new() Alias(a)`
: A constructor, with type parameters.

```bridje
decl: I/now() I
decl: I/parse(Str) I
decl: I/.toEpochMilli() Int
decl: I/.isAfter(I) Bool

decl: [a] ArrayList/new() ArrayList(a)
decl: [a] ArrayList/.add(a) Bool
```

Host type arguments are invariant; two host types join to their nearest common superclass.

## Calling

`Alias/method(a, b)`
: Calls a static method.
  An undeclared static method can still be called, untyped.

`Alias/.method(receiver, a, b)`
: Calls an instance method, or reads an instance field. The member must be declared.

`Alias/FIELD`
: Reads a declared static field.

`Alias/new()`, `Alias(a, b)`
: Constructs an instance.

A host member is spelled exactly as the host spells it: `I/.toEpochMilli`, `URI/.toURI`.

## Exceptions

A host exception is caught as the `Host` anomaly:

```bridje
try:
  java.lang.Integer/parseInt(s)
  catch: Host(e) 0
```

## Collections

Bridje vectors and sets are `Iterable`, and so are Java `Iterable`s: `mapv`, `filterv` and `reduce` work over a Java `List` directly.

## Other GraalVM languages

`lang(language, Type, code)`
: Evaluates `code` in another installed GraalVM language, taking its result to be a `Type`:

  ```bridje
  lang("js", Int, "6 * 7")
  ```

  `language` and `code` must be string literals, and the language must be on the classpath alongside Bridje.

## Bridje values from outside

Bridje values are [polyglot](https://www.graalvm.org/latest/reference-manual/polyglot-programming/) values:

- **functions** are executable;
- **vectors** have array elements;
- **records**, and **tagged values**, have members named after their keys: `getMember("name")`;
- **a namespace**, as returned by evaluating a file, has members named after its definitions.
