---
title: Effects
description: Declaring effects with defx, binding them with withFx, and how effects are inferred.
---

An effect is a lexically scoped value: declared once, used like any other value, and bound for a region of code with `withFx:`.
[Effects](/about/effects/) explains the design.

## Declaring

`defx: name(P1, P2) R`
: Declares an effect that's a function, in the same shape as a function [declaration](/reference/types/#declarations).

`defx: name Type`
: Declares an effect of any type — a function, a record, a host object.

`defx: name(…) R default`, `defx: name Type default`
: As above, with a default value, used wherever the effect isn't bound.

```bridje
defx: now() Int
defx: log(Str) Nothing? println
defx: config {.port, .host}
```

`defx:` is only allowed at the top level.
An effect from another namespace is referred to through a require alias, `m/log`.

## Using

An effect is used like any other value: `log("starting")`, `.port(config)`.

A top-level `def:` whose value — as opposed to a function body — uses an effect is an error: `effects can only be used within a function body`.

## Binding

`withFx: [effect1 value1, effect2 value2, …] body…`
: Evaluates the body with each effect bound to its value.
  Each value is checked against the effect's declared type.

  The binding applies to everything evaluated within the body, however deeply nested the calls, including tasks spawned there.
  Code outside the body is unaffected.

  An effect is named in `withFx:` as it would be used: `log`, or `m/log` from another namespace.

```bridje
def: fixedClock()
  1000

def: quietly(msg)
  nil

withFx: [now fixedClock, log quietly]
  completeTodo(milk)
```

## Inference

The compiler infers the set of effects every expression uses: the effects it refers to directly, and those of the functions it calls.
Effects aren't annotated.

Binding an effect removes it from the set, and adds the effects its bound value uses:

```bridje
defx: log(Str) Nothing?
defx: stdio(Str) Nothing? println

def: logViaStdio(msg)
  stdio(msg)

def: doWork()          // uses log
  log("starting")

def: main(args)        // uses stdio
  withFx: [log logViaStdio]
    doWork()
```

:::caution
An effect with no default must be bound by an enclosing `withFx:` before it's called.
This isn't checked at compile time yet: calling an unbound effect fails at runtime.
:::
