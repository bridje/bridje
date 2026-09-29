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
  Joins are per kind: records intersect their keys, two tags of one enum join to the enum, vectors join elementwise, host classes join along the class chain, and vectors, sets and iterable host classes that are not one constructor join to an `Iterable`.
  The one intersection form is a record on a base, `{.k & t}`: a value of `t` known to carry `.k` as well, which is what `with` produces.

- **`Nothing` is the bottom type and `Nothing?` is nil.**
  `throw` returns `Nothing`, so it fits anywhere; `nil` fits anywhere nullable.

### How types render

A scheme renders as a leading vector of its quantified variables and constraints, then the type:

```
[a] Fn([[a]] a)                       // first
[a, b] Fn([Iterable(a), Fn([a] b)] [b])  // mapv
[a] Fn([Bool, {& a}] a)               // fn: (p r) if: p with(r, .dirty true) r
Fn([Bool, Int] Int)                   // fn: (p x) if: p x 1
[a, isa([Int], a)] Fn([Bool, a] a)    // fn: (p x) if: p x [1]
Fn([Form, [Form]] List)               // the when macro
```

- **A bound the type can say is written in it.**
  - A record demanded of a variable goes on the occurrences it is taken in at, `{& a}`: `a`, which is a record.
  - A primitive bound is the primitive itself: joins are per kind, so `x ∨ Int` only exists where `x` is an `Int`.

- **`isa(lower, upper)` is a bound that could not be folded into the type**, read as `lower` is a subtype of `upper`.
  `isa([Int], a)` says an `[Int]` flows into `a`: the join depends on what `a` is, and there is no union to write it with.
  A bound another bound of the same variable implies is left out: with `isa(Form, a)`, `isa(List, a)` says nothing more.

- **A variable that only ever flows out shows as what flows in**, and `Nothing` when nothing does.
  One that only ever flows in shows as its one demand.
  Nil in a variable's lower bound shows as `a?` where the variable appears positively.

- **Two uses of one local that always travel together are one variable**, which is how `{.dirty & r} ∨ r` above reads as `a`.

- **A variable that reaches itself through the types it would stand for is kept**, so a recursive type renders as its variable and constraints.

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

A record type names the keys a value carries:

```bridje
decl: person {.name, .age}            // a record with at least .name and .age
decl: user {.name, .?email}           // .email may be present, at its declared type
decl: box {.value(Int)}               // .value, declared [a] .value a, holding an Int
decl: [a] touch({& a}) {.dirty & a}   // a record a, then that same a with .dirty
```

Each key must already be declared; the type it holds comes from that declaration.
A key declared with type variables takes its arguments, `.value(Int)`, or fresh ones, which the declaration quantifies.
A record type form is open: the value may carry keys it does not mention.

`& a` puts the keys on a type variable: `{.dirty & a}` is a value of `a` that also carries `.dirty`.
There is one base, and it is a variable.
A tag known to carry keys beyond its own is written on the tag instead, `User{.email}`.

### Tags and enums

A tag or enum name, with type arguments where it has parameters:

```bridje
decl: user User
decl: unwrap(Result(Int, Str)) Int
decl: user User{.email}               // a User whose record is known to carry .email as well
```

The keys after a tag or enum are those it carries beyond its own; naming one of its own adds nothing, so `User{.name}` is `User` where `User` declares `.name`.
A tag or enum named without its type arguments has fresh ones, which the declaration quantifies: `decl: size(Maybe) Int` is `[a] Fn([Maybe(a)] Int)`.

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

`ifLet` narrows its binding past nil:

```bridje
ifLet: [n name]
  n                                    // n : Str, given name : Str?
  "anonymous"
```

The binding is not an intersection: the value is taken as a `b?` and the binding is the `b`.
`fn: f(x) ifLet: [y x] y 0` types as `Fn([Int?] Int)`.
`case` matches tags only, so it never narrows past nil.

## Keys

Keys are the atoms of the record system.
Every key is declared with its type, once, before a tag, a record literal or a read uses it.

```bridje
decl: .name Str, .age Int, .email Str
```

`.name` means `Str` everywhere.
If you need a different type, use a different key.
This follows the clojure.spec school of thought: a fully-qualified key has one meaning.

- **An undeclared key is an error**: `decl: .name` alone is `needs a type`, and a tag over an undeclared key is `.name has no declared type: decl: .name <type>`.

- **A key's type may name variables**, and each record then holds the key's value at an instance of its own:

  ```bridje
  decl: [a] .value a
  ```

  - `{.value 1}` is a `{.value(Int)}`, and `.value` read off it is an `Int`.
  - A tag gives the instance through its type arguments, `tag: [a] Box{.value(a)}`, so `.value(Box{.value "s"})` is a `Str`.
  - The key's variables are its arguments in the order they are declared, `decl: [a, b] .k Fn([b] a)` taking `.k(A, B)` for `Fn([B] A)`.

- **`.name` demands a record carrying `.name` and yields a `Str`.**
  Reading a key a record may not carry is an error (`{} lacks {.name}`).

- **`.?name` accepts a record that may carry `.name` and yields a `Str?`.**
  A key with type variables is read at the instance the record's type holds it at, `{.?value(Int)}` giving an `Int?`. A record that does not mention the key has no instance to give: a closed one lacks it, so `.?value` is `nil`, and an open one may carry it at any type, so `.?value` on it is an error (`may carry .value at any type`).

- **A record literal checks each value against its key's type**, so `{.name 42}` is an error.

## Records

A record type is the keys a value carries: each certainly, `.k`, or perhaps, `.?k`, and at an instance where the key has type variables, `.k(Int)`. The value types come from the key declarations.

Records are **structural**: a function that reads `.fn` and `.ln` accepts any record carrying them, whatever else it carries.
More keys is more specific: `{.name, .age, .email}` is a subtype of `{.name, .age}`.

- **A record literal is closed, and a type form open.**
  `{.a 1}` carries `.a` and nothing else, while a value of `{.a}` may carry any other key. A closed record is a subtype of the open one with the same keys, and not the other way.
  Rendered types do not show which a record is.

- **A join keeps the shared keys, and those only one side carries as optional where the other is closed.**
  `if: p {.a 1, .b 2} {.a 1, .c 3}` is a `{.a, .?b, .?c}`: reading `.b` off it is an error, and `.?b` is an `Int?`.
  An open side may carry a key at any instance, so a join with one keeps only the keys both carry, and those of one type.

- **`with` adds keys to whatever it was given.**
  `with(r, .dirty true)` on a parameter `r` is `{.dirty & r}`: still `r`, now known to carry `.dirty`.
  This is why `if: p with(r, .dirty true) r` types as `[a] Fn([Bool, {& a}] a)` and not as an error: one arm is a narrowing of the other, and the join absorbs it.
  A key with type variables is given a new instance by `with`, so there the join keeps both arms, and joining two withs of it on one variable where only one gives the key is an error.

- **A function whose last parameter the empty record satisfies may be called without it**, and the callee sees an empty record.
  This is the trailing-options convention; `.?opt` is how such a parameter is read, and `.foo()` is `function arity mismatch: 1 vs 0`, as `{}` has no `.foo`.
  An argument beyond a function's parameters must be a record, and the callee ignores it.

- **`meta(x)` is `{}`: a record nothing is known to carry.**
  Its keys are read with `.?`, `rdr/.?loc(meta(form))`, and `rdr/.loc(meta(form))` is an error.

## Tags

A tag is a name over one record, or over none.
A tag is distinct from every other tag.

```bridje
decl: [a] .value a
tag: [a] Box{.value(a)}               // .value instantiated at the tag's parameter
tag: Failure{.form, .message}         // keys typed by their own declarations
tag: Nothing                          // nullary: a singleton value
```

- **A tag's keys hold their declared types**, and `.value(a)` instantiates a key's type variables at the tag's.
  `Box{.value "s"}` is a `Box(Str)`, and `case: b Box{value} value` yields a `Str`.
  A tag's keys are declared beforehand, and a key with type variables takes them from the tag: `tag: Box{.value}` is `.value is declared with type variables: give them, .value(a)`.

- **A tag's type arguments vary as the keys they instantiate do.**
  Under `decl: [a] .value a`, `Box(a)` is covariant; under `decl: [a] .h Fn([a] Bool)`, `tag: [a] H{.h(a)}` is contravariant, as an `H` over a function taking `{.x}` cannot stand in for one over a function taking `{}`.
  An argument no key uses, as a nullary variant's, says nothing: `Nothing(Int) ≤ Maybe(Str)`.

- **A tag is a record carrying its keys**, so a `Failure` passes where `{.form}` is demanded and `.form(f)` reads it.

- **A tag's type records the keys it is known to carry beyond its own.**
  - `User{.name "a", .email "b"}` is a `User{.email}`, and `with(u, .email "b")` on a `User` is one too.
  - They are width-subtyped as a record's keys are: `User{.email} ≤ User`, and not the other way.
  - A join keeps the keys both sides carry, and as a record's does, those one carries where the other is closed: `User{.name "a"}` or `User{.name "a", .email "b"}` is a `User{.?email}`.
  - A demand for a key a tag does not declare is met by a tag known to carry it: `User ∧ {.email}` is `User{.email}`.
  - `with` giving a tag's own key with type variables a new instance would change the tag's arguments, so it yields the record without the name: `with(Box{.value 1}, .value "s")` is a `{.value(Str)}`.

- **Constructing a tag demands its keys**: `Pair{.fst 1}` is an error (`{.fst} lacks {.snd}`).

- **A pattern binds the tagged value, `Box(r)`, or destructures its record, `Box{value}`**; a bare pattern matches the tag and ignores its record: `case: x Pair 1 _ 2`.
  `r` is the `Box` the pattern matched, so `with(r, .value 2)` is a `Box` too.

### Enums

An enum declares a fixed set of tags.
It is the sum type: a join of two tags of one enum is the enum.
Two tags of different enums join to the record of the keys they share, as a record is a tag with no name: `Ok{.value 1}` or `Just{.value 2}` is a `{.value}`.
A tag with no record, such as `Nothing`, joins with no other tag.

```bridje
enum: ServerRole
  tag: Follower{.knownLeader}
  tag: Candidate{.votesReceived}
  tag: Leader{.nextIndex, .matchIndex}

enum: Result(a, e)
  tag: Ok{.value(a)}
  tag: Err{.error(e)}
```

- **A constructor is typed as its tag**, so a function that only ever returns `Ok` is typed as returning `Ok`, and one that returns `Ok` or `Err` as returning `Result`.
  A tag is a subtype of its enum.

- **A `case` over an enum must handle every variant or have a default.**
  Missing variants are an error (`non-exhaustive case: missing Err`).

- **A catch-all binding is narrowed to what remains.**
  In `case: r Ok{value} value other other`, `other` is an `Err`.

- **Two standalone tags cannot be cased together without a default.**
  `case: x A 1 B 2` asks for a value that is an `A` or a `B`, and there is no such type; declare an enum.
  With a default the case demands nothing of `x`.

### Forms

The reader's forms are an enum, `Form`, whose variants are the `brj.rdr` tags: `SymbolForm`, `List`, `Vector`, `Int`, `String`, and so on.
A `case` over a form therefore needs a default or all fifteen variants.

A macro is a function over forms: each parameter is a `Form`, a rest parameter is a `[Form]`, and the result is a `Form`.
`when` types as `Fn([Form, [Form]] List)`.

## Anomalies

An anomaly is a tag over a record of details: `Incorrect{.exnMessage "..."}`.
Its keys are optional, so a caught anomaly's message is read with `.?exnMessage`.

The anomaly tags are the variants of the enum `Anomaly`: `throw` takes one, `throw("boom")` is `Str is not a subtype of Anomaly`, and a catch-all binding, `catch: e …`, is an `Anomaly`, as a host exception is caught as a `Host`.

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

- **Two host types join when one is the other's superclass**, to the superclass.
  Otherwise two iterable ones join to an `Iterable` (`ArrayList(Int) ∨ LinkedList(Int)` is `Iterable(Int)`), and any others do not: Java's interfaces give no one least upper bound.

- **A generic class named without its arguments, `AL`, is compatible with any arguments**, as a Java raw type is: it has fresh ones wherever it is compared.

### Protocol types

`Iterable(a)` and `Iterator(a)` are Truffle interop capabilities, not Java classes.

```bridje
decl: [a] itr(Iterable(a)) Iterator(a)
decl: [a] itrHasNext(Iterator(a)) Bool
decl: [a] itrNext(Iterator(a)) a
```

`[a]` and `#{a}` are subtypes of `Iterable(a)`, and `java.lang.Iterable` and `java.util.Iterator` of `Iterable(a)` and `Iterator(a)`.
Two of them that are not one constructor join to the protocol type: `[Int] ∨ #{Int}` is `Iterable(Int)`.
These relationships live in the checker, not in the class hierarchy.

## Subtyping

`A ≤ B` means an `A` can be used wherever a `B` is expected.

```
Nothing      ≤  every type
nil          ≤  T?                    for any T
T            ≤  T?
{K}          ≤  {K2}                  every key K2 names K carries: certainly where K2 demands it, at an instance below K2's
{K} closed   ≤  {K2}                  as above, and a key K2 names perhaps, `.?k`, may be missing from K
{K}          ≤  {K2} closed           only a closed {K} mentioning no key K2 does not
Tag{K}       ≤  Enum{K2}              its enum, its own keys with K
Tag{K}       ≤  {K2}                  a record is a tag with no name
Tag(A)       ≤  Tag(B)                as the keys A instantiates vary
[a]          ≤  Iterable(a)
{K & a}      ≤  a                     where K's keys have one type each
```

Functions are contravariant in their parameters and covariant in their result.
Vectors and sets are covariant, a tag's and an enum's arguments vary as their keys do, and host type arguments are invariant.

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
- **`Map(k, v)`, `Long`, and numeric widening.**
