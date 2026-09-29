---
title: Coming from Clojure
description: What carries over from Clojure, what's different, and the habits worth unlearning.
---

If you've written Clojure, you already know most of Bridje — it's the language I'd want Clojure to be if it had to win over people who've never written a Lisp.
This page is the list of what's the same, what's different, and the things that will trip you up for your first week.

## What's the same

- **It's a Lisp.** Code is data, every expression returns a value, and there are no statements.
  The sugar is purely syntactic: `'foo(a, b)` is the form `(foo a b)`, and you can write the parenthesised forms anywhere.
- **Immutable, persistent data**, by default.
- **The REPL**, over [nREPL](https://nrepl.org/) — [Conjure](https://github.com/Olical/conjure) works out of the box.
- **Macros**, with `'`, `~`, `~@` and `x#` gensyms, and `` ` `` to resolve symbols to their namespaces.
- **Namespaces**, with `require` and `import`, and `alias/name` qualification.
- **The threading macros** — `->`, `as->`, `cond->`, `doto` — and `cond`, `when`, `and`, `or`, `comment`, `#_`, `^` metadata, and `loop`/`recur`.
- **The JVM**, and all of its libraries.

## The syntax

The three sugars are covered in [the tour](/tour/first-steps/#its-a-lisp):

| Clojure | Bridje |
| :-- | :-- |
| `(f a b)` | `f(a, b)` |
| `(defn f [a b] ...)` | `def: f(a, b)` then an indented body |
| `(fn [x] ...)` | `fn: name(x) ...` — anonymous functions still take a name, for stack traces |
| `#(inc %)` | `#: add(it, 1)` — always one parameter, called `it`; `#0:` takes none |
| `(let [x 1] ...)` | `let: [x 1]` then an indented body |
| `(:name user)` | `.name(user)` |
| `(-> x (f 1) g)` | `->: x f(1) g()` |

Commas are still whitespace.

## Keys, not keywords

Bridje has no keywords.
The equivalent is the **key**, written `.name`, and every key is declared once, with a type, before it's used:

```bridje
decl: {.name Str, .email Str}
```

If that sounds like `clojure.spec` — a key has one meaning everywhere — that's exactly where it comes from, except that here the compiler checks it.

- **A key is a function**: `.name(user)`, `mapv(users, .name)`.
- **`.?name(user)`** returns `nil` if the key isn't there; plain `.name` is checked at compile time to be present.
- **Keys are namespaced** by where they're declared: from elsewhere, `m/.name`, where `m` is a require alias.
- **Records replace maps.** `{.name "James"}` is a record; `with` is `assoc`.
  There are no general-purpose maps with arbitrary keys yet.

## Tags and enums, not `:type` keys

Where Clojure code reaches for a map with a `:type` key, or a multimethod dispatching on one, Bridje has **tags** and **enums**:

```bridje
decl: {.radius Int, .side Int}

enum: Shape
  tag: Circle{.radius}
  tag: Square{.side}

def: area(shape)
  case: shape
    Circle{radius} mul(3, mul(radius, radius))
    Square{side} mul(side, side)
```

`case` matches tags — not constants, as Clojure's does — and checks that every variant of an enum is covered.

## nil, and no truthiness

- **`if` takes a `Bool`.** There's no truthiness: `if: nil ...` and `if: 0 ...` are type errors.
- **Nil is tracked.** A value that might be `nil` has a type ending in `?`, and the compiler won't let you use it where a real value is needed.
- **`if-let` is `ifLet`, `some->` is `?>`,** and `(or x default)` is `orElse(x, default)`.
- **`case` doesn't match `nil`** — branch on it with `ifLet` first.

## Dynamic vars become effects

Where Clojure has `^:dynamic` vars and `binding`, Bridje has [effects](/tour/effects/): `defx` declares one, `withFx` binds it.
The differences:

- **Effects are lexical**, not thread-bound: a `withFx` applies to everything evaluated inside it, including code on tasks it spawns, and nothing else.
- **Effects are tracked.** The compiler knows which effects every function uses, so they can't hide.

## Exceptions are anomalies

Errors are thrown as **anomalies** — tags named after the [cognitect.anomalies](https://github.com/cognitect-labs/anomalies) categories (`NotFound`, `Incorrect`, `Forbidden`, `Unavailable`, …), each over a record of details.
`catch:` pattern-matches on them:

```bridje
try:
  findUser(id)
  catch: NotFound(e) nil
```

A Java exception arrives as `Host`.

## Macros take forms

Quoting returns typed **forms**, not lists: `'(a b)` is a `List` form, a value of the `brj.rdr/Form` enum, not a runtime list.
A macro receives forms and returns one, and takes a form apart with `case`:

```bridje
case: form
  rdr/List{els} ...
  other ...
```

`` ` `` quotes a form and resolves the symbols in it to their namespaces; a local can't be resolved, so it has to be unquoted: `` `(when ~x) ``.

## Other things that will catch you out

- **camelCase, not kebab-case.** `isEmpty`, not `empty?`; `mapv`, `filterv`.
  See [idioms](/about/idioms/#naming).
- **Host interop reads differently.** `(.toEpochMilli inst)` is `I/.toEpochMilli(inst)`, where `I` is the import alias — and an instance member has to be [declared](/reference/interop/) first.
- **No destructuring in `let` or function parameters**, yet — only in `case` patterns.
- **One arity per function**, and no variadic functions; macros can take `& rest`.
- **Collections are eager.** `mapv` and `filterv` return vectors; there are no lazy sequences.
- **Definitions can only refer to what's above them**, including themselves: a function can't yet call itself by name — use `recur`.
