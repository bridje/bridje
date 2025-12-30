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

   - types: the age-old debate.
     Both sides have very valid points: static-types advocates are correct in saying that a compile-time type-checker eliminates a class of bugs; dynamic-types folks are also correct in saying that a type-checker (especially as implemented in the main programming languages) requires too much up-front design and speculation, as well as introducing unnecessary rigidity into the code.

     Bridje aims to find another point on the solution spectrum: the relative safety of a compile-time type-system but without hindering progress - instead, reflecting the natural types of your domain while still preventing mistakes.
   - side effect management: Clojure (and FP, more broadly) has patterns and idioms here which make this simpler - 'functional core, imperative shell' - but I'd like Bridje to help me out here in a similar way to how a type-system helps with types, by helping its users track what side-effects each function relies on, and allowing them to be substituted easily for testing.
   - concurrency: I have come to really appreciate Kotlin's 'structured concurrency' - knowing that threads are carefully managed, exceptions propagated etc has been a boon.

     I have found that systems that work through 'communicating sequential processes' (CSP) have often been easier to reason about, and, as a result, contain fewer bugs.
     Again, Bridje will support this as a core pattern.

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

Copyright © 2025 James Henderson.
