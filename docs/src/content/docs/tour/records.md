---
title: Records
description: Keys, record literals, reading and updating records, and why keys are declared once.
---

Most data in Bridje is held in **records**: collections of named values, like an object without a class, or a map with fixed keys.

## Keys

The names in a record are **keys**, written with a leading dot.
Before you use a key, you declare it, with its type:

```bridje
decl: {.title Str, .priority Int}
```

This happens once, at the top level of a namespace, and it's the only time you'll say what type `.title` holds: everywhere `.title` is used, it's a `Str`.
If you need something else — an `Int` title, say — that's a different key.

## Making records

A record literal pairs keys with values, between braces:

```bridje
decl: milk {.title, .priority}
def: milk {.title "Buy milk", .priority 2}
```

A record's type is the set of keys it carries — `{.title, .priority}` — since each key's own type was declared up front.

## Reading records

Each key you declare is also a function that reads it:

```bridje
.title(milk)
// => "Buy milk"
```

Because keys are functions, they slot straight into `mapv`, `->` and friends:

```bridje
mapv(todos, .title)
// => ["Buy milk", ...]

->: milk .title
// => "Buy milk"
```

If a record might not have a key, read it with `.?` instead — you get `nil` if it's absent:

```bridje
decl: .notes Str

.?notes(milk)
// => nil
```

## Updating records

Records are immutable, so "updating" one means making a new record with some keys changed.
`with` does exactly that:

```bridje
decl: urgentMilk {.title, .priority}
def: urgentMilk
  with(milk, .priority 1)

.priority(urgentMilk)
// => 1

.priority(milk)
// => 2 — the original is unchanged
```

`with` can add keys, too: `with(milk, .notes "semi-skimmed")`.

## Records are structural

A function that reads `.title` accepts *any* record with a `.title` — it doesn't need to know what else is in there:

```bridje
decl: shout({.title}) Nothing?
def: shout(x)
  println(.title(x))

shout(milk)
shout({.title "Walk the dog", .notes "before 9"})
```

Bridje's type checker works this out for itself: it infers that `shout` needs a record with a `.title`, and checks every caller provides one.
Pass it something without — `shout({.notes "?"})` — and you'll get an error before the program runs: `{.notes} lacks {.title}`.
You don't have to write any types for this; you can, though, and we'll see how [later](/tour/tags-and-enums/#declaring-types).

## Next

Records are great for data whose shape is all that matters.
When a value needs an identity of its own — this is a `Todo`, not just something with a title — that's what [tags and enums](/tour/tags-and-enums/) are for.
