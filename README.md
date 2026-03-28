# Bridje

Bridje is a statically-typed programming language that runs on the Graal JVM, drawing inspiration from Clojure, Kotlin, Haskell, Erlang, and many more, as well as current advances in programming language theory.

**Bridje is currently pre-alpha, and nowhere near finished.**

What follows below are mostly ideas for how I envisage the language to work.

For me, there have been two main drivers for writing Bridje:

1. I absolutely love writing Clojure - I've used it since 2012, and (subjectively) no other language comes close when it comes to the joy of programming.

   I ascribe this largely to:

   - the REPL: the fast-feedback loop here has changed the way I develop software - building it up piece by piece, encouraging small increments that give me confidence in my changes.
   - being an expression-oriented LISP: ridiculously simple syntax makes such a difference when manipulating code.

     Some people say the main benefit of a LISP syntax is its macros - a boon, no doubt - but it's the fact that the individual sub-expression building blocks are interchangeable, easily extractable, and uniform.

   - immutability by default: I'm not spending time wondering why my data is changing underneath me.

   Given all this, I find it a massive shame that Clojure is not nearly as mainstream as it should be!

   Bridje therefore aims to bring this Clojure/LISP simplicity to a wider audience through a syntax that looks like a C/Java-style language (i.e. "brackets where you'd expect them"!), but retaining the benefits of a LISP core.

2. No matter how much experience I amass, it seems that there are some areas of software engineering that still require significant thought - I'd like Bridje to help with this complexity.

   - **types**: the age-old debate.
     Both sides have very valid points: static-types advocates are correct in saying that a compile-time type-checker eliminates a class of bugs; dynamic-types folks are also correct in saying that a type-checker (especially as implemented in the main programming languages) requires too much up-front design and speculation, as well as introducing unnecessary rigidity into the code.

     Bridje aims to find another point on the solution spectrum: the relative safety of a compile-time type-system but without hindering progress - instead, reflecting the natural types of your domain while still preventing mistakes.

   - **essential vs incidental complexity**: (cf. Moseley & Marks, "Out of the Tar Pit")
     I want my code to read as close to a specification as possible - the domain logic (essential) should not be interleaved with I/O, serialisation, concurrency plumbing, or framework wiring (incidental).

     When I look at a function that implements a business rule, I want to see the business rule - not the database calls, the HTTP requests, or the thread management that happen to surround it.

     Bridje aims to make this separation natural: pure domain logic reads like a spec; side effects are declared, explicit, and substitutable; high-level "what" is expressible without low-level "how".

   - **side effect management**: Clojure (and FP, more broadly) has patterns and idioms here which make this simpler - 'functional core, imperative shell' - but I'd like Bridje to help me out here in a similar way to how a type-system helps with types, by helping its users track what side-effects each function relies on, and allowing them to be substituted easily for testing.

   - **concurrency and simulation testing**: I have come to really appreciate Kotlin's 'structured concurrency' - knowing that threads are carefully managed, exceptions propagated etc has been a boon.

     I have found that systems that work through 'communicating sequential processes' (CSP) have often been easier to reason about, and, as a result, contain fewer bugs.

     But I want to go further: if the concurrency model is expressed as inspectable data (what is this process waiting for, and what will it do when each event arrives?), then the same code that runs in production can be simulated deterministically in tests.
     A test harness can control the order of events, inject failures, and verify invariants - all without mocking the runtime.

     Bridje's proc/select model aims to make this the default, not a special testing mode.

## Inspiration: Allium and "Out of the Tar Pit"

[Allium](https://github.com/juxt/allium) is a behavioural specification language developed at JUXT - it takes informal specs and gives them a more formal structure.
It's been a major inspiration for Bridje, particularly in how it strips a complex domain down to its essence: the types, the rules, and the invariants - nothing more.

Moseley & Marks' ["Out of the Tar Pit"](http://curtclifton.net/papers/MosessleyMarks06a.pdf) makes the same argument from a different angle: most of the complexity in software is incidental (I/O, state management, concurrency plumbing), and the essential complexity - the actual domain logic - is surprisingly small once you separate it out.

Bridje takes this seriously.
The aim is that Bridje code reads like a spec - the domain logic is front and centre, and the incidental machinery is factored out to the edges.

Where Bridje differs from Allium:

- **Allium is deliberately non-executable** - it specifies *what* should be true, not *how* to make it true.
  Bridje is a full programming language - it has to actually run.
  The challenge is keeping the incidental cost of executability low enough that the essential logic still reads clearly.

- **Bridje is statically type-checked** - the domain model (entities, variants, value types) is expressed in the type system, and the compiler catches mistakes.
  Allium captures this structure too, but as documentation rather than something a compiler enforces.

- **Bridje is simulation-testable** - because the concurrency model is expressed as inspectable data (the proc/select model), the same code that runs in production can be simulated deterministically in tests.
  Allium's invariants describe what should be true; Bridje aims to verify them automatically through property-based and simulation testing.

The ideal: one artifact that serves as spec, implementation, and test subject - not three separate documents that drift out of sync.

## A spoonful of sugar

Bridje is, at its core, a LISP.

That said, it has two syntactic sugars that make it feel like a more mainstream C/Java-style language, without compromising on its LISP foundations:

1. `foo(b, c)` and `a.foo(b, c)` method calls (desugars to `(foo b c)` and `(foo a b c)` respectively)
2. Colon blocks: a symbol suffixed with a colon (e.g. `def:`) starts a new block.
   Anything indented further than that symbol is included in the block.

   For example:

   ```bridje
   def: foo(a, b)
     let: [c add(a, b)]
       mul(c, 2)
   ```

   desugars to

   ```clojure
   (def (foo a b)
     (let [c (add a b)]
       (mul c 2)))
   ```

That is: you can write s-expressions, but I don't expect this to be the norm.

As an introduction, standard control flow operators therefore look like this:

```bridje
// function `foo` taking parameters `a` and `b`:

def: foo(a, b)
  <function body>

// `if`:
if: <pred>
  <then expr>
  <else expr>

// `let` bindings - pairs of symbols and expressions, binding `a` and `b` within `<body>`:

let: [a <expr>
      b <expr>]
  <body>

// `case` - deconstructs a tagged value
case: <expr>
  <match> <expr>
  ...
  <default?>

do:
  <expr>
  <expr>
```

# LICENSE

Bridje is licensed under the Mozilla Public License, version 2 or (at your option) any later version.

Copyright © 2026 James Henderson.
