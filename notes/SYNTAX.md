# Bridje Syntax

Bridje is a LISP.
Everything is an s-expression: `(operator arg1 arg2 ...)`.

## Primitives

```bridje
42                    // Int (64-bit integer)
3.14                  // Double (default float type)
42N                   // BigInt
3.14M                 // BigDec
"hello"               // Str
"hello ${name}"       // Str with interpolation
true                  // Bool
false
nil                   // null value, type Nothing?
:name                 // Member key — record key and first-class accessor function
```

## Collections

```bridje
[1, 2, 3]                        // Vec — ordered, homogeneous
#{1, 2, 3}                       // Set — unordered, unique, homogeneous
{:name "James", :age 30}         // Record — heterogeneous, keyed by members
```

Commas are whitespace everywhere — they're optional.

## Syntactic Sugar

Bridje has three sugars over its s-expression core.
All examples below use them, so they're introduced here first.

The goal is to bring LISP simplicity to a wider audience through syntax that looks like a C/Java-style language — "brackets where you'd expect them" — while retaining the benefits of a LISP core.
Critically, these are purely syntactic transformations.
The underlying language is still s-expressions, and the original s-expression syntax still works.
Idiomatic Bridje uses the sugared forms for the majority of calls.

### Call syntax

`foo(a, b)` desugars to `(foo a b)`.

In a LISP, the function goes inside the parentheses: `(foo a b)`.
Call syntax moves it outside: `foo(a, b)`.
This is the single change that makes LISP syntax feel familiar to mainstream programmers.

The transformation is simple: `symbol(a, b)` becomes `(symbol a b)`.

```bridje
add(1, 2)             // (add 1 2)
foo(a, b, c)          // (foo a b c)
println("hello")      // (println "hello")
```

Commas are optional here — `add(1 2)` === `add(1, 2)` but the latter is probably more familiar.

### Colon blocks

A symbol suffixed with `:` starts an indentation block.
Everything indented further than that symbol is included in the block.

This replaces the closing parentheses that make LISP code hard to read for newcomers.
Instead of counting parens, indentation determines structure — similar to Python, but only for block-opening forms.

```bridje
// Bridje:
def: foo(a, b)
  let: [c add(a, b)]
    mul(c, 2)

// Desugars to s-expression:
(def (foo a b)
  (let [c (add a b)]
    (mul c 2)))
```

The rule: `symbol:` opens a block.
The block contains everything on the same line after the colon, plus any indented lines below.
Nested colon blocks nest naturally via indentation.

```bridje
// Bridje:
if: gt(a, b)
  do:
    println("a wins")
    a
  b

// Desugars to:
(if (gt a b)
  (do
    (println "a wins")
    a)
  b)
```

### Curly-brace construction sugar

`Foo{...}` desugars to `Foo({...})`.

When a symbol is immediately followed by curly braces, it desugars to calling that symbol with a record argument.
This makes tagged record construction concise:

```bridje
Person{:name "James", :age 42}
// desugars to:
Person({:name "James", :age 42})
// which is:
(Person {:name "James" :age 42})
```

## Instance Members

Instance members use a dot prefix: `:name`, `:age`, `:toEpochMilli`.
They are first-class functions — `:name` is a function that takes a value and returns the named member.

```bridje
:name(person)                  // access :name on person — returns the value
:toEpochMilli(instant)         // call :toEpochMilli on instant — returns Int

// Members are just functions, so they compose with higher-order functions:
map(:name, people)             // extract :name from each person
filter(:?nickname, profiles)   // only those with :nickname present
```

Members from other namespaces are qualified with `/`: `I/:toEpochMilli`, `myns/:customField`.
The `/` separates the namespace, the `.` marks it as an instance member.

There is no postfix dot syntax (`a.b`).
Left-to-right chaining uses the threading macro `->`.

### Optional member access

Optionality is at the access site, not the definition (per Rich Hickey's "Maybe Not").
Any member can be accessed optionally — returning `nil` if absent:

```bridje
:?timeout(state)               // returns the value or nil
->: state :?timeout            // threaded form
```

The member `:timeout` is always `Duration` when present.
Whether it's required or optional depends on the context that accepts the value.

## Threading Macros

### ->

`->` is a macro (defined in `brj.core`, not syntactic sugar) that threads a value through a sequence of calls, inserting it as the first argument to each.
This is the primary way to chain operations left-to-right.

```bridje
// Without threading:
inc(div(count(cluster), 2))

// With threading:
->: cluster count() div(2) inc()

// macro-expands to:
inc(div(count(cluster), 2))
```

The rules follow Clojure's `->`:
- The first element is the seed — evaluated as-is, becomes the initial threaded value.
- A bare symbol or member (`:name`, `count`) is treated as a one-arg call: `:name(prev)`, `count(prev)`.
- A call form (`div(2)`, `:toUpperCase()`) gets the threaded value inserted as the first argument: `div(prev, 2)`, `:toUpperCase(prev)`.
- Prefer parens on function calls for clarity: `count()` rather than `count`.

```bridje
// Threading with instance members:
->: person :name :toUpperCase()

// macro-expands to:
:toUpperCase(:name(person))

// Mixing members and static functions:
->: state :log count() div(2) inc()
```

### ?>

`?>` is a nil-safe threading macro.
If any step produces `nil`, the entire chain short-circuits to `nil`.

```bridje
// Nil-safe chaining:
?>: state :?config :?timeout
```

## Core Forms

These are the fundamental expressions built into the language.

### def

Define a value or function.

```bridje
def: x 42

def: foo(a, b)
  add(a, b)

// latter desugars to:
(def (foo a b)
  (add a b))
```

### if

Three arguments: predicate, then, else.
Predicate must be a boolean.

```bridje
if: pred
  thenExpr
  elseExpr
```

### let

Sequential bindings — later bindings can reference earlier ones.

```bridje
let: [a expr1
      b expr2]
  body
```

Record destructuring works in let bindings.
Members are written without the `.` prefix when destructuring (they become local bindings):

```bridje
let: [{name, age} getUser()]
  println("${name} is ${age}")
```

### fn

Anonymous function.

```bridje
fn: [a, b] add(a, b)

fn: [a, b]
  let: [c add(a, b)]
    mul(c, 2)
```

Short form — always one parameter, named `it`:

```bridje
#: inc(it)
#: add(it, 1)
#: :name(it)
```

Since `:name` is already a first-class function, you can often use it directly instead of `#:`:

```bridje
map(:name, people)             // no need for #: :name(it)
```

### do

Sequential evaluation — returns the last expression.

```bridje
do:
  expr1
  expr2
```

### case

Pattern matching on tagged values.

Branches are pairs of forms: pattern then body expression.
Patterns can be:
- A tag with bindings: `Ok(v)`, `Follower({knownLeader})`
- `nil` for the null case
- A binding variable (lowercase): catches anything and binds it

If the number of forms is odd, the last is the default (like Clojure's `case`).

```bridje
case: expr
  Ok(v) handleOk(v)
  defaultValue              // last expression is the default
```

Exhaustive matching — when all variants are handled, no default is needed:

```bridje
case: :role(state)
  Follower(f) handleFollower(f)
  Candidate(c) handleCandidate(c)
  Leader(l) handleLeader(l)
```

Without a default, the compiler verifies all variants of the enum are handled.

Destructuring inside tags:

```bridje
case: result
  Ok({value, metadata}) process(value, metadata)
  Err({message, code}) log("Error ${code}: ${message}")
```

### set!

Mutate a record field in place.
Returns the old value (or `nil` if the member was not previously set).
Bridje is immutable by default, but mutation is available when performance requires it (consenting adults).

```bridje
set!(record, :key, value)
```

### quote, unquote, and unquote-splice

Code as data — but unlike Clojure, Bridje's quote returns **typed Form ADT objects**, not raw data structures.
This is because Bridje is fully type-checked: the quoted representation must be statically typed.

`'form` returns a Form object.
`~form` inside a quote evaluates that sub-expression and splices the result in.
`~@form` inside a quote evaluates a list and splices its elements in (unquote-splice).

```bridje
'foo                  // SymbolForm("foo")
'(1 2 3)             // ListForm([IntForm(1), IntForm(2), IntForm(3)])
'[a b c]             // VectorForm([SymbolForm("a"), SymbolForm("b"), SymbolForm("c")])
```

The Form ADT types are:
- `SymbolForm`, `IntForm`, `DoubleForm`, `BigIntForm`, `BigDecForm`, `StringForm`, `KeywordForm`
- `ListForm`, `VectorForm`, `SetForm`, `RecordForm`
- `QualifiedSymbolForm` for namespaced symbols
- `UnquoteForm` for `~expr` inside quotes
- `UnquoteSpliceForm` for `~@expr` inside quotes

Form constructors are available as functions: `Symbol("foo")`, `Int(42)`, `List([...])`, etc.
These are used in macro bodies to build forms programmatically.

Unquote splices evaluated expressions into quoted forms:

```bridje
let: [x 'foo]
  '(bar ~x)           // ListForm([SymbolForm("bar"), SymbolForm("foo")])
```

Unquote-splice expands a list into the surrounding form:

```bridje
let: [xs '(1 2 3)]
  '(foo ~@xs)         // ListForm([SymbolForm("foo"), IntForm(1), IntForm(2), IntForm(3)])
```

Desugaring happens before quoting — `'foo(a, b)` captures the desugared s-expression `(foo a b)`, not the sugared form.
This means macros always work with s-expressions, regardless of how the user wrote the code.

Auto-gensyms with `#` suffix prevent variable capture in macros:

```bridje
'(let [tmp# ~x] (add tmp# tmp#))
// Both occurrences of tmp# resolve to the same unique symbol
```

## Top-Level Forms

### decl

`decl` serves two purposes: declaring member keys and declaring type signatures.

#### Member key declarations

A dot-prefixed name declares an instance member with a globally fixed type.
A member has one meaning everywhere (the clojure.spec approach).

```bridje
decl: :name Str, :age Int

decl:
  :host Str
  :port Int
  :timeout Duration
```

Each declared member creates a callable accessor function (`:name`) and an optional variant (`:?name`).

#### Type declarations

A symbol or call form declares a type signature for a value or function.

```bridje
decl: x Int
decl: foo(Int, Str) Bool
decl: callback Fn([Int, Str] Bool)
decl: identity(a) a
decl: map([a], Fn([a] b)) [b]
```

### Records

Construction uses dot-prefixed members:

```bridje
{:name "James", :age 30}
```

Member access uses the accessor function:

```bridje
:currentTerm(state)            // access :currentTerm on state
:from(req)                     // access :from on req

// With threading:
->: state :currentTerm
```

Update with `with` — returns a new record, does not mutate:

```bridje
->: state with(:currentTerm newTerm, :votedFor nil)

// or without threading:
with(state, :currentTerm newTerm, :votedFor nil)
```

Destructuring in function parameters and `let` bindings.
Members are written without the `.` prefix in destructuring (they become local bindings):

```bridje
// Construction — dot prefix:
{:name "James", :age 30}

// Destructuring — no dot prefix (these become local bindings):
def: displayName({fn, ln}) "${fn} ${ln}"

def: greet({name, age})
  if: gt(age, 18)
    "Hello, ${name}"
    "Hey ${name}!"

let: [{name, email} getUser()]
  sendWelcome(name, email)
```

### tag

Nominal type — a thing you can construct and pattern match on.
A tag is distinct from any other tag, even with identical members.

Positional members (tuple-style):

```bridje
tag: Ok(a)
tag: Err(e)
tag: Pair(first, second)
tag: Nothing                   // nullary — a singleton value
```

Construction uses call syntax:

```bridje
Ok(42)
Pair(1, 2)
Nothing                        // singleton, no parens needed
```

<!-- TODO: tag with named members via {} — tag: User{:name, :age} -->
<!-- TODO: curly-brace construction sugar for named tags — User{:name "James"} -->

### enum

Closed sum type — a fixed set of variants.
Variants are constructors owned by the enum, not standalone types.
`Just(x)` has type `Maybe(a)`, not type `Just`.

```bridje
enum: ServerRole
  tag: Follower({:knownLeader})
  tag: Candidate({:votesReceived})
  tag: Leader({:nextIndex, :matchIdx})

enum: Result(a, e)
  tag: Ok(a)
  tag: Err(e)

enum: Maybe(a)
  tag: Just(a)
  tag: Nothing
```

Variant constructors are interned into the declaring namespace.
Pattern matching on enums is exhaustive — the compiler verifies all variants are handled.

This is a superset of Java enums — a Java-style enum is the degenerate case where all variants are nullary:

```bridje
enum: Direction
  tag: North
  tag: South
  tag: East
  tag: West
```

### trait

<!-- TODO: trait is not yet implemented -->

A named bundle of instance members — a structural type alias.
Any value with the right members satisfies the trait, no declaration of conformance needed.

```bridje
trait: Named{:name, :age}

trait: Temporal{:toEpochMilli, :isAfter}
```

Traits compose — a trait can include other traits and additional members:

```bridje
trait: Person{Named, :email}
// Person requires :name, :age (from Named), and :email
```

Trait subtyping is structural: if `A` has all the members of `B` and more, then `A` is a subtype of `B`.

Optional members in traits:

```bridje
trait: Profile{:name, :?nickname}      // :name required, :nickname optional
```

### impl

<!-- TODO: impl is not yet implemented -->

Implement methods for a tag.
The member name comes first; the receiver is a parameter like any other.

```bridje
impl: User
  def: :greet(this)
    "Hello, ${:name(this)}"

  def: :fullName(this)
    "${:firstName(this)} ${:lastName(this)}"
```

Methods are instance members — available via `:greet(user)` or `->: user :greet()` after implementation.

The prototypical delegation model: member lookup checks the instance (the record data) first, then falls through to the tag's implementations.
A record member can shadow a tag method.

### defmacro

Compile-time macro.
Receives unevaluated Form objects as arguments, must return a Form.
Supports variadic parameters with `&` rest syntax.

Because Bridje's quote returns typed Form ADT objects (not raw data), macros manipulate Form values directly:

```bridje
// Simple macro using quote/unquote:
defmacro: unless(cond, body)
  '(if ~cond nil ~body)

// Variadic macro using & rest params and ~@ unquote-splice:
defmacro: when(cond, & body)
  '(if ~cond (do ~@body) nil)

// Macro that manipulates Form objects directly:
defmacro: ifLet(bindings, then, else)
  let: [bvec first(bindings)]
    '(let [v# ~nth(bvec, 1)]
       (case v#
         nil ~else
         ~first(bvec) ~then))
```

Maximum macro expansion depth is 100 to prevent infinite expansion.

## Effects

Effects are lexically scoped values — like Clojure's dynamic vars but entirely lexical.
They are orthogonal to traits (see `TYPES.md` for the type-system perspective).

### Declaring effects

`defx` declares an effect variable with a type and an optional default:

```bridje
defx: log Fn(Str)                    // no default — must be provided by enclosing scope
defx: stdio Fn(Str) println           // has a default value
defx: net RaftNetwork                 // type can be a trait, a function, a record — anything
```

If a `defx` has no default, it must be provided by an enclosing `withFx` or the compiler reports an error.

### Using effects

Effect variables are used like any other value — no special syntax:

```bridje
def: doWork()
  log("starting")
  // ...
```

### Providing effects

`withFx` binds effect values into lexical scope:

```bridje
withFx: [net prodNetwork]
  startServer()
```

Effect impls can use other (typically lower-level) effects.
This replaces a higher-level effect with a lower-level one in the inferred effect set:

```bridje
withFx: [log fn: [msg] stdio("LOG: ${msg}")]
  doWork()
```

Here, `doWork` uses effect `{log}`.
The `withFx` satisfies `log`, but its impl uses `stdio`.
The effect set of the whole expression becomes `{stdio}` — the higher-level effect is replaced by the lower-level one.

### Effect inference

The compiler fully infers the effect set of every expression — users do not annotate effects:

```bridje
def: doWork()                   // inferred effects: {log}
  log("starting")

def: main()                     // inferred effects: {stdio}
  withFx: [log fn: [msg] stdio("LOG: ${msg}")]
    doWork()
```

## Common Macros

Defined in `brj.core`.

### ifLet

Conditional binding — binds the expression result, takes the then-branch if non-nil, else-branch if nil:

```bridje
ifLet: [leader :knownLeader(f)]
  redirect(leader)        // then — leader is bound and non-nil
  retryLater()            // else — expression was nil
```

### unlessLet

Inverse of ifLet — takes the first branch if nil:

```bridje
unlessLet: [config loadConfig()]
  useDefaults()           // nil case — config not found
  useConfig(config)       // non-nil case — config is bound
```

### orElse

Default for a nullable value — variadic, for chained fallbacks.
Default expressions are lazy — not evaluated if value is non-nil:

```bridje
orElse(name, "anonymous")
orElse(:timeout(config), :timeout(defaults), t/dur("PT30S"))
```

### when / unless

Conditional execution — evaluates body if predicate is true (when) or false (unless).
Returns nil otherwise.

```bridje
when: neq(peer, :id(state))
  sendAppendEntries(peer, state)

unless: empty?(items)
  processAll(items)
```

### and / or

Short-circuiting boolean macros — variadic.
`and` returns `false` at the first falsy value, `or` returns `true` at the first truthy value.

```bridje
and: gt(x, 0) lt(x, 100)       // true if 0 < x < 100
or: nil?(x) empty?(x)          // true if x is nil or empty
```

### cond

Multiple condition branches, evaluated top-to-bottom, first match wins.
A flat sequence of pairs — test expression, then result expression.
If the number of expressions is odd, the last one is the default (no test needed).

```bridje
// Simple — alternating test, result pairs:
cond:
  gt(n, 0) "positive"
  lt(n, 0) "negative"
  "zero"                          // default — no test needed
```

When the result expression is complex, it can use a colon block or indentation:

```bridje
cond:
  lt(:term(req), :currentTerm(state))
    rejectStale(state, req)

  not(logConsistent(state, req))
    rejectInconsistent(state, req)

  handleSuccess(state, req)       // default
```

This works because each result expression is just the next form after the test — colon block desugaring means an indented block is a single form.

### cond->

Conditional threading — threads a value through steps, but each step is guarded by a predicate.
If the predicate is false, the step is skipped and the value passes through unchanged.

```bridje
cond->: state
  dirty?(state) flush()
  stale?(state) refresh()
```

### doto

Evaluate side-effecting forms on a value, then return the value.
Useful for initialization or logging.

```bridje
doto: createConnection()
  :setAutoCommit(false)
  :setReadOnly(true)
```

### as->

Thread a value through forms with an explicit binding name, allowing it in any argument position.
Unlike `->` which always inserts as the first argument.

```bridje
as->: initial-value name
  transform(name, extra-arg)
  otherFn(something, name)
```

### comment

Ignores all body forms, returns nil.
Used to keep code around for development without executing it:

```bridje
comment:
  experimental-feature()
  debug-output()
```

### doseq

Side-effecting iteration over a collection.
Binding form is like `let` — a vector of name-expression pairs:

```bridje
doseq: [peer :cluster(state)]
  when: neq(peer, :id(state))
    sendAppendEntries(peer, state)
```

## Namespaces

```bridje
ns: raft.server
  require:
    brj:
      as(concurrent, c)
  import:
    java.time:
      as(Instant, I)
```

`.` separates namespace segments: `raft.server`, `brj.concurrent`.
`/` separates namespace from local name: `c/spawn`, `I/now`.

`require` brings in Bridje namespaces.
`import` brings in Java classes — creating both a type and a namespace of methods.

Qualified symbols:

```bridje
c/spawn(fn: () 42)
I/now()
brj.core/map(:name, people)
```

<!-- TODO: typed host interop — import creates typed method namespace via reflection -->
<!-- TODO: instance method syntax — I/:toEpochMilli(instant) for receiver-first calls -->

## Metadata

Attach metadata to forms with `^`.
Metadata is a member key or a record attached to the following form.
`^:test` is shorthand for `^{:test true}`:

```bridje
^:test
def: testElection()
  let: [state createTestState()]
    assert(eq(:role(state), Follower({:knownLeader nil})))

^{:doc "Returns the majority threshold for a cluster"}
def: majority(state) ...
```

Privacy is by convention — prefix with `_` to indicate private.
There is no access control in the language (consenting adults):

```bridje
def: _helper(x) ...              // private by convention
def: publicApi(x) _helper(x)     // nothing prevents calling _helper
```

## Comments

```bridje
// Line comment
//// Section comment (convention for visual grouping, no special meaning)
#_ expr              // Discard — comments out the following form
```

## Error Handling

Bridje uses anomaly-based error handling.
Errors are thrown as anomaly records with a category tag.
`try`/`catch`/`finally` work like Clojure/Java but catch anomaly categories.

```bridje
throw: NotFound({:message "user not found"})

try:
  riskyOperation()
  catch: NotFound(e) handleNotFound(e)
  catch: Forbidden(e) handleForbidden(e)
  finally: cleanup()
```

## Concurrency

Bridje uses structured concurrency via `brj.concurrent`.

```bridje
ns: my.app
  require:
    brj:
      as(concurrent, c)

def: fetchBoth()
  let: [a c/spawn(fn: () fetchA())
        b c/spawn(fn: () fetchB())]
    [c/await(a), c/await(b)]
```

`c/spawn` creates a virtual thread within a structured scope.
`c/await` blocks until the result is ready.
`c/interrupt` cancels a spawned task.
Parent tasks wait for all children; errors propagate up.

## Idioms

### Everything is an expression

There are no statements.
`if`, `let`, `case`, `do` all return values.
The last expression in a block is its value.

```bridje
def: describe(n)
  let: [label if: gt(n, 0) "positive" "non-positive"]
    "${n} is ${label}"
```

### Immutability by default

Values are immutable.
Records, vectors, sets, and maps are persistent data structures.
`with` returns a new record; it does not mutate the original.

Mutation is available when performance requires it (`set!`), but the default is immutable.
Bridje takes the "consenting adults" principle — mutability is a tool, not forbidden.

### Single-pass, bottom-up file structure

Namespaces are single-pass like Clojure — definitions can only reference earlier definitions.
Files naturally read top-down: types and helpers at the top, entry points at the bottom.

```bridje
//// Members
decl:
  :name Str
  :age Int

//// Types
tag: ...
type: ...

//// Helpers
def: _helperA(...) ...
def: _helperB(...) ...

//// Public API
def: handleRequest(...) ...
def: startServer(...) ...
```

### Naming conventions

- `camelCase` for functions and values: `handleVoteRequest`, `startElection`
- `PascalCase` for tags and types: `ServerState`, `VoteRequest`, `Result`
- `:camelCase` for instance members: `:name`, `:currentTerm`, `:toEpochMilli`
- Predicates and boolean values end in `?`: `nil?`, `pos?`, `some?`, `empty?`
- Effects optionally end in `!` by convention: `log!`, `send!`
- Private by convention with `_` prefix: `_helper`, `_internal`

### No infix operators

Bridje has no infix operators.
Arithmetic and comparison are regular functions.

```bridje
// Call syntax for standalone operations:
add(a, b)
eq(:term(req), :currentTerm(state))
max(commitIndex, 0)

// Threading for chains:
->: cluster count() div(2) inc()
```

For variadic operations, colon block syntax avoids deep nesting:

```bridje
and:
  gte(:term(req), :currentTerm(state))
  or:
    nil?(:votedFor(state))
    eq(:from(req), :votedFor(state))
  candidateLogUpToDate(state, req)
```

The block form is preferred when the expression has more than two arguments or when arguments are themselves complex expressions.

### Data-oriented design

Prefer plain records and tags over complex abstractions.
Reach for a record when you have keyed data, a tag when you need domain identity, an ADT when you have a fixed set of alternatives.
Functions transform data — they take records in and return records out.
Use traits for structural polymorphism — any value with the right members satisfies the trait.
