---
title: brj.core
description: The functions and macros available unqualified in every namespace.
---

Everything in `brj.core` is available in every namespace, without a `require`.

## Arithmetic and comparison

:::note
There's no numeric widening: both arguments must be the same kind of number — `add(1, 1.5)` is an error.
:::

`add(a, b)`, `sub(a, b)`, `mul(a, b)`, `div(a, b)`
: Arithmetic on two numbers of the same type — two `Int`s or two `Double`s — returning that type.
  `div` on `Int`s truncates.

`eq(a, b)`, `neq(a, b)`
: Equality, returning `Bool`.
  Numbers, strings, booleans and tag singletons compare by value; vectors, sets and records currently compare by identity, so two separately built `[1, 2]`s are not `eq`.

`lt(a, b)`, `gt(a, b)`, `lte(a, b)`, `gte(a, b)`
: Ordering, returning `Bool`.

`isSame(a, b)`
: Identity — whether `a` and `b` are the same object.

`not(b)`
: `Bool` negation.

## Vectors

`count(xs)`
: The number of elements.

`first(xs)`
: The first element; `firstOrNull(xs)` returns `nil` if `xs` is empty.

`rest(xs)`
: All but the first element.

`nth(xs, i)`
: The element at index `i`.

`cons(x, xs)`
: `xs` with `x` added at the front.

`concat(xs, ys)`
: `xs` followed by `ys`.

`isEmpty(xs)`
: Whether `xs` has no elements.

## Sets

`toSet(xs)`
: A set of the elements of the vector `xs`.

`conj(x, s)`, `disj(x, s)`
: `s` with `x` added, or removed.

`contains(s, x)`
: Whether `s` contains `x`.

`union(s1, s2)`
: The elements of either set.

`setCount(s)`, `isSetEmpty(s)`
: The number of elements; whether there are none.

## Iteration

These take any `Iterable` — vectors, sets and host collections.

`mapv(xs, f)`
: A vector of `f` applied to each element.

`filterv(xs, pred)`
: A vector of the elements for which `pred` returns `true`.

`mapcatv(xs, f)`
: `f` applied to each element, which returns a vector; the results concatenated.

`reduce(xs, init, f)`
: Folds `f(acc, x)` over the elements, starting from `init`.

`itr(xs)`, `itrHasNext(it)`, `itrNext(it)`
: The iteration protocol underneath: an iterator over `xs`, whether it has another element, and the next element.

## Output

`println(x)`
: Prints `x`, followed by a newline.

## Errors

`throw(anomaly)`
: Throws an anomaly. Its return type is `Nothing`, so it fits anywhere. See [errors](/reference/errors/).

## Metadata

`meta(x)`
: The metadata record attached to `x`.
  Nothing declares which keys it carries, so its type is `{}`, and its keys are read with `.?`: `rdr/.?loc(meta(form))`.

`withMeta(x, record)`
: `x` with `record` as its metadata.

## Namespaces

`allNses()`
: The names of every loaded namespace, as `Symbol`s.

`nsVars(ns)`
: The vars of the namespace named `ns`.

`gensym()`, `gensym(prefix)`
: A fresh symbol, for macros.

## Macros

### Conditionals

`when: test body…`
: `body` if `test` is true, otherwise `nil`.

  ```bridje
  when: gt(n, 100)
    println("that's a big number")
  ```

`unless: test body…`
: `body` if `test` is false, otherwise `nil`.

`cond: test1 result1 test2 result2 … default?`
: The result of the first true test; a final unpaired form is the default, otherwise `nil`.

  ```bridje
  cond:
    gt(n, 0) "positive"
    lt(n, 0) "negative"
    "zero"
  ```

`and(a, b, …)`, `or(a, b, …)`
: Short-circuiting: `and` is `false` at the first false argument, `or` is `true` at the first true one.
  With no arguments, `true` and `false` respectively.

`unlessLet: [name value] else then`
: `ifLet` with its branches the other way round.

  ```bridje
  unlessLet: [config loadConfig()]
    useDefaults()
    useConfig(config)
  ```

`orElse(value, default1, default2, …)`
: The first of its arguments that isn't `nil`; the defaults are only evaluated if needed.

### Threading

In each threading macro, a step that's a call — `f(a)`, `.k()` — has the value inserted as its first argument; a bare step — `f`, `.k` — is called with the value alone.

`->: seed step1 step2 …`
: Threads `seed` through each step in turn.

  ```bridje
  ->: cluster count() div(2) add(1)
  // add(div(count(cluster), 2), 1)
  ```

`?>: seed step1 step2 …`
: As `->`, but returns `nil` as soon as any step does.

`as->: seed name step1 step2 …`
: Binds each result to `name`, which the steps can use in any position.

`cond->: seed test1 step1 test2 step2 …`
: Threads `seed` through each step whose test is true, skipping the others.

`doto: value step1 step2 …`
: Calls each step with `value`, for its side effects, then returns `value`.

### Anonymous functions

`#: body…`
: A one-parameter function, whose parameter is `it`: `#: add(it, 1)`.

`#0: body…`
: A function of no parameters: `#0: fetch()`.

### Other

`comment: body…`
: Ignores its body, and returns `nil`.

## Anomalies

The anomaly tags — `Unavailable`, `Interrupted`, `Busy`, `Incorrect`, `Forbidden`, `Unsupported`, `NotFound`, `Conflict`, `Fault` and `Host` — and their `.exnMessage` key are in `brj.core`. See [errors](/reference/errors/).
