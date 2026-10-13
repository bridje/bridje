---
title: Functions
description: Local bindings, anonymous functions, conditionals, working with collections, and threading.
---

## Local bindings

`let:` names intermediate values.
It takes a vector of name–value pairs, then a body that can use them:

```bridje
decl: hypotenuseSquared(Int, Int) Int
def: hypotenuseSquared(a, b)
  let: [a2 mul(a, a)
        b2 mul(b, b)]
    add(a2, b2)
```

Each binding can refer to the ones before it.

## Doing several things

`do:` evaluates its forms in turn and returns the last one — useful when something needs to happen for its side effect first:

```bridje
decl: noisyDouble(Int) Int
def: noisyDouble(x)
  do:
    println("doubling")
    mul(x, 2)
```

A function body is already a sequence, so you won't often need `do:` there; `def: noisyDouble(x)` followed by the two indented lines works just as well.

## More than two branches

`if:` has exactly two branches.
For more, there's `cond:` — pairs of test and result, checked from the top, with an optional default at the end:

```bridje
decl: describe(Int) Str
def: describe(n)
  cond:
    gt(n, 0) "positive"
    lt(n, 0) "negative"
    "zero"
```

`when:` is an `if` with no else — it returns `nil` if the test is false:

```bridje
when: gt(n, 100)
  println("that's a big number")
```

`and` and `or` take any number of arguments, and stop as soon as they know the answer.

## Anonymous functions

Functions are values: you can pass them to other functions, and return them.
`fn:` makes one without defining it at the top level:

```bridje
mapv([1, 2, 3], fn: double(x) mul(x, 2))
// => [2, 4, 6]
```

An `fn` still has a name — `double`, here — which shows up in stack traces.
For short one-parameter functions, `#:` is shorter still; its parameter is always called `it`:

```bridje
mapv([1, 2, 3], #: mul(it, 2))
// => [2, 4, 6]

filterv([1, 2, 3, 4], #: gt(it, 2))
// => [3, 4]
```

## Working with collections

`mapv`, `filterv` and `reduce` are the workhorses:

```bridje
reduce([1, 2, 3, 4], 0, fn: plus(acc, x) add(acc, x))
// => 10
```

along with `count`, `first`, `rest`, `concat`, `isEmpty` and `contains` — see [the core library](/reference/core/) for the rest.

## Threading

Nested calls read inside-out:

```bridje
add(div(count(items), 2), 1)
```

The `->` macro lets you write them in the order they happen instead.
It takes a starting value, and passes it as the first argument to each step in turn:

```bridje
->: items count() div(2) add(1)
```

Reading left to right: take `items`, `count` them, divide by 2, add 1.
`?>` is the same, except that it stops, returning `nil`, as soon as any step returns `nil`.

## Loops

Most iteration in Bridje is `mapv`, `filterv` and `reduce`.
When you need a loop, `loop:` binds some starting values, and `recur` jumps back to the top with new ones:

```bridje
decl: sumTo(Int) Int
def: sumTo(n)
  loop: [i 1, total 0]
    if: gt(i, n)
      total
      recur(add(i, 1), add(total, i))
```

`recur` can only appear as the last thing a loop does, which is what lets it run in constant space.
A function body is a `recur` target too, re-running the function with new arguments.

## Next

[Records](/tour/records/) — Bridje's main way of structuring data.
