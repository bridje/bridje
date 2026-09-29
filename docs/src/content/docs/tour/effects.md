---
title: Effects
description: Declaring side effects, providing them with withFx, and swapping them out in tests.
---

Sooner or later, a program has to talk to the outside world: print something, read the clock, call a service, write to a database.

The usual options each have a cost.
Call the outside world directly, and the code is hard to test — there's no seam to put a fake in.
Pass the outside world in as parameters, and every function in between has to carry them along, whether it uses them or not.

Bridje's answer is **effects**: named, typed values that code can use without passing them around, and that a caller can replace for everything it calls.

## Declaring an effect

`defx:` declares an effect.
It looks like a `decl:` — a name and a type — optionally followed by a default:

```bridje
defx: now() Int                          // no default
defx: log(Str) Nothing? println          // defaults to println
```

`log` is a function from a `Str` to nothing in particular (`Nothing?` is the type of `nil`), and unless told otherwise, it prints.

## Using an effect

Code that uses an effect just calls it, like any other function:

```bridje
decl: .completedAt Int

decl: [a] completeTodo({& a}) {.completedAt & a}
def: completeTodo(todo)
  log("completing a todo")
  with(todo, .completedAt now())
```

Its type reads: given any record `a`, `completeTodo` returns that same record, now carrying `.completedAt` too.

There's no extra parameter, and nothing special about the call.
Bridje works out for itself which effects each function uses — `completeTodo` uses `log` and `now` — and so, in turn, does every function that calls it.

## Providing an effect

`withFx:` provides effects for everything evaluated inside it — however deep the calls go:

```bridje
decl: fixedClock() Int
def: fixedClock()
  1000

decl: [a] quietly(a) Nothing?
def: quietly(msg)
  nil

withFx: [now fixedClock, log quietly]
  completeTodo(milk)
```

Inside the `withFx:`, `completeTodo` sees a clock that always says `1000`, and a `log` that does nothing.
Outside it, nothing has changed.
A one-off implementation can be written in place, too: `withFx: [now fn: fixedClock() 1000]`.

An effect's implementation can use other effects — a `log` that writes via some lower-level `stdio` effect, say — and then that's what the code inside the `withFx:` uses instead.

An effect with no default has to be provided by some enclosing `withFx:` before it's called.

:::note
The compiler doesn't yet check this for you: calling an effect that nothing has provided fails when it's called.
:::

## Effects in tests

This is where effects earn their keep.
Because the clock is an effect, a test can pin it.
Here, `todo.core` holds `completeTodo`, `milk` and the `now` effect; the test refers to them through the alias `c`, which the [next page](/tour/namespaces-and-java/) explains:

```bridje
ns: todo.core-test
  require:
    brj:
      as(test, t)
    todo:
      as(core, c)

def: fixedClock()
  1000

^t/.test
def: completingSetsTheTime()
  withFx: [c/now fixedClock]
    t/is(eq(1000, c/.completedAt(c/completeTodo(c/milk))))
```

No mocking library, no dependency injection framework, and nothing in `completeTodo` had to change to make it testable.

## Next

The last stop on the tour: [namespaces and Java](/tour/namespaces-and-java/) — organising a program, and using the JVM's libraries from it.
