---
title: Idioms
description: How idiomatic Bridje is written — layout, naming, and choosing between records, tags and enums.
---

None of this is enforced by the compiler; all of it is how Bridje is meant to read.

## Layout

### Use the sugar

The s-expression forms always work, but idiomatic Bridje uses the sugared ones: `f(a, b)` for calls, `name:` blocks for anything with a body.

Reach for the parenthesised form only where the sugar can't go — calling the result of an expression, for example: `((adder 1) 2)`.
(Call syntax on a call, `adder(1)(2)`, is [#146](https://github.com/bridje/bridje/issues/146).)
Don't combine the two: `foo: a b` already _is_ `(foo a b)`, so `(foo: a b)` is `((foo a b))`.

### Blocks for anything complex

Keep a call on one line while it's short and flat.
Once it has more than a couple of arguments, or its arguments are themselves calls, give it a block:

```bridje
and:
  gte(.term(req), .currentTerm(state))
  or:
    isEmpty(.log(state))
    eq(.from(req), .votedFor(state))
  candidateLogUpToDate(state, req)
```

The same goes for `cond:` and `case:` whose results are more than a single expression: put the result on the next line, indented.

A block runs to the end of its line, so a block in the middle of a line takes everything after it — keep a block last on its line.

### Thread left to right

Prefer `->` over nested calls when there are more than two steps, and write each step with parentheses — `count()`, not `count` — so it reads as a call:

```bridje
->: cluster count() div(2) add(1)
```

### Top-level structure

A namespace can only use what's defined above it, so a file reads bottom-up: members and types first, then helpers, then the public API.
Use `////` comments to mark the sections:

```bridje
//// Keys
decl: {.name Str, .age Int}

//// Types
tag: Person{.name, .age}

//// Helpers
def: _isAdult(person)
  gte(.age(person), 18)

//// Public API
def: greet(person)
  ...
```

Leave a blank line between top-level forms.
A tight group of one-liners that belong together — a run of `decl:`s, say — can sit without them.

## Naming

- **`camelCase`** for functions and values: `handleVoteRequest`, `startElection`.
- **`PascalCase`** for tags, enums and types: `ServerState`, `VoteRequest`.
- **`.camelCase`** for keys: `.currentTerm`, `.knownLeader`.
- **`lowercase.dotted`** for namespaces: `raft.server`; a hyphen within a segment is fine for test namespaces, `raft.server-test`.
- **Acronyms are words**: `handleRpc`, `parseJson` — not `handleRPC`.
- **Predicates** take an `is` prefix only where the name isn't already a verb: `isEmpty` and `isSame`, but `contains`, `exists`, `hasNext`.
- **No suffixes** for effects or side-effecting functions: `log`, `send`.
  What a function does to the world belongs in its type, not its name.
- **`_private`** by convention.
  There's no access control — nothing stops a caller using `_helper` — but the underscore says "not part of the API".

The threading macros — `->`, `?>`, `as->`, `cond->` — are the only names containing symbols; the hyphen there is half an arrow, not a word separator.

**Host names are spelled as the host spells them.**
A Java member is looked up by its name, so `.toURI` stays `.toURI`, even though a Bridje name would be `toUri`.

## Data

Bridje has three ways to model data; pick the least structure that does the job:

- **A record** when all that matters is the data — its keys and their values.
- **A tag** when a value needs an identity of its own: a `User`, not just something with a `.name`.
- **An enum** when a value is one of a fixed set of alternatives, each with its own data.

Functions transform data: records in, records out.
Keep the logic in plain functions over plain data, and the outside world behind [effects](/about/effects/).

Keys are functions, so there's rarely a need to wrap one: `mapv(people, .name)`, not `mapv(people, #: .name(it))`.

## Immutability

Values are immutable by default, and `with` returns a new record rather than changing the old one.

For now, there's no in-place mutation of Bridje values at all — `set` has been taken out while mutability is rethought — so where you genuinely need mutable state, keep it in a host object, such as an `AtomicReference`.
