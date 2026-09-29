---
title: Syntax
description: The reader — literals, symbols and members, collections, the three sugars, comments, metadata and quoting characters.
---

Bridje source is read into **forms**, the reader's s-expressions, before anything else sees it.
Everything on this page is the reader's: the sugars are gone by the time the analyser, or a macro, sees the code.

## Literals

`42`, `-1`
: `Int` — a 64-bit signed integer.

`3.14`, `-0.5`
: `Double` — a 64-bit float.
  A decimal point needs digits either side.

`42N`
: `BigInt` — an arbitrary-precision integer. `n` works too.

`3.14M`, `42M`
: `BigDec` — an arbitrary-precision decimal. `m` works too.

:::caution
`BigInt` and `BigDec` literals are read, and can be quoted, but aren't yet supported at runtime.
:::

`"hello"`
: `Str`.
  Escapes: `\n`, `\t`, `\r`, `\b`, `\f`, `\"`, `\\`, and `\uXXXX` with four hex digits; any other escape is an error.
  There is no string interpolation.

`true`, `false`
: `Bool`.

`nil`
: the absent value, of type `Nothing?`.

## Symbols

`foo`, `isEmpty`, `->`, `+`
: A symbol: a letter or one of `* _ + = ? ! < > &`, followed by those and digits and `-`.
  A symbol may also start with `-` followed by a non-digit (`-1` is an `Int`), or with `#` (`#`, `#0` — see [anonymous functions](/reference/core/#anonymous-functions)).

`brj.core`, `java.time.Instant`
: A dotted symbol — a namespace or class name.

`c/spawn`, `brj.core/mapv`
: A qualified symbol: an alias, namespace or class, then `/`, then a name.

`tmp#`
: A symbol ending in `#` is an auto-gensym inside a [quote](/reference/macros/#gensyms).

## Members

`.name`
: A member: a record key, or — qualified by an import alias — a host member.
  See [records](/reference/records-tags-enums/#keys).

`.?name`
: The optional accessor for the key `.name`.

`m/.name`, `I/.toEpochMilli`
: A qualified member: a require alias gives a Bridje key, an import alias a host member.

There is no postfix dot: `a.b` is a single dotted symbol, not a member access.

## Collections

`(a b c)`
: A list — a call, or a special form, when evaluated.

`[a b c]`
: A vector.

`#{a b c}`
: A set.

`{.k1 v1 .k2 v2}`
: A record: members and values, alternating.

Commas are whitespace everywhere: `[1, 2, 3]` is `[1 2 3]`.

## Sugar

`f(a, b)`
: **Call syntax.** A symbol, qualified symbol or member immediately followed by `(` reads as a list with it at the head: `(f a b)`.
  There must be no whitespace before the `(`.

`Tag{…}`
: **Construction syntax.** A symbol or qualified symbol immediately followed by `{` reads as a call with the braces' record: `Tag{.k v}` is `(Tag {.k v})`.

`name: …`
: **A block.** A symbol or qualified symbol immediately followed by `:` opens a list headed by that symbol.
  It contains every form after the colon on the same line, then every following line indented further than the start of the line the block opened on; the block closes at the first line that isn't.

  ```bridje
  def: foo(a, b)
    let: [c add(a, b)]
      mul(c, 2)
  ```

  reads as `(def (foo a b) (let [c (add a b)] (mul c 2)))`.

  The colon *is* the opening parenthesis: `(foo: a)` reads as `((foo a))`.
  A block opened mid-line takes the rest of that line, so `[a fn: f() 1, b 2]` puts `b 2` inside the `fn`.

## Comments

`// …`
: A line comment. `////` is a convention for section headings.

`#_ form`
: Discards the next form, whatever its size.

## Metadata

`^{.k v} form`
: Attaches the record to the form, as its static metadata.

`^.k form`
: Shorthand for `^{.k true} form`; `^m/.k` for a qualified key.

Metadata on a `def:` is recorded on the var.
Several `^`s on one form merge.

## Quoting

`'form`
: `(quote form)`.

`` `form ``
: `(squote form)`.

`~form`
: `(unquote form)`.

`~@form`
: `(unquoteSplicing form)`.

See [macros and quoting](/reference/macros/) for what they do.
