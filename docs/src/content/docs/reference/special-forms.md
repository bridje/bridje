---
title: Special forms
description: The forms built into the analyser — their shapes, rules and errors.
---

A special form is recognised by the analyser at the head of a list.
Special forms aren't values: they can't be passed around, and a local of the same name doesn't shadow them.

Shapes are given in block syntax, on one line; each is equally a list — `if: p a b` is `(if p a b)`.
The examples under each show how they're laid out in practice.

## Top level

These are only allowed at the top level of a namespace, or inside a top-level `do:`.

`def: name value`
: Defines `name` as the value of `value`.

`def: name(p1, p2, …) body…`
: Defines a function; equivalent to `def: name fn: name(p1, p2, …) body…`.

  A definition can't see itself: a function can't call itself by name — use [`recur`](#loop-and-recur).
  Metadata on the `def:` — `^.test def: …` — is recorded on the var.

  ```bridje
  def: answer 42

  def: area(w, h)
    mul(w, h)
  ```

`defmacro: name(p1, p2, & rest) body…`
: Defines a macro. See [macros](/reference/macros/).

`decl: …`
: Declares a key or a type. See [types](/reference/types/#declarations) and [keys](/reference/records-tags-enums/#keys).

`tag: …`, `enum: …`
: Declare a tag or an enum. See [tags and enums](/reference/records-tags-enums/#tags).

`defx: …`
: Declares an effect. See [effects](/reference/effects/).

`ns: …`
: Names the namespace; only as the first form of a file. See [namespaces](/reference/namespaces/).

## Sequencing and binding

`do: form…`
: Evaluates each form in order, returning the value of the last.
  Requires at least one form.

  ```bridje
  do:
    println("working")
    42
  ```

`let: [name1 value1, name2 value2, …] body…`
: Binds each name to its value, in order — each value can refer to the names before it — then evaluates the body.
  Names are symbols; there is no destructuring.

  ```bridje
  let: [w 3
        h 4]
    mul(w, h)
  ```

## Functions

`fn: name(p1, p2, …) body…`
: A function value.
  The name is required — it appears in stack traces — but isn't bound in the body.
  Parameters are symbols; a function has one fixed arity.

  Closures capture the locals they refer to.

  ```bridje
  mapv(xs, fn: double(x) mul(x, 2))

  def: adder(n)
    fn: addN(x)
      add(x, n)
  ```

## Conditionals

`if: test then else`
: Evaluates `test`, which must be a `Bool`, then `then` or `else`.
  Exactly three forms; for a one-armed `if`, see [`when`](/reference/core/#conditionals).

  ```bridje
  if: gt(n, 0)
    "positive"
    "not positive"
  ```

`ifLet: [name value] then else`
: Evaluates `value`; if it isn't `nil`, binds it to `name` and evaluates `then`, otherwise evaluates `else`.
  `name` is in scope in `then` only, where its type is narrowed past nil.

  ```bridje
  ifLet: [nick .?nickname(user)]
    nick
    .name(user)
  ```

`case: value pattern1 body1 pattern2 body2 … default?`
: Evaluates `value` and the body of the first pattern that matches it.
  A final, unpaired form is the default.

  ```bridje
  case: shape
    Circle{radius}
      mul(3, mul(radius, radius))
    Square{side}
      mul(side, side)
    0
  ```

  | Pattern | Matches |
  | :-- | :-- |
  | `Tag`, `m/Tag` | a value with that tag, whatever its record |
  | `Tag(r)` | a value with that tag, binding it to `r` |
  | `Tag{k1, k2}` | a value with that tag, binding `k1` to its `.k1` and `k2` to its `.k2` |
  | `x` — lowercase | any value, nil included, binding it to `x` |

  A key in a `Tag{…}` pattern is looked up among the tag's own keys first, then in scope.
  `nil` is not a pattern — branch on it with `ifLet`.

  Without a default or a catch-all, a `case` over an enum's tags must name every variant: `Non-exhaustive case: missing variants … of enum …`.
  Tags from different enums, or standalone tags, can't be cased together: `case patterns A and B belong to different enums`.

## Loops

`loop: [name1 init1, name2 init2, …] body…`
: Binds the names, like `let:`, and evaluates the body.

`recur(v1, v2, …)`
: Re-binds the innermost `loop:`'s names — or the enclosing function's parameters — and starts its body again.
  Takes one value per binding, and is only allowed in tail position: `recur` anywhere else is an error.

  ```bridje
  loop: [i 1
         total 0]
    if: gt(i, 10)
      total
      recur(add(i, 1), add(total, i))
  ```

## Records

`with(record, .k1 v1, .k2 v2, …)`
: A copy of `record`, with each key set to its value — added if absent.

  ```bridje
  with(state, .currentTerm newTerm, .votedFor nil)
  ```

:::note
There's no in-place mutation for now: `set` has been taken out while mutability is rethought, and is likely to return.
Meanwhile, mutable state lives in host objects, such as a `java.util.concurrent.atomic.AtomicReference`.
:::

## Errors

`try: body… catch: pattern1 handler1 … finally: cleanup…`
: Evaluates the body.
  If it throws, the thrown anomaly is matched against the `catch:` clauses' patterns, as in `case:`; a final unpaired form is the default.
  A `try:` needs at least one `catch:` clause, and may have several, whose branches are combined in order.
  `finally:` runs whether or not the body threw.

  ```bridje
  try:
    findUser(id)
    catch: NotFound(e)
      nil
    catch: Host(e)
      throw(Fault{.exnMessage "lookup failed"})
    finally:
      closeConnection()
  ```

  See [errors](/reference/errors/).

## Effects

`withFx: [effect1 value1, effect2 value2, …] body…`
: Binds each effect to its value for the evaluation of the body.

  ```bridje
  withFx: [now fixedClock, log quietly]
    completeTodo(milk)
  ```
  See [effects](/reference/effects/).

## Host languages

`lang(language, Type, code)`
: Evaluates `code` in another GraalVM language, taking its result to be of type `Type`.
  `language` and `code` are string literals; the language must be installed alongside Bridje.

## Quoting

`quote(form)`, `squote(form)`
: Written `'form` and `` `form ``. See [macros and quoting](/reference/macros/).
