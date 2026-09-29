---
title: Effects
description: Why Bridje treats side effects as lexically scoped values, and what that buys you.
---

Every program has to touch the outside world somewhere — and the question every language answers, one way or another, is how the rest of the code gets at it.

## The usual answers

**Call it directly.**
`Instant.now()`, `System.out.println`, `db.query(...)`, wherever they're needed.
It's simple, and it's what most code does — but the outside world is then wired into the domain logic, with no seam to substitute it at.
Testing means either mocking frameworks that rewrite classes at runtime, or not testing.

**Pass it in.**
Dependency injection, in its many forms: the clock and the repository become constructor parameters, or function arguments.
It works, and it's testable — but everything between the entry point and the code that needs the clock now has to know about the clock, and carry it along.
Frameworks exist largely to hide that plumbing.

**Model it in the types.**
Monads, effect systems, `IO`: the side effects become part of every function's type, and the type checker makes sure they're handled.
Rigorous — and, in my experience, a significant tax on everyday code.

**Make it dynamic.**
Clojure's dynamic vars: declare a var, and rebind it with `binding` for everything in scope.
No plumbing, easy to substitute — but invisible: nothing tells you which functions depend on which vars, and the binding follows the thread rather than the code.

## Bridje's answer

Bridje takes the part of dynamic vars that works — declare once, rebind for a scope — and fixes the parts that don't:

- **Effects are lexically scoped.**
  A `withFx:` binds an effect for exactly the code evaluated inside it — including tasks spawned from inside it — and nothing else.
  There's no thread-local state to leak.

- **Effects are tracked.**
  The compiler infers which effects every function uses, directly or through what it calls.
  You don't annotate anything; the dependency is there to be seen, rather than hidden.

- **Effects are just values.**
  An effect is declared with a type, and bound to anything of that type — a function, a record of functions, a connection.
  Using one is an ordinary call.

```bridje
defx: now() Int

def: stamp(todo)
  with(todo, .completedAt now())

def: fixedClock()
  1000

withFx: [now fixedClock]
  stamp(milk)
```

`stamp` doesn't take a clock, doesn't know how the clock is implemented, and doesn't need changing to be tested.

## Higher and lower level effects

An effect's implementation can itself use effects.
A high-level `log` might be implemented in terms of a lower-level `stdio`:

```bridje
defx: log(Str) Nothing?
defx: stdio(Str) Nothing? println

def: logViaStdio(msg)
  stdio(msg)

withFx: [log logViaStdio]
  doWork()
```

`doWork` uses `log`; the `withFx:` satisfies it, and in its place, the expression as a whole now uses `stdio`.
This is what lets a program describe _what_ it needs in its own terms — send a vote request, record a term — and decide _how_ at the edges.

## Where this is heading

Effects are one half of separating essential from incidental complexity; the other is concurrency.
The direction is for concurrent processes to be expressed as data — what each is waiting for, and what it does when something arrives — so that the same code can run in production or be driven, deterministically, by a test harness that controls the order of events.

For now, the compiler infers effects but doesn't yet check that each one is bound before it's used.
The [effects reference](/reference/effects/) has the details.
