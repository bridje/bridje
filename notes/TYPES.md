# Bridje Type System

Bridje is a statically typed Lisp on the JVM (Truffle/GraalVM).
Types are inferred; a `decl` at a definition is checked against what was inferred and becomes the exported type.
The system is built around **keys with global types, structural records, nominal tags, and enums as the sum type**, over an algebraic-subtyping core.
Traits are not built yet; where this document names them it says so.

The checker lives in `language/src/main/kotlin/brj/types`.
The design decisions behind it are recorded on [#129](https://github.com/bridje/bridje/issues/129).

## How inference works

**Every expression gets a typing: a result type, a demand on each free local, and the bounds of the variables it mentions.**
This is Dolan's algebraic subtyping (MLsub), restricted as the sections below say.

- **A type variable carries a lower bound and an upper bound**, not a single solution.
  What flows into it (a literal, a constructor call, another variable) is a lower bound; what is asked of it (a key read, a call, a declared parameter) is an upper bound.
  Every constraint is `lower ≤ upper`, decomposed structurally until it reaches a variable or fails.

- **Typings compose from their children alone.**
  Each occurrence of a local is its own variable and each sub-expression its own bound graph; two uses of one local meet in a fresh variable, the graphs union, and only the construct's own constraints are solved.
  Nothing is threaded through a definition, so the typing of an expression follows from its children's.

- **Generalisation happens at the top level only.**
  A `def` produces a scheme: the type plus the bounds of every variable it mentions.
  A use of the global instantiates the scheme with fresh variables, copying the bounds in.

- **There are no union or intersection types, and no top type.**
  A variable's lower bounds must join; two of different kinds do not, so `if: p 1 "s"` is an error (`Cannot join Int with Str`).
  Joins are per kind: records intersect their keys, two tags of one enum join to the enum, vectors join elementwise, host classes join along the class chain.
  The one intersection form is a variable met with a record shape or with non-nil, which is what `with` and nil narrowing produce.

- **`Nothing` is the bottom type and `Nothing?` is nil.**
  `throw` returns `Nothing`, so it fits anywhere; `nil` fits anywhere nullable.

### How types render

A scheme renders as a leading vector of its quantified variables and constraints, then the type:

```
[a] Fn([[a]] a)                       // first
[a, b] Fn([Iterable(a), Fn([a] b)] [b])  // mapv
[a, ^(a, {})] Fn([Bool, a] a)         // fn: (p r) if: p with(r, .dirty true) r
[a, ^(Int, a)] Fn([Bool, a] a)        // fn: (p x) if: p x 1
Fn([Form, [Form]] List)               // the when macro
```

- **`^(lower, upper)` is a bound that could not be folded into the type.**
  `^(a, {})` says `a` is a record; `^(Int, a)` says an `Int` flows into `a`.
  The spelling is a placeholder (Q8 on #129).

- **A variable that only ever flows out shows as what flows in**, and `Nothing` when nothing does.
  One that only ever flows in shows as its one demand.
  Nil in a variable's lower bound shows as `a?` where the variable appears positively.

- **Two uses of one local that always travel together are one variable**, which is how `(r ∧ {dirty}) ∨ r` above reads as `a`.

## Type Syntax

Types appear in `decl` forms.

### Primitives and named types

```bridje
decl: x Int
decl: name Str
decl: role ServerRole
```

`Int`, `Double`, `BigInt`, `BigDec`, `Str`, `Bool`, `Bytes`, `Form`.

### Functions

Parentheses after the name denote parameters; the final position is the return type:

```bridje
decl: foo(Int) Str                    // Int -> Str
decl: bar(Int, Str) Bool              // Int, Str -> Bool
```

Anonymous function types use `Fn`:

```bridje
decl: callback Fn([Int, Str] Bool)    // a function value, not a named function
```

### Type variables

Type variables are named in a leading vector:

```bridje
decl: [a] identity(a) a
decl: [a, b] mapv(Iterable(a), Fn([a] b)) [b]
```

A declared variable is rigid: the definition must be at least as general as the declaration, and the declaration is what callers see (D29 on #129).
An annotation can narrow a type but never widen it.

### Collections

Literal syntax mirrors value literals:

```bridje
decl: nums [Int]
decl: ids #{Str}
```

`Map(k, v)` is not built yet.

### Records

A record type names the keys a value must carry:

```bridje
decl: person {.name, .age}            // a record with at least .name and .age
decl: user {.name, .?email}           // .email may be present; the type does not demand it
```

Each key must already be declared; the type it holds comes from that declaration.
`.?email` is documentation: the checker neither demands the key nor lints its absence (D22 on #129).

### Tags and enums

A tag or enum name, with type arguments where it has parameters:

```bridje
decl: user User
decl: unwrap(Maybe(Int)) Int
```

`User({.fn, .ln})`, a tag with a required shape, is not built yet.

### Nullable

`?` suffix makes a type nullable:

```bridje
decl: name Str?                       // Str or nil
decl: user User?                      // User or nil
```

### Nothing

```bridje
decl: throw(Str) Nothing              // never returns
```

`Nothing?` is the type of `nil` itself.

## Nullability

Any type can be made nullable with `?`.
Non-nullable is the default.

`nil` is a subtype of every nullable type and of nothing else, so passing a `Str?` where a `Str` is demanded is an error (`Str? is nullable, but must not be`).

A `case` with a `nil` branch narrows a catch-all binding past nil:

```bridje
case: name
  nil "anonymous"
  n n                                  // n : Str, given name : Str?
```

## Keys

Keys are the atoms of the record system.
A key has a globally fixed value type, declared once.

```bridje
decl: .name Str, .age Int, .email Str
```

`.name` means `Str` everywhere.
If you need a different type, use a different key.
This follows the clojure.spec school of thought: a fully-qualified key has one meaning.

- **`.name` demands a record carrying `.name` and yields a `Str`.**
  Reading a key a record may not carry is an error (`{} lacks {.name}`).

- **`.?name` accepts any record and yields a `Str?`.**

- **A record literal checks each value against its key's type**, so `{.name 42}` is an error.

## Records

A record type is the set of keys a value is known to carry; the value types come from the key declarations.

Records are **structural**: a function that reads `.fn` and `.ln` accepts any record carrying them, whatever else it carries.
More keys is more specific: `{.name, .age, .email}` is a subtype of `{.name, .age}`.

- **A join keeps the shared keys.**
  `if: p {.a 1, .b 2} {.a 1, .c 3}` is a `{.a}`; reading `.b` off it is an error and `.?b` is fine.

- **`with` adds keys to whatever it was given.**
  `with(r, .dirty true)` on a parameter `r` is `r ∧ {.dirty}`: still `r`, now known to carry `.dirty`.
  This is why `if: p with(r, .dirty true) r` types as `[a, ^(a, {})] Fn([Bool, a] a)` and not as an error: one arm is a narrowing of the other, and the join absorbs it.

- **`set` mutates in place and yields the old value, or nil.**
  The record's type is unchanged: `set` on a record not known to carry the key is fine, and reading the key afterwards still needs `.?`.

- **A function whose last parameter is a record may be called without it**, and the callee sees an empty record.
  This is the trailing-options convention; `.?opt` is how such a parameter is read.

## Tags

A tag is a nominal constructor with a payload.
A tag is distinct from every other tag.

```bridje
tag: Pair(first, second)              // two untracked fields
tag: [t] Wrapper(t)                   // one field of the tag's type parameter
tag: Failure{.form, .message}         // record-style: fields are keys, typed by their declarations
tag: Nothing                          // nullary: a singleton value
```

- **A field named after a type parameter has that parameter's type.**
  `Wrapper("s")` is a `Wrapper(Str)`, and a `case` binding on it yields a `Str`.

- **A record-style tag's fields are typed by their keys**, and the tag registers those keys.
  Construction is positional, `Failure(form, message)`, and a `case` binds the fields positionally.
  Reading a record-style tag's payload as a record (D24 on #129) is not built yet.

- **A field that is neither is untracked**: any value goes in, and a `case` binding on it is unconstrained.

- **A bare pattern matches the tag and ignores its payload**: `case: x Pair 1 _ 2`.

### Enums

An enum declares a fixed set of tags.
It is the sum type: a join of two tags of one enum is the enum, and two tags of different enums do not join.

```bridje
enum: ServerRole
  tag: Follower{.knownLeader}
  tag: Candidate{.votesReceived}
  tag: Leader{.nextIndex, .matchIndex}

enum: Maybe(a)
  tag: Just(a)
  tag: Nothing
```

- **A constructor is typed as its tag**, so a function that only ever returns `Ok` is typed as returning `Ok`, and one that returns `Ok` or `Err` as returning `Result`.
  A tag is a subtype of its enum.

- **A `case` over an enum must handle every variant or have a default.**
  Missing variants are an error (`non-exhaustive case: missing Err`).

- **A catch-all binding is narrowed to what remains.**
  In `case: m Just(v) v other other`, `other` is a `Nothing`.

- **Two standalone tags cannot be cased together without a default.**
  `case: x A 1 B 2` asks for a value that is an `A` or a `B`, and there is no such type; declare an enum.
  With a default the case demands nothing of `x`.

### Forms

The reader's forms are an enum, `Form`, whose variants are the `brj.rdr` tags: `SymbolForm`, `List`, `Vector`, `Int`, `String`, and so on.
A `case` over a form therefore needs a default or all fifteen variants.

A macro is a function over forms: each parameter is a `Form`, a rest parameter is a `[Form]`, and the result is a `Form`.
`when` types as `Fn([Form, [Form]] List)`.

## Anomalies

An anomaly is a tag over a record of details: `Incorrect({.exnMessage "..."})`.
Its keys are optional, so a caught anomaly's message is read with `.?exnMessage`.

## Java Interop

A host class is imported and its members declared in Bridje syntax; the checker trusts the declarations.

```bridje
ns: example
  import:
    java.time:
      as(Instant, Inst)

decl: Inst/now() Inst
decl: Inst/.toEpochMilli() Int
decl: [a] AL/.add(a) Bool
```

- **Host type arguments are invariant.**

- **Two host types join along the class chain**, to the nearest declared common superclass; interfaces are not consulted.

### Protocol types

`Iterable(a)` and `Iterator(a)` are Truffle interop capabilities, not Java classes.

```bridje
decl: [a] itr(Iterable(a)) Iterator(a)
decl: [a] itrHasNext(Iterator(a)) Bool
decl: [a] itrNext(Iterator(a)) a
```

`[a]` and `#{a}` are subtypes of `Iterable(a)`, and `java.lang.Iterable` and `java.util.Iterator` of `Iterable(a)` and `Iterator(a)`.
These relationships live in the checker, not in the class hierarchy.

## Subtyping

`A ≤ B` means an `A` can be used wherever a `B` is expected.

```
Nothing   ≤  every type
nil       ≤  T?                    for any T
T         ≤  T?
{k}       ≤  {k2}                  iff k2 ⊆ k
Tag       ≤  Enum                  its enum
[a]       ≤  Iterable(a)
a ∧ {k}   ≤  a                     a narrowing of a variable
```

Functions are contravariant in their parameters and covariant in their result.
Vectors, sets and enum arguments are covariant; host type arguments are invariant.

## Effects

Effects are lexically scoped values, declared with `defx` and bound with `withFx`.

```bridje
defx: log(Str) Nothing?
defx: stdio(Str) Nothing? println

withFx: [log fn: logger(msg) stdio("LOG: ${msg}")]
  doWork()
```

A `defx` declares the effect's type; `withFx` checks each bound value against it.
Effect inference (which effects an expression uses) is a separate analysis and not part of the type checker.

## Not built yet

- **Traits**, as constraints on type variables or as interfaces.
- **Tags with a required shape** in a type form, `User({.fn, .ln})`.
- **Record-payload transparency** for record-style tags (D24 on #129).
- **`Map(k, v)`, `Long`, and numeric widening.**
- **User-declared variance.**
