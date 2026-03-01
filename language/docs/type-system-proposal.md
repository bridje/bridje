# Bridje Type System: Records and Tags

## Fundamental Concepts

### Types of types

Field value types and tag payloads are globally fixed at definition site.
`defkeys: {:name Str, :age Int}` means `:name` always holds a `Str` and `:age` always holds an `Int`.
`deftag: Just(value)` means `Just` always carries one payload.
These are global facts, not inferred per-usage.

This follows the clojure.spec school of thought: a fully-qualified key has one meaning everywhere.
In practice, when should a `:user-id` be a `String` in one context and a `UUID` in another?
If you need that distinction, use different keys.

Record types and tag types therefore don't track *what types* their contents have — they track *which* keys/variants are involved.

### Records: which keys, with what presence

A record type is a set of keys, each with a presence flag:

```
Rec({name: req, age: opt})
```

"Definitely has `:name`, might have `:age`."

Presence forms a lattice: `req` (definitely present) ≤ `opt` (maybe present).

### Tags: which variants are possible

A tag type is a set of variant identities:

```
Tag({Just, Nothing})
```

"Is either `Just` or `Nothing`."

### The duality

Records and tags are duals in the subtyping lattice:

- **Records grow more specific by adding fields.**
  `Rec({name: req, age: req})` <: `Rec({name: req})` — more fields = more specific = subtype.
  A record with `:name` and `:age` can be used wherever only `:name` is needed.

- **Tags grow more specific by removing variants.**
  `Tag({A})` <: `Tag({A, B})` — fewer variants = more specific = subtype.
  A value known to be `A` can be used wherever `A`-or-`B` is expected.
  This is intentionally the dual of record subtyping.

### Operations mirror the duality

- **`set!` adds a field to a record type** — makes it more specific (goes down in the lattice).
- **`case` branch removes a variant from a tag type** — makes it more specific (goes down in the lattice).

Both are special forms with statically known keys/patterns, so the type system can compute exact results.

---

## Join and Meet

### Record join (if-branches, outputs — least upper bound)

```
Rec(ρ₁) ⊔ Rec(ρ₂):

  key in both   →  key: (ω₁ ⊔ ω₂)
  key in one    →  key: opt
```

All keys are kept.
Shared keys: join presence (`req ⊔ req = req`, `req ⊔ opt = opt`).
Unshared keys: become `opt`.

### Record meet (inputs, requirements — greatest lower bound)

```
Rec(ρ₁) ⊓ Rec(ρ₂):

  key in both   →  key: (ω₁ ⊓ ω₂)
  key in one    →  key: (keep its presence)
```

All keys are kept.
Shared keys: meet presence (`req ⊓ opt = req`).

### Tag join

```
Tag(S₁) ⊔ Tag(S₂) = Tag(S₁ ∪ S₂)
```

Union of variant sets. Goes up — less specific, more possibilities.

### Tag meet

```
Tag(S₁) ⊓ Tag(S₂) = Tag(S₁ ∩ S₂)
```

Intersection of variant sets. Goes down — more specific, fewer possibilities.
Empty intersection is a type error (no valid value can satisfy both).

---

## Subtyping and Constraints

### Record subtyping

```
Rec(ρ₁) <: Rec(ρ₂)   iff   for every (k: ω₂) ∈ ρ₂:
                               ∃ (k: ω₁) ∈ ρ₁  with  ω₁ ≤ ω₂
```

The subtype must have at least all the keys of the supertype, with at least as strong presence.
Extra keys in the subtype are fine (width subtyping).

### Tag subtyping

```
Tag(S₁) <: Tag(S₂)   iff   S₁ ⊆ S₂
```

The subtype's variant set must be a subset.
Fewer possibilities = more specific = subtype.

---

## Worked Examples

### Example 1: If-branches with different record fields

```
if cond
  then {:name "Alice", :age 30}
  else {:name "Bob", :email "bob@x.com"}
```

Branch types:
- `Rec({name: req, age: req})`
- `Rec({name: req, email: req})`

Join: `Rec({name: req, age: opt, email: opt})`

`:name` is guaranteed present.
`:age` and `:email` might be — use `?age` / `?email` to access.

### Example 2: Function that reads a field

```
fn(r) => :name(r)
```

`:name(r)` generates constraint `r <: Rec({name: req})`.
Inferred param type: `Rec({name: req})` — any record with at least `:name`.
Calling with `{:name "x", :age 1}` is fine — extra fields ignored by width subtyping.

### Example 3: Function with required and optional access

```
fn(r) =>
  :name(r)
  ?age(r)
```

Constraints on `r`: `Rec({name: req})` and `Rec({age: opt})`.
Meet: `Rec({name: req, age: opt})`.

Inferred param type says: "I need `:name`, can use `:age` if it's there."

### Example 4: set! adding a field

```
let r = {:name "Alice"}
set! r :age 30
```

`r` has type `Rec({name: req})`.
After `set!`: `Rec({name: req, age: req})`.

### Example 5: If-branches with different tags

```
if cond then Just(1) else Nothing
```

Branch types: `Tag({Just})`, `Tag({Nothing})`.
Join: `Tag({Just, Nothing})`.

The result is a value that could be `Just` or `Nothing`.
The only way to use it is `case`.

### Example 6: Case — exhaustive

```
-- x : Tag({Just, Nothing})
case x
  (Just(v)  v)
  (Nothing  0)
```

Branch types: both return `Int!` (assuming `:value` in `Just` is `Int`).
Join: `Int!`.

Each branch handles one variant.
No default needed because all variants are covered.

### Example 7: Case — with default (open tag acceptance)

```
fn(x) =>
  case x
    (A(v)  handleA(v))
    _      defaultHandler(x)
```

The default branch means this function accepts *any* tag type.
No constraint on which variants `x` contains.

In the default branch, `x` is narrowed: its type is the input minus `{A}`.

If `x` came in as `Tag({A, B, C})`, the default branch sees `x : Tag({B, C})`.

### Example 8: Case — partial handling, returning the rest

```
fn(x) =>
  case x
    (A(v)  Done(processA(v)))
    _      Remaining(x)
```

Input: `x` is any tag type (open, because of default).
- `A` branch: returns `Tag({Done})`
- Default branch: returns `Tag({Remaining})`, where `Remaining` carries the narrowed tag

Result: `Tag({Done, Remaining})`

The narrowed tag inside `Remaining` has type `Tag(input - {A})`.
This is the dual of `set!`: `set!` adds info to a record, case default subtracts info from a tag.

### Example 9: Case on tag, returning records

```
case input
  (Success(data)  {:ok true, :data data})
  (Failure(msg)   {:ok false, :error msg})
```

Branch types:
- `Rec({ok: req, data: req})`
- `Rec({ok: req, error: req})`

Join: `Rec({ok: req, data: opt, error: opt})`

The "smeared" record.
The type system doesn't track the correlation between `:ok`'s value and which other fields are present.
If that correlation matters, return tags instead of records.

### Example 10: Tag meet from multiple call sites

```
def: handleMaybe(x) ...
  -- called as handleMaybe(Just(1))
  -- called as handleMaybe(Nothing)
  -- called as handleMaybe(someTaggedValue)  where someTaggedValue : Tag({Just, Nothing, Other})
```

Constraints on `x` from call sites: `Tag({Just})`, `Tag({Nothing})`, `Tag({Just, Nothing, Other})`.
These flow in via `<:`, so `x`'s type accumulates as their join.
Join: `Tag({Just} ∪ {Nothing} ∪ {Just, Nothing, Other})` = `Tag({Just, Nothing, Other})`.

### Example 11: Nested — record containing a tag field

```
defkeys: {:value SomeTagType, :label Str}

{:value Just(42), :label "answer"}
```

Type: `Rec({value: req, label: req})`.
The fact that `:value` holds a `Tag({Just})` is known from the `defkeys` declaration, not tracked in the record type.

### Example 12: The full pipeline

```
deftag: Success(result)
deftag: Failure(error)
deftag: Pending

fn(x) =>
  case x
    (Success(r)  {:done true, :result r})
    (Failure(e)  {:done true, :error e})
    _            {:done false, :remaining x}
```

Input `x`: unconstrained tag type (default branch = accepts anything).

Branch types:
- `Rec({done: req, result: req})`
- `Rec({done: req, error: req})`
- `Rec({done: req, remaining: req})`

Join: `Rec({done: req, result: opt, error: opt, remaining: opt})`

In the default branch, `x` has type `Tag(input - {Success, Failure})`.
If the caller passed `Tag({Success, Failure, Pending})`, the remaining value is `Tag({Pending})`.

---

## Design Boundaries

### No union types on scalars

`Int ⊔ String` is a type error.
Tags are the union mechanism.
Records get a non-trivial join (optional fields) because their structure is inspectable at runtime.

### Optional presence is informational

The `req`/`opt` flag flows through inference and is surfaced for documentation.
It primarily matters in declared signatures (future bidi): "this function needs `:name` but `:age` is optional."

### Key and tag payload types are global

Defined by `defkeys` and `deftag`, not inferred per-usage.
Record types and tag types track *which* keys/variants, not their value types.

### Open vs closed is implicit

A record type with just `{name: req}` is implicitly open — width subtyping allows extra fields.
A tag type `Tag({A, B})` is implicitly "at most these" — subtyping means callers can pass fewer.
Case with a default branch imposes no constraint on the variant set — fully open.
Case without a default requires handling all variants in the type — this gives exhaustiveness checking for free as a consequence of the subtyping rules, not as a separate analysis pass.

### set! and case are dual special forms

`set!` adds known fields to record types (makes records more specific).
`case` removes known variants from tag types (makes tags more specific).
Both are special forms with static keys/patterns, so the type system computes exact results.
Neither requires row polymorphism.
