---
title: Tags and enums
description: Giving values an identity with tags, closed sets of alternatives with enums, and matching on them with case.
---

Records say what a value _has_.
Sometimes we also need to say what a value _is_: a `Todo`, a `User`, an `Error`.

## Tags

A **tag** is a name over a record:

```bridje
decl: {.title Str, .priority Int}

tag: Todo{.title, .priority}
```

That declares a new type, `Todo`, whose values each carry a `.title` and a `.priority`.
To make one, give the tag a record — the `Tag{…}` syntax reads just like a record literal:

```bridje
decl: milk Todo
def: milk Todo{.title "Buy milk", .priority 2}
```

A `Todo` is still a record — `.title(milk)` and `with` work as before, and `with` gives you back a `Todo` — but it's also a distinct type of its own.
Two tags with exactly the same keys are still different types: a `Todo` is never mistaken for a `Reminder`.

A tag can also stand alone, with no record at all:

```bridje
tag: Nobody
```

`Nobody` is then a value in its own right — there's only ever one of it.

## Enums

An **enum** is a fixed set of tags — one of these, or one of those, and nothing else:

```bridje
decl: .doneBy Str

enum: Status
  tag: Open
  tag: InProgress{.doneBy}
  tag: Done{.doneBy}
```

A `Status` is always exactly one of `Open`, `InProgress` or `Done` — and the `.doneBy` is only there when it makes sense.
If you've met sum types, algebraic data types or sealed classes elsewhere, this is those.

## Matching with case

`case:` looks at which tag a value has, and picks a branch:

```bridje
decl: describe(Status) Str
def: describe(status)
  case: status
    Open "not started"
    InProgress{doneBy} "in progress"
    Done{doneBy} "done"
```

Each branch is a pattern, then the value to return.
`Done{doneBy}` both matches a `Done` and pulls its `.doneBy` out into a local called `doneBy`; `Done(d)` would instead bind the whole `Done` to `d`.

Because `Status` is an enum, Bridje knows every tag it could be — so if you forget one, it tells you:

```bridje
case: status
  Open "not started"
  Done{doneBy} "done"
// error: Non-exhaustive case: missing variants InProgress of enum Status
```

If you do want to catch "anything else", put a lone expression at the end — it's the default:

```bridje
case: status
  Done{doneBy} "done"
  "not done yet"
```

## Nothing at all

There's no `Maybe` or `Optional` in Bridje: a value that might be missing is just a value that might be `nil`.
The type checker tracks which values might be `nil`, and won't let you use one where a real value is needed until you've checked.

`ifLet:` does the checking.
It binds a value, and takes the first branch if it's there, the second if it's `nil`:

```bridje
decl: owner({}) Str
def: owner(todo)
  ifLet: [who .?doneBy(todo)]
    who
    "nobody yet"
```

Inside the first branch, `who` is definitely not `nil`.

## Declaring types

The `decl:`s in this tour have so far been for show: Bridje infers every type for itself.
You can write them yourself, though, and when you do, Bridje checks the definition against them:

```bridje
decl: describe(Status) Str
def: describe(status)
  ...
```

A declaration is documentation that can't go out of date — and it's what the rest of your program sees when it calls `describe`.
It can be narrower than what Bridje infers, but never wider.

The tour's `hypotenuseSquared` does exactly that: with no literal to pin its numbers down, and no model of numeric types yet, Bridje infers that it works on anything `mul` does — `[a] hypotenuseSquared(a, a) a` — and `decl: hypotenuseSquared(Int, Int) Int` narrows it to what you'd expect.
`Str?` is a `Str` that might be `nil`; `[Todo]` is a vector of `Todo`s; the [types reference](/reference/types/) has the rest.

## Next

We have a data model.
Next, [effects](/tour/effects/) — how Bridje code talks to the outside world.
