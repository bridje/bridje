# Bridje Type System

Bridje is a statically typed Lisp on the JVM (Truffle/GraalVM).
Types are inferred by default; annotations are available at boundaries when they help.
The type system is built around **structural records, nominal tags, open traits, and closed sums**.

## Type Syntax

Types appear in `decl` forms (top-level and within traits) and optional annotations.

### Primitives and named types

```bridje
decl: x Int
decl: name Str
decl: role ServerRole
```

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

### Collections

Literal syntax mirrors value literals:

```bridje
decl: nums [Int]
decl: ids #{Str}
decl: lookup Map(Str, Int)            // Map — no literal type syntax
```

### Records

Record types list their required and optional keys:

```bridje
decl: person {.name, .age}            // record with at least .name and .age
```

### Tags

A tag name alone, or with a record shape constraint:

```bridje
decl: user User                       // any User
decl: user User({.fn, .ln})           // a User, only requiring .fn and .ln
```

### Nullable

`?` suffix makes a type nullable:

```bridje
decl: name Str?                       // Str or null
decl: user User?                      // User or null
```

### Nothing

```bridje
decl: throw(Str) Nothing              // never returns
```

`Nothing?` is the type of `null` itself.

### Union (sum types)

Named sum types are referenced by name:

```bridje
decl: role ServerRole                 // Follower | Candidate | Leader
decl: role ServerRole                 // Follower | Candidate | Leader
```

### Type variables

Lowercase names are type variables:

```bridje
decl: identity(a) a
decl: map([a], Fn([a] b)) [b]
```

### Trait constraints

Trait constraints are inferred — the compiler calculates them from usage.
Users rarely need to write them explicitly.
Exact syntax for explicit trait constraints on type variables is TBC.

## Generics

Types can be parameterised by type variables.
Lowercase names in type positions are type variables; uppercase names are concrete types.

```bridje
tag: Ok(a)
tag: Err(e)

type: Result(a, e)
  Sum: Ok(a) | Err(e)

tag: Pair(a, b)
```

Collection types are generic: `[a]`, `#{a}`, `Map(k, v)`.

Generic functions are inferred — the compiler determines type variables from usage:

```bridje
def: first(xs)          // inferred: [a] -> a
  nth(xs, 0)

def: pair(a, b)         // inferred: (a, b) -> Pair(a, b)
  Pair(a, b)
```

## Primitive Types

### Numeric

`Int` (32-bit), `Long` (64-bit), and `Double` (64-bit float) are the main numeric types.
`Byte`, `Short`, and `Float` exist for JVM interop but are not promoted.

### Other primitives

- `Bool`
- `Str`

### Core value types

These are immutable, identity-free types in `brj.core`, treated as effectively primitive:

- Java 8 time types: `Instant`, `Duration`, `LocalDate`, `LocalDateTime`, etc.
- `UUID`

## Collection Types

Homogeneous, immutable, parameterised by element type:

- `[a]` (Vec) — ordered sequence. Literal syntax: `[1, 2, 3]`
- `#{a}` — unordered, unique elements. Literal syntax: `#{1, 2, 3}`
- `Map(k, v)` — key-value mapping. No literal syntax (records use `{}`); constructed via API.

Element types are inferred from literals.
Empty literals (`[]`, `#{}`) infer the element type from usage context.

## Function Types

`Fn([a, b] c)` — a function taking `a` and `b`, returning `c`.
Functions are first-class values.

Named function declarations use `()`: `decl: foo(Int, Str) Bool`.
Anonymous function types use `Fn` with `[]`: `Fn([Int, Str] Bool)`.
The `[]` mirrors the `fn` value syntax: `fn: [a, b] ...`.

## Nullability

Any type can be made nullable with `?`: `Int?`, `Str?`, `User?`.
Non-nullable types are the default.

`nil` is the null literal. Its type is `Nothing?` — it is a subtype of any nullable type.
(Bridje uses `nil` where other languages write `null`.)

## Nothing

`Nothing` is the bottom type.
No value has this type.
It is the type of expressions that never return: `throw(...)`, infinite loops, process exit.

`Nothing` is a subtype of every type, which allows non-returning expressions in any position:

```bridje
let: [config
      if: exists?(configFile)
        loadConfig(configFile)
        throw(ConfigError("not found"))]  // Nothing < Config, so this typechecks
```

`Nothing?` is the type of `null` itself — the nullable bottom type.

## Keys

Keys are the atoms of the record system.
A key has a globally fixed value type, declared once.

```bridje
decl: .name Str, .age Int, .email Str
```

`.name` means `Str` everywhere.
If you need a different type, use a different key.
This follows the clojure.spec school of thought: a fully-qualified key has one meaning.

## Records

A record is a set of key-value pairs.
Record types track which keys are present; the value types come from the key declarations.

Records are **structural** — any record with at least the required keys is accepted:

```bridje
def: displayName({fn, ln})
  "${fn} ${ln}"

// Accepts any record with .fn and .ln, regardless of other keys
displayName({.fn "James", .ln "Henderson"})
displayName({.fn "James", .ln "Henderson", .email "j@h.com"})
```

More keys = more specific = subtype.
`{.name, .age, .email}` is a subtype of `{.name, .age}`.

## Tags

Tags are nominal wrappers around records.
A tag is distinct from any other tag, even with identical keys.

```bridje
tag: User({.fn, .ln, .email, .role})
tag: Customer({.fn, .ln, .email, .since})
```

`User` is not `Customer`, even though they share keys.
The tag carries domain identity.

### Tags are subtypes of their underlying record shape

A tagged record is more specific than its untagged equivalent.
`User({.fn, .ln})` is a subtype of `{.fn, .ln}`.

This means functions can choose their level of specificity:

```bridje
// Structural — accepts any record with .fn and .ln
def: displayName({fn, ln})
  "${fn} ${ln}"

// Nominal — must be a User, only needs .fn and .ln
def: userDisplayName(User({fn, ln}))
  "${fn} ${ln}"

// Both of these work with displayName:
displayName({.fn "James", .ln "Henderson"})
displayName(User({.fn "James", .ln "Henderson", .email "j@h.com"}))
```

The tag asserts domain identity.
The keys assert shape.
They are independently specified at each usage site.

### Tag-level type precision

The compiler tracks individual tags, not just their containing sum type.
A function that only returns `Ok` is typed as returning `Ok`, not `Result`.

```bridje
// Inferred return type: Ok(a)
def: lookup(m, k)
  Ok(get(m, k))

// Inferred return type: Ok(a) | Err(Str)
def: safeLookup(m, k)
  if: hasKey(m, k)
    Ok(get(m, k))
    Err("key not found")
```

## Closed Sum Types

A closed sum declares a fixed set of variants.
Each tag belongs to exactly one closed sum (1:N).

```bridje
type: ServerRole
  Sum:
    Follower({.knownLeader})
    Candidate({.votesReceived})
    Leader({.nextIndex, .matchIdx})
```

The compiler infers the sum type from its members — seeing `Follower` is enough to know `ServerRole`.
Pattern matching is exhaustive against the declared set.

1:N is necessary for inference.
If a tag could belong to multiple closed sums, the compiler couldn't determine which sum type to infer.

## Traits

Traits are interfaces — named sets of method declarations.
At runtime, a trait impl is a record of functions on the type's meta-object.

```bridje
trait: Show
  decl: show() Str

impl: Show(Int)
  def: show(it) intToStr(it)

impl: Show(User)
  def: show(User({fn, ln})) "${fn} ${ln}"
```

The `impl` names the type; the receiver is a parameter like any other.
Destructuring works in the receiver position, same as any function parameter.

Traits are open — any tag can implement any number of traits (M:N).
This contrasts with closed sums (1:N).

### Resolution

Trait impls live on the type's meta-object — fixed, one per type, not overridable.


## Subtyping

`A < B` means A is a subtype of B — an A can be used wherever a B is expected.
More specific = subtype.

```
Nothing   <  every type
Nothing?  <  every nullable type
nil       <  T?                    for any T
T         <  T?
Tag({k})  <  {k}                   tagged record < underlying record shape
{k}       <  {k2}                  iff k2 ⊆ k (more keys = more specific)
Ok        <  Ok | Err              fewer variants = more specific
```

## Records and Tags: The Duality

Records and tags are duals in the subtyping lattice:

- Records grow more specific by **adding keys**.
- Tags grow more specific by **removing variants**.
- `set!` mutates a record field in place, adding a key to its type (more specific).
- `case` removes a variant from a tag type (more specific).

Keys are structural and shared across record shapes (M:N).
Tags are nominal and belong to one closed sum (1:N), but can implement multiple traits (M:N).

## Effects

Effects are orthogonal to traits.
They are lexically scoped values — like Clojure's dynamic vars but entirely lexical.

### Declaring effects

An effect is declared as a global variable with a type:

```bridje
defx: log Fn(Str)                      // no default — must be provided by enclosing scope
defx: stdio Fn(Str) println             // has a default
defx: net RaftNetwork                   // can be any type — a trait, a function, a record
```

If a `defx` has no default, it must be provided by an enclosing `withFx` or the compiler reports an error.

### Using effects

Effect variables are used like any other value:

```bridje
def: doWork()
  log("starting")
  // ...
```

### Providing effects

`withFx` binds effect values into lexical scope:

```bridje
withFx: [log fn: [msg] stdio("LOG: ${msg}")]
  doWork()
```

Effect impls can use other (typically lower-level) effects.
This replaces a higher-level effect with a lower-level one in the inferred effect set.

In this example, `doWork` uses `{log}`.
The `withFx` satisfies `log` but its impl uses `stdio`, so the effect set of the whole expression is `{stdio}`.

### Effect inference

The compiler fully infers the effect set of every expression.
Users do not annotate effects — the compiler calculates them.

```bridje
def: doWork()                   // inferred effects: {log}
  log("starting")

def: main()                     // inferred effects: {stdio}
  withFx: [log fn: [msg] stdio("LOG: ${msg}")]
    doWork()
```

