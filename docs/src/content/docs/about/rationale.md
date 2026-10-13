---
title: Rationale
description: Why Bridje exists, and the problems it's trying to help with.
---

There have been two main drivers for writing Bridje.

## A Lisp for everyone

I absolutely love writing Clojure — I've used it since 2012, and (subjectively) no other language comes close when it comes to the joy of programming.
I put that down to three things:

- **The REPL.** The fast feedback loop has changed the way I develop software: building it up piece by piece, in small increments, each of which I'm confident in.
- **Being an expression-oriented Lisp.** Ridiculously simple syntax makes such a difference when manipulating code.
  People often say the main benefit of a Lisp is its macros — a boon, no doubt — but for me it's that the individual sub-expressions are uniform, interchangeable and easily extracted.
- **Immutability by default.** I'm not spending time wondering why my data has changed underneath me.

Given all that, I find it a massive shame that Clojure isn't nearly as mainstream as it should be — and I think the parentheses carry a lot of the blame.
For a lot of developers, the first look at a Lisp is the last.

So Bridje keeps the Lisp core, and puts a syntax on top that looks like a C- or Java-family language — brackets where you'd expect them:

```bridje
def: foo(a, b)
  let: [c add(a, b)]
    mul(c, 2)
```

is exactly

```clojure
(def (foo a b)
  (let [c (add a b)]
    (mul c 2)))
```

The transformation is purely syntactic, so nothing is lost: macros still see s-expressions, sub-expressions are still uniform, and you can write the parentheses wherever you'd rather.
But you don't _have_ to — and I expect most Bridje code won't.

## Help with the hard parts

No matter how much experience I gain, some parts of software engineering still take real thought.
I'd like the language to help with them.

### Types

The age-old debate.
Both sides have a point: static-typing advocates are right that a compile-time checker eliminates a class of bugs; dynamic-typing advocates are right that a type checker — especially as the mainstream languages implement one — demands too much up-front design and speculation, and adds rigidity the code doesn't need.

Bridje aims for another point on the spectrum: the safety of a compile-time checker, without it getting in the way — reflecting the natural types of your domain while still catching your mistakes.
Types are inferred, records are structural, and a key means the same thing everywhere.
[The type system](/about/types/) goes into how.

### Essential and incidental complexity

Moseley and Marks' ["Out of the Tar Pit"](http://curtclifton.net/papers/MosessleyMarks06a.pdf) argues that most of the complexity in software is incidental — I/O, state management, concurrency plumbing — and that the essential complexity, the actual domain logic, is surprisingly small once it's separated out.

I want my code to read as close to a specification as possible.
When I look at a function that implements a business rule, I want to see the business rule — not the database calls, the HTTP requests, or the thread management that happen to surround it.
Bridje aims to make that separation natural: pure domain logic reads like a spec; side effects are declared, explicit and substitutable.

[Allium](https://github.com/juxt/allium), a behavioural specification language developed at JUXT, has been a major inspiration here — particularly in how it strips a domain down to its types, rules and invariants.
Allium is deliberately non-executable; Bridje has to run.
The challenge is keeping the incidental cost of executability low enough that the essential logic still reads clearly.

### Side effects

Functional programming has patterns for this — "functional core, imperative shell" — but I'd like Bridje to help in the same way a type checker helps with types: by tracking which side effects each function relies on, and by letting them be swapped out easily, for testing in particular.
That's what [effects](/about/effects/) are for.

### Concurrency

I've come to really appreciate Kotlin's structured concurrency — knowing that tasks are carefully managed and their failures propagated has been a boon — and I've found that systems built from communicating sequential processes are often easier to reason about, and contain fewer bugs as a result.

I'd like to go further: if a system's concurrency is expressed as inspectable data — what is this process waiting for, and what will it do when each event arrives? — then the same code that runs in production can be simulated deterministically in tests, with the harness controlling the order of events, injecting failures and checking invariants.
Bridje has structured concurrency today; the simulation model is where it's heading.

## The ideal

One artefact that serves as specification, implementation and test subject — rather than three separate documents that drift out of sync.
Bridje isn't there yet, but that's the direction.
