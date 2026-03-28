# Bridje Syntax

Bridje is a LISP.
Everything is an s-expression: `(operator arg1 arg2 ...)`.

## Primitives

```bridje
42                    // Int (32-bit integer)
3.14                  // Double (default float type)
42N                   // BigInt
3.14M                 // BigDec
"hello"               // Str
"hello ${name}"       // Str with interpolation
true                  // Bool
false
nil                   // null value, type Nothing?
:name                 // Keyword — used as record keys
```

## Collections

```bridje
[1, 2, 3]            // Vec — ordered, homogeneous
#{1, 2, 3}           // Set — unordered, unique, homogeneous
{:name "James", :age 30}  // Record — heterogeneous, keyed
```

Commas are whitespace everywhere — they're optional.

## Tagged literals

```bridje
#dur("PT0.15S")       // Duration
#inst("2026-03-28")   // Instant
```

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

### Universal Function Call Syntax (UFCS)

`a.foo(b, c)` desugars to `(foo a b c)`.
`a.field` desugars to `(:field a)`.

This turns inside-out LISP nesting into left-to-right chaining.
Without UFCS, accessing a nested field and calling a function reads inside-out:

```bridje
// Without UFCS (raw s-expression style):
inc(div(count(cluster), 2))

// With UFCS:
cluster.count.div(2).inc()
```

Both desugar to the same s-expression: `(inc (div (count cluster) 2))`.

UFCS is purely syntactic — there is no special method dispatch, no receiver, no `this`.
`a.foo(b)` and `foo(a, b)` are identical after desugaring.
Trait methods, key accessors, and plain functions all use the same call mechanism.

**When to use UFCS vs call syntax:**

UFCS is preferred when the function has a natural receiver — accessing a field, calling a method on a value, chaining transformations:

```bridje
state.currentTerm
state.with(:role newRole)
cluster.count.div(2).inc()
peers.associateWith(#: 0)
```

Normal call syntax is preferred when there's no natural receiver — standalone functions, constructors, predicates:

```bridje
add(a, b)
eq(req.term, state.currentTerm)
max(1, nextIndex.dec())
ServerState({:id serverId, :cluster cluster})
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
Keys are written without the `:` prefix when destructuring (like Clojure's `:keys`):

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
#: it.name
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
Patterns can be tag names, `nil`, or a binding variable (lowercase).
The last expression is the default — no `_` or `:else` keyword needed (like Clojure's `case`).

```bridje
case: expr
  Ok(v): handleOk(v)
  defaultValue              // last expression is the default
```

Exhaustive matching — when all variants are handled, no default is needed:

```bridje
case: state.role
  Follower(f): handleFollower(f)
  Candidate(c): handleCandidate(c)
  Leader(l): handleLeader(l)
```

Without a default, the compiler verifies all variants of the sum type are handled.

Destructuring inside tags:

```bridje
case: result
  Ok({value, metadata}): process(value, metadata)
  Err({message, code}): log("Error ${code}: ${message}")
```

### set!

Mutate a record field in place.
Returns the old value (or `nil` if the key was not previously set).
Bridje is immutable by default, but mutation is available when performance requires it (consenting adults).

```bridje
set!(record, :key, value)         // returns old value
record.set!(:key, value)          // UFCS form
```

### quote and unquote

Code as data — but unlike Clojure, Bridje's quote returns **typed Form ADT objects**, not raw data structures.
This is because Bridje is fully type-checked: the quoted representation must be statically typed.

`'form` returns a Form object.
`~form` inside a quote evaluates that sub-expression and splices the result in.

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

Form constructors are available as functions: `Symbol("foo")`, `Int(42)`, `List([...])`, etc.
These are used in macro bodies to build forms programmatically.

Unquote splices evaluated expressions into quoted forms:

```bridje
let: [x 'foo]
  '(bar ~x)           // ListForm([SymbolForm("bar"), SymbolForm("foo")])
```

Desugaring happens before quoting — `'foo(a, b)` captures the desugared s-expression `(foo a b)`, not the sugared form.
This means macros always work with s-expressions, regardless of how the user wrote the code.

Auto-gensyms with `#` suffix prevent variable capture in macros:

```bridje
'(let [tmp# ~x] (add tmp# tmp#))
// Both occurrences of tmp# resolve to the same unique symbol
```

## Top-Level Forms

### defkeys

Define record keys with globally fixed types.
A key has one meaning everywhere (the clojure.spec approach).

```bridje
defkeys: {:name Str, :age Int}

defkeys:
  {:host Str
   :port Int
   :timeout Duration}
```

### Records

Construction:

```bridje
{:name "James", :age 30}
```

Field access via UFCS — desugars to a keyword call:

```bridje
state.currentTerm             // (:currentTerm state)
req.from                      // (:from req)
```

Optional access — any key can be accessed in a nullable way with `?` via UFCS:

```bridje
state.?timeout                // desugars to (:?timeout state) — returns the value or nil
state.timeout                 // desugars to (:timeout state) — assumes the key is present
```

Optionality is at the call site, not the definition.
Any key can be accessed optionally — there's no distinction between "required" and "optional" keys in `defkeys`.
(See Rich Hickey's "Maybe Not" talk for the rationale.)

Update with `with` — returns a new record, does not mutate:

```bridje
state.with(:currentTerm newTerm, :votedFor nil)
```

Destructuring in function parameters and `let` bindings.
**Important**: keys use `:` prefix in construction but **not** in destructuring (like Clojure's `:keys`):

```bridje
// Construction — colon prefix:
{:name "James", :age 30}

// Destructuring — no colon prefix (these become local bindings):
def: displayName({fn, ln})
  "${fn} ${ln}"

def: greet({name, age})
  if: gt(age, 18)
    "Hello, ${name}"
    "Hey ${name}!"

let: [{name, email} getUser()]
  sendWelcome(name, email)
```

### tag

Nominal wrapper around a record.
A tag is distinct from any other tag, even with identical keys.

```bridje
tag: User({:name, :age, :email})
tag: LogEntry({:term, :index, :command})
```

Tags with type parameters:

```bridje
tag: Ok(a)
tag: Err(e)
```

Tagged record construction — two forms, both equivalent:

```bridje
// Call syntax:
VoteResponse({:from state.id, :to req.from, :term state.currentTerm, :granted true})

// Colon block syntax (desugars to the same thing):
ServerState:
  {:id serverId
   :cluster cluster
   :currentTerm 0}

// ServerState: {...} desugars to (ServerState {...}) which is ServerState({...})
```

Destructuring tagged records in function parameters:

```bridje
def: majority(ServerState({cluster}))
  cluster.count.div(2).inc()

def: formatEntry(LogEntry({term, index, command}))
  "[${term}:${index}] ${command}"
```

### type ... Sum

Closed sum type — a fixed set of variants.

```bridje
type: ServerRole
  Sum:
    Follower({:knownLeader})
    Candidate({:votesReceived})
    Leader({:nextIndex, :matchIdx})

type: Result(a, e)
  Sum: Ok(a) | Err(e)
```

### decl

Type declaration for a value or function.

```bridje
decl: x Int
decl: foo(Int, Str) Bool
decl: callback Fn([Int, Str] Bool)
decl: identity(a) a
decl: map([a], Fn([a] b)) [b]
```

### trait

A named set of method declarations.
The receiver (the type implementing the trait) is implicit — it does not appear in the `decl`.
Declared parameters are the *additional* parameters beyond the receiver.

```bridje
trait: Show
  decl: show() Str               // receiver only, no additional params

trait: Ord(t)
  decl: compareTo(t) Int         // receiver compared to t

trait: RaftNetwork
  decl: sendVoteRequest(ServerId, VoteRequest)
  decl: sendVoteResponse(ServerId, VoteResponse)
  decl: sendAppendRequest(ServerId, AppendRequest)
  decl: sendAppendResponse(ServerId, AppendResponse)
```

### impl

Implement a trait for a type.
The `impl` names the type; the `def` uses UFCS.

```bridje
impl: Show(Int)
  def: it.show() intToStr(it)

impl: Show(User)
  def: User({fn, ln}).show() "${fn} ${ln}"

impl: Int.Ord(Int)
  def: it.compareTo(other) intCompare(it, other)
```

Destructuring works in the receiver position.
For parameterised traits like `Ord(t)`, the impl specifies both the receiver type and the type parameter: `Int.Ord(Int)`.

### defmacro

Compile-time macro.
Receives unevaluated Form objects as arguments, must return a Form.

Because Bridje's quote returns typed Form ADT objects (not raw data), macros manipulate Form values directly:

```bridje
// Simple macro using quote/unquote:
defmacro: unless(cond, body)
  '(if ~cond nil ~body)

// Macro that manipulates Form objects directly:
defmacro: ifLet(bindings, then, else)
  let: [bvec bindings.nth(0)
        bname bvec.nth(0)
        bexpr bvec.nth(1)]
    '(let [v# ~bexpr]
       (case v#
         nil ~else
         ~bname ~then))
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
ifLet: [leader f.knownLeader]
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

Default for a nullable value.
Default expression is lazy — not evaluated if value is non-nil:

```bridje
name.orElse("anonymous")
config.timeout.orElse(#dur("PT30S"))
```

### when

Conditional execution — evaluates body if predicate is true, returns nil if false.
No else branch.

```bridje
when: neq(peer, state.id)
  peer.sendAppendEntries(state)
```

### cond

Multiple condition branches, evaluated top-to-bottom, first match wins.
Like Clojure's `cond`: a flat sequence of pairs — test expression, then result expression.
If the number of expressions is odd, the last one is the default (no test needed).

```bridje
// Simple — alternating test, result pairs on the same line:
cond:
  gt(n, 0) "positive"
  lt(n, 0) "negative"
  "zero"                          // default — no test needed
```

When the result expression is complex, it can use a colon block or indentation:

```bridje
cond:
  req.term.lt(state.currentTerm)
    rejectStale(state, req)

  not(logConsistent(state, req))
    rejectInconsistent(state, req)

  handleSuccess(state, req)       // default
```

This works because each result expression is just the next form after the test — colon block desugaring means an indented block is a single form.

### doseq

Side-effecting iteration over a collection.
Binding form is like `let` — a vector of name-expression pairs:

```bridje
doseq: [peer state.cluster]
  when: neq(peer, state.id)
    peer.sendAppendEntries(state)

doseq: [idx range(state.lastApplied.inc(), state.commitIndex.inc())]
  sm.apply(state.log.nth(idx).command)
```

## Namespaces

```bridje
ns: raft:server
  alias:
    proc: brj:proc
```

`:` separates namespace segments.
`alias` creates short names for other namespaces.
Qualified symbols: `proc:spawn`, `proc:Recv`.

## Metadata

Attach metadata to forms with `^`.
Metadata is a keyword or a record attached to the following form:

```bridje
^:test
def: testElection()
  let: [state createTestState()]
    assert(eq(state.role, Follower({:knownLeader nil})))

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

TBC.

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
//// Types
defkeys: ...
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
- Predicates and boolean values end in `?`: `nil?`, `pos?`, `some?`, `empty?`, `canGrant?`, `upToDate?`
- Effects optionally end in `!` by convention: `log!`, `send!`
- Private by convention with `_` prefix: `_helper`, `_internal`

### No infix operators

Bridje has no infix operators.
Arithmetic and comparison are regular functions.

For binary operations, either call syntax or UFCS works — use whichever reads better:

```bridje
// UFCS for chains — reads left-to-right:
cluster.count.div(2).inc()
state.currentTerm.gt(req.term)
n.add(1).mul(2)

// Call syntax for standalone operations:
add(a, b)
eq(req.term, state.currentTerm)
max(commitIndex, 0)
```

For variadic operations, colon block syntax avoids deep nesting:

```bridje
// Binary — either style:
and(condA, condB)
condA.and(condB)

// Variadic — block style reads better:
and:
  req.term.gte(state.currentTerm)
  or:
    nil?(state.votedFor)
    eq(req.from, state.votedFor)
  candidateLogUpToDate(state, req)

// Compare with the call-syntax equivalent:
and(req.term.gte(state.currentTerm), or(nil?(state.votedFor), eq(req.from, state.votedFor)), candidateLogUpToDate(state, req))
```

The block form is preferred when the expression has more than two arguments or when arguments are themselves complex expressions.

### Data-oriented design

Prefer plain records and tags over complex abstractions.
Reach for a record when you have keyed data, a tag when you need domain identity, a sum type when you have a fixed set of alternatives.
Functions transform data — they take records in and return records out.
Keep data and behavior separate; use traits when you need polymorphic dispatch.
