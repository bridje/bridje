---
title: Types
description: Type declarations, type syntax, subtyping, joins, nullability, and how types are displayed.
---

Types are inferred.
A declaration is optional; when given, the definition is checked against it, and it becomes the type other code sees.
[The type system](/about/types/) explains the design.

## Declarations

`decl: name Type`
: Declares the type of the value `name`.

`decl: name(P1, P2, …) R`
: Declares a function: its parameter types, then its return type.

`decl: [a, b] name(…) R`
: Declares type variables, in a leading vector, for use in the rest of the declaration.

```bridje
decl: x Int
decl: greet(Str) Str
decl: callback Fn([Int, Str] Bool)
decl: [a] identity(a) a
decl: [a, b] mapv(Iterable(a), Fn([a] b)) [b]
```

A declared type variable is rigid: the definition must be at least as general as the declaration.
A declaration can narrow an inferred type, but never widen it — `declared type … is more general than the definition`.

The same syntax declares [keys](/reference/records-tags-enums/#keys), [host members](/reference/interop/#declaring-members) and [effects](/reference/effects/).

## Type syntax

`Int`, `Double`, `Str`, `Bool`
: The scalar types.

`BigInt`, `BigDec`
: Arbitrary-precision numbers.

`Bytes`
: A byte array. See [`brj.bytes`](/reference/stdlib/#brjbytes).

`Form`
: A form, as read or quoted. See [macros](/reference/macros/).

`Nothing`
: The bottom type, with no values: the return type of a function that never returns, like `throw`.

`Nothing?`
: The type of `nil`.

`T?`
: `T`, or `nil`.

`[T]`
: A vector of `T`.

`#{T}`
: A set of `T`.

`Fn([P1, P2] R)`
: A function value.

`Iterable(T)`, `Iterator(T)`
: The iteration protocol.
  Vectors and sets are `Iterable`, as are `java.lang.Iterable`s; `java.util.Iterator`s are `Iterator`s.

`{.k1, .k2}`
: A record carrying at least `.k1` and `.k2`.
  The value types come from the keys' declarations.
  A record type is open: the value may carry keys it doesn't mention.

`{.k1, .?k2}`
: As above, and perhaps `.k2`, at its declared type.

`{.value(Int)}`
: A record carrying `.value`, a key declared with type variables, at an instance: here, holding an `Int`.

`{.k & a}`
: A value of the type variable `a`, known to carry `.k` as well.

`Tag`, `Tag(T1, T2)`
: A tag or an enum, with its type arguments.
  Named without them, it has fresh ones, which the declaration quantifies: `decl: size(Maybe) Int` is `[a] Fn([Maybe(a)] Int)`.

`Tag{.k}`
: A `Tag` whose record is known to carry `.k` as well as the tag's own keys.

`Alias`, `Alias(T)`
: An imported host class, with its type arguments.

`a`
: A type variable, declared in the leading vector.

## Subtyping

`A ≤ B` means an `A` is accepted wherever a `B` is expected.

| Rule | |
| :-- | :-- |
| `Nothing ≤ T` | for every `T` |
| `nil ≤ T?` | `nil` is nullable, and nothing else is |
| `T ≤ T?` | |
| `{K1} ≤ {K2}` | when `K1` carries every key `K2` demands, at an instance below `K2`'s — more keys is more specific |
| `{K1} ≤ {K2}`, `K1` closed | as above, and a key `K2` names as `.?k` may be missing from `K1` |
| `Tag{K1} ≤ {K2}` | a record is a tag with no name |
| `Tag{K1} ≤ Enum{K2}` | a tag is a subtype of its enum |
| `Tag(A) ≤ Tag(B)` | as the keys `A` instantiates vary |
| `[a] ≤ Iterable(a)`, `#{a} ≤ Iterable(a)` | |
| `{K & a} ≤ a` | |

A record literal is **closed** — `{.a 1}` carries `.a` and nothing else — while a record type form is **open**.
A closed record is a subtype of the open one with the same keys, but not the other way round; rendered types don't show which a record is.

Functions are contravariant in their parameters and covariant in their result.
Vectors and sets are covariant, a tag's and an enum's arguments vary as the keys they instantiate do, and host type arguments are invariant.
An argument no key uses, as a nullary variant's, says nothing: `None(Int) ≤ Maybe(Str)`.

## Joins

Where two types meet — the branches of an `if` or a `case`, two uses of a local — they must join.
There are no union types, and no top type.

Scalars
: join only with themselves — this is `Cannot join Int with Str`:

  ```bridje
  if: p
    1
    "s"
  ```

Records
: join to the keys they share, with those only one side carries kept as optional where the other side is closed — this is a `{.a, .?b, .?c}`:

  ```bridje
  if: p
    {.a 1, .b 2}
    {.a 1, .c 3}
  ```

Tags
: of one enum join to the enum.
  Tags of different enums, or standalone tags, join as records do: `Ok{.value 1}` and `Just{.value 2}` join to a `{.value(Int)}`.
  A tag with no record joins with no other tag.

Vectors and sets
: join elementwise with their own kind: `[Int] ∨ [Int?]` is `[Int?]`.
  A vector and a set join to an `Iterable`: `[Int] ∨ #{Int}` is `Iterable(Int)`.

Host classes
: join to their nearest common superclass; interfaces aren't consulted.
  Two iterable classes off different chains join to an `Iterable`: `ArrayList(Int) ∨ LinkedList(Int)` is `Iterable(Int)`.

`{.k & r}` and `r`
: join to `r` — one side is a narrowing of the other.

## Nullability

Every type is non-nullable unless it ends in `?`.
Passing a `T?` where a `T` is demanded is an error: `nil is not a Bool, which is not nullable`.

`ifLet:` narrows its binding past nil in its then-branch; `case:` never narrows past nil.

## How types display

Error messages display a type as its quantified variables, and any bounds they carry, then the type:

```
[a] Fn([[a]] a)                          // first
[a, b] Fn([Iterable(a), Fn([a] b)] [b])  // mapv
[a] Fn([{& a}] {.dirty & a})             // fn: touch(r) with(r, .dirty true)
Fn([Bool, Int] Int)                      // fn: f(p, x) if: p x 1
[a, isa([Int], a)] Fn([Bool, a] a)       // fn: f(p, x) if: p x [1]
```

A bound the type can express is written in it: a record demanded of `a` is `{& a}`, and a primitive bound is the primitive itself — `x`, joined with an `Int`, can only be an `Int`.

`isa(lower, upper)`
: A bound that can't be folded into the type: `lower` is a subtype of `upper`.
  `isa([Int], a)` says an `[Int]` flows into `a`; what they join to depends on what `a` turns out to be.

A variable that only ever flows out displays as what flows into it, and one that only flows in as what's demanded of it.

## Not built yet

- Traits.
- `Map(k, v)`.
- Numeric widening: `add(1, 1.5)` is an error.
