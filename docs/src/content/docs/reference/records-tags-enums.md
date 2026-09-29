---
title: Records, tags and enums
description: Keys, record literals and operations, tags and their construction, enums, and pattern matching.
---

## Keys

Every key is declared once, at the top level, with the type of the value it holds — before any tag, record literal or read uses it:

`decl: .name Str`
: Declares the key `.name`, holding a `Str`.

`decl: {.name Str, .age Int}`
: Declares several keys at once.

`decl: [a] .value a`
: Declares a key whose type has variables.
  Each record holds the key's value at an instance of its own: `{.value 1}` is a `{.value(Int)}`, and `.value` read off it is an `Int`.
  The key's variables are its arguments, in the order they're declared.

`decl: .name`
: Without a type, is an error: `needs a type`.

A key belongs to the namespace that declares it.
Within that namespace it's written `.name`; elsewhere, qualified by a require alias, `m/.name`.

Each key defines two functions:

`.name(r)`
: The value of `.name` in `r`.
  The type checker demands that `r` carries `.name`: `{.age} lacks {.name}`.

`.?name(r)`
: The value of `.name` in `r`, or `nil` if it has none; accepts a record that may carry `.name`.
  Its type is the key's, made nullable.
  For a key with type variables, it's read at the instance the record's type holds it at; on a record whose type doesn't mention the key at all, it's an error — `may carry .value at any type` — since there's no instance to read it at.

## Records

`{.k1 v1, .k2 v2, …}`
: A record literal.
  Each value is checked against its key's type: `{.name 42}` is an error, `Int is not a subtype of Str`.

`with(r, .k1 v1, .k2 v2, …)`
: A copy of `r`, with each key set — added, or replaced.
  On a record parameter `r`, the result is `r`'s type, known to carry `.k1` and `.k2` as well.

:::note
There's no in-place mutation for now: `set` has been taken out while mutability is rethought, and is likely to return.
Meanwhile, mutable state lives in host objects, such as a `java.util.concurrent.atomic.AtomicReference`.
:::

A record's type is the set of keys it's known to carry; see [type syntax](/reference/types/#type-syntax).

A function whose last parameter is a record can be called without it, and receives an empty record — the trailing-options convention, read with `.?opt`.

## Tags

`tag: Name{.k1, .k2}`
: Declares the tag `Name`, over a record carrying `.k1` and `.k2`.
  The keys must already be declared: a tag over an undeclared key is `.k1 has no declared type: decl: .k1 <type>`.

`tag: Name`
: Declares a nullary tag: `Name` is a singleton value.

`tag: [a] Name{.k(a)}`
: Declares a tag with type parameters; `.k(a)` instantiates `.k`'s type variables at the tag's.
  A key declared with type variables must be given them: `tag: Box{.value}` is an error.
  A tag's arguments vary as the keys they instantiate do — covariant under `decl: [a] .value a`, contravariant under `decl: [a] .h Fn([a] Bool)`.

  ```bridje
  decl: [a] .value a

  tag: [a] Box{.value(a)}
  ```

  `Box{.value "s"}` is a `Box(Str)`.

A tag is distinct from every other tag, including one with the same keys.
Tag names are capitalised.

### Constructing

`Name{.k1 v1, .k2 v2}`
: A tagged value, from a record literal.
  In the literal, a bare key is looked up among the tag's own keys first — `other/Name{.k1 v}` needs no `other/.k1`.

`Name(r)`
: A tagged value, from a record held elsewhere.

`Name`
: A nullary tag's value.

The record must carry the tag's keys — `Pair{.fst 1}` is `{.fst} lacks {.snd}` — and may carry more.
A tag takes exactly one argument: `Name(a, b)` is an error.

### Reading

A tagged value is a record carrying its tag's keys: `.k1(v)`, `.?k3(v)`, and it passes wherever `{.k1}` is demanded.
`with` works on it as on any record, and keeps its tag: `with(milk, .priority 1)` is still a `Todo`.
The exception is a key with type variables: giving it a new instance would change the tag's arguments, so `with(Box{.value "s"}, .value 1)` is a plain `{.value(Int)}`.

## Enums

`enum: Name` followed by `tag:` forms
: Declares an enum over a fixed set of tags:

  ```bridje
  decl: [a] .value a
  decl: [e] .error e

  enum: Result(a, e)
    tag: Ok{.value(a)}
    tag: Err{.error(e)}
  ```

  `enum: Name(a, b)` gives it type parameters, which its tags share.

Each variant is a tag in the declaring namespace, and a subtype of its enum.
`Ok{.value 1}` is an `Ok(Int, e)`; a value that may be an `Ok` or an `Err` is a `Result(a, e)`.
Enum names are capitalised.

## Pattern matching

`case:` and `catch:` branches match tags:

`Name`
: A value with the tag `Name`, whatever its record.

`Name(r)`
: A value with the tag `Name`, binding the tagged value itself to `r` — so `with(r, …)` is still a `Name`.

`Name{k1, k2}`
: A value with the tag `Name`, binding `k1` to its `.k1` and `k2` to its `.k2`.
  The keys are looked up among the tag's own first.
  A pattern checks the tag alone: the keys a destructuring reads must be present when it matches.

`x` (lowercase)
: Any value, nil included, bound to `x`.
  In an enum `case`, its type is narrowed to the variants not yet matched.

`m/Name`, `m/Name(r)`, `m/Name{k}`
: A tag from another namespace.

A `case` over an enum must name every variant, or have a default or catch-all.
