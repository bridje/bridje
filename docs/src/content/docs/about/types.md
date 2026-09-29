---
title: The type system
description: Why Bridje's types work the way they do — inference, keys, structural records, nominal tags, and nil.
---

Bridje's type system has one job: to catch your mistakes without asking you to design your program around it.

That rules out a lot of the familiar options.
A type system that needs an annotation on every function asks for design work up front, before you know what the design is.
One built on classes and interfaces asks you to decide, early, which things are related to which — and then makes it expensive to change your mind.

So Bridje's types are built from a few pieces, each chosen to reflect how data actually flows through a program.

## Inferred, everywhere

Bridje infers the type of everything: locals, parameters, return values, whole functions.
You _can_ declare a function's type, with `decl:`, and the checker will hold the definition to it — but it's never required.

The checker is based on **algebraic subtyping** (Dolan's MLsub).
Rather than solving each type variable to a single type, as Hindley–Milner inference does, it tracks what flows _into_ each variable, and what's _demanded_ of it, as two sets of bounds:

```bridje
decl: shout({.title}) Nothing?
def: shout(x)
  println(.title(x))
```

Here, nothing flows into `x` yet, but `.title(x)` demands that it's a record carrying `.title`.
That demand is `shout`'s type: callers can pass any record at all, so long as it has a `.title`.

The upshot is that inferred types are as general as the code allows — `shout` doesn't need to know about `Todo`s, or `User`s, or whatever else has a title.

## Keys have one meaning

Records are built from **keys**, and a key's type is declared once, globally:

```bridje
decl: .title Str
```

Every key is declared, before anything uses it.
This follows `clojure.spec`'s lead: a fully qualified key means one thing, everywhere.
`.title` is a `Str` in every record that has one; if something needs an `Int` title, it's a different key.

It sounds like a restriction, but it's what makes structural records practical.
Because the key carries its type, a record's type is just the _set of keys_ it's known to carry — `{.title, .priority}` — and there's nothing to reconcile when two records share a key: they share its meaning too.
And it means the vocabulary of your domain lives in one place.

## Records are structural

A function that reads `.title` and `.priority` accepts _any_ record carrying them, whatever else it carries.
There's no class to extend and no interface to implement; if the data has the right shape, it fits.

More keys is more specific: `{.title, .priority, .notes}` fits anywhere `{.title, .priority}` does.
And when two branches produce different records, the result is the keys they have in common:

```bridje
if: urgent
  {.title "Call Bob", .priority 1}
  {.title "Water plants", .notes "the fern too"}
// a {.title, .?priority, .?notes}: .title is certainly there, the others might be
```

## Tags have identity

Sometimes shape isn't enough: a `Todo` and a `Reminder` might both have a `.title`, but they're not interchangeable.
**Tags** are nominal — a tag is distinct from every other tag, however similar their keys — so they're how you give a value an identity.

A tag is still a record, though: a `Todo` passes anywhere `{.title}` is asked for.
Tags add identity to structure; they don't replace it.

## Enums are the only sums

An **enum** is a closed set of tags, and it's Bridje's only sum type.
There are no union types — `Int | Str` doesn't exist — and so this is an error, not an `Int or Str`:

```bridje
if: p
  1
  "s"
```

That's deliberate.
Anonymous unions make inference produce types nobody asked for, and push the work of taking them apart onto every consumer.

Tags from different enums meet as records do, at the keys they share: an `Ok{.value 1}` or a `Just{.value 2}` is a record carrying `.value` — you can read it, but it's neither an `Ok` nor a `Just` any more.
An enum is a decision you make once, and name — and because it's closed, `case` can check you've handled every variant.

## Nil is a type, not a wrapper

Bridje has no `Maybe` or `Optional`.
A value that might be missing has a type ending in `?` — `Str?` — and a value without one can't be `nil`.

This follows Rich Hickey's ["Maybe Not"](https://www.youtube.com/watch?v=YR5WdGrpoug): optionality belongs at the place a value is used, not wrapped around the value itself.
A key's type says what it holds _when it's there_; whether a particular function needs it to be there is a separate question — answered by `.title` (it must be) or `.?title` (it might not be).

## Declarations narrow

A `decl:` can say less than the checker inferred — to fix a public API, or to document intent — but never more.
The declaration is what callers see, so an inferred type that's more general than you'd like to commit to can be narrowed, and a declaration that promises more than the code delivers is an error.

## What's not built yet

- **Traits** — named sets of keys and functions, as constraints on type variables.
- **Maps** with arbitrary keys, `Map(k, v)`.
- **Numeric widening** — `add(1, 1.5)` is an error for now.

The [types reference](/reference/types/) has the full syntax and rules.
