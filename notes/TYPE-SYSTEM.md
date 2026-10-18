# Bridje's type system: a definition

This is the formal account of the checker in `language/src/main/kotlin/brj/types`, written for a reader who knows Hindley–Milner and could implement Algorithm W, but has not met Bridje.
`notes/TYPES.md` is the user's guide to the same system; this file says what the checker computes and why it holds together.

The design is Dolan's algebraic subtyping (MLsub), in Parreaux's Simple-sub style, with three departures that shape everything below:

- **There are no union or intersection types, and no top.**
  Where MLsub keeps `τ ⊔ σ` symbolically, Bridje computes a join per kind eagerly, and fails where two kinds meet (`Int ⊔ Str` is an error, not a union).
- **Records are keyed by globally declared keys**, each with one declared type (possibly polymorphic), and a record type is a *name* (a tag, an enum, or none) over *slots* for those keys.
- **Nil is a flag on every type**, not a type constructor.

Wherever a formal statement is given, the prose under it says what it means.
Code references are to files under `language/src/main/kotlin/brj/types/` unless a path says otherwise.
`D` and `Q` numbers are the decisions and open questions recorded on [#129](https://github.com/bridje/bridje/issues/129), where the reasons for each rule are argued.

## Contents

1. [Notation](#1-notation)
2. [Types](#2-types)
3. [The declaration context](#3-the-declaration-context)
4. [Bounds, schemes and what a variable means](#4-bounds-schemes-and-what-a-variable-means)
5. [Subtyping](#5-subtyping)
6. [Constraint solving](#6-constraint-solving)
7. [Joins and meets](#7-joins-and-meets)
8. [The typing judgement](#8-the-typing-judgement)
9. [Typing each expression](#9-typing-each-expression)
10. [Top-level forms: generalisation, declarations, effects](#10-top-level-forms-generalisation-declarations-effects)
11. [Simplification](#11-simplification)
12. [Soundness](#12-soundness)

## 1. Notation

| Symbol | Reads as | In the code |
|---|---|---|
| `α, β, γ` | a flexible type variable | `TypeVar(rigid = false)` |
| `α̂` | a rigid (skolem) type variable | `TypeVar(rigid = true)` |
| `ι` | a primitive: `Int`, `Double`, `BigInt`, `BigDec`, `Str`, `Bool`, `Bytes` | `Base.Prim` |
| `⊥` | `Nothing`, the bottom type | `Base.Nothing` |
| `τ?` | `τ` with nil added | `Type(base, nullable = true)` |
| `nil` | `⊥?`, the type of the nil value | `NothingType.nullable()` |
| `τ ≤ σ` | `τ` is a subtype of `σ` | `BoundEnv.constrain(τ, σ)` |
| `τ ⊔ σ` | the join (least upper bound) of `τ` and `σ` | `Solve.join` |
| `τ ⊓ σ` | the meet (greatest lower bound) of `τ` and `σ` | `Solve.meet` |
| `B` | a bound environment: each variable's lower and upper bounds | `BoundEnv` |
| `Δ` | a demand environment: what an expression needs of each free local | `Typing.monoEnv` |
| `Π` | the global context: declarations, global schemes, host classes | `TypeCtx` |
| `Π ⊢ e ⇒ ⟨Δ; τ⟩ ⊣ B` | the typing judgement, §8 | `Infer.typing` |
| `∀B. τ` | a type scheme: `τ` with the bounds of all its variables | `Scheme(type, bounds)` |
| `x̄` | a sequence `x₁, …, xₙ` | `List` |
| `ρ ⊨ B` | the ground substitution `ρ` satisfies `B`, §4 | — |

A *ground* type is one with no variables.
"Fresh" always means a variable not occurring anywhere else.

## 2. Types

### 2.1 Grammar

```
τ, σ ::= (b, n)                     a type: a base b, and n ∈ {0, 1}, whether nil is added
                                    written b when n = 0 and b? when n = 1

b    ::= ι                          primitive
       | ⊥                          Nothing
       | α | α̂                      a variable, flexible or rigid
       | Fn(τ₁, …, τₙ) → τ          function
       | [τ]                        vector
       | #{τ}                       set
       | Iterable(τ)                anything iterable, a Truffle interop capability
       | Iterator(τ)                an iterator, likewise
       | C⟨τ̄⟩                       a JVM class C at type arguments τ̄ (C⟨⟩ is the raw class)
       | N⟨τ̄⟩{S}ᵒ | N⟨τ̄⟩{S}ᶜ       a record: a name, its type arguments, extra slots, open or closed
       | {S & α}                    the record-on-a-variable: an α that also carries the slots S

N    ::= T                          a tag name
       | E                          an enum name
       | ·                          no name: a plain record (then τ̄ is empty)

S    ::= .k₁⟨σ̄₁⟩, …, .?kₘ⟨σ̄ₘ⟩      slots: a finite map from keys to slots
                                    .k⟨σ̄⟩   the key is certainly present, its value at instance σ̄
                                    .?k⟨σ̄⟩  the key is perhaps present, at instance σ̄
                                    ⟨σ̄⟩ is omitted when the key's type has no variables
```

**A type is a base and a nil bit.**
`Str?` is `(Str, 1)`, and `nil` is `(⊥, 1)`: nothing but nil.
On a concrete base the bit is exact: `Str` excludes nil.
On a variable it is additive only: `(α, 0)` is "whatever `α` is", and `α` may itself be instantiated at a nullable type, so `(α, 0)` does not exclude nil; `(α, 1)` is `α` *with nil added*.
So there is no `τ??`: adding nil twice is adding it once (`Type.nullable()`).

**`⊥` is `Nothing`**, the type of `throw(…)` and of `recur(…)`: no value has it, so it is below everything.
There is no top: no type every value has.

**A record is a name over slots.**
`Rec(name, args, keys, closed)` in `Type.kt`:

- The *name* is a tag `T` (e.g. `Ok`), an enum `E` (e.g. `Result`), or none.
  A tag is a nominal name over (at most) one record, and an enum is a closed sum of tags.
  A plain record literal `{.a 1}` has no name: in Bridje a record is a tag with no name.
- The *arguments* `τ̄` are the name's type parameters, `Result⟨Int, Str⟩`.
- The *slots* `S` are the keys the value carries *beyond those its name declares* (for a record with no name, all of them).
  `User{.email}` is a `User` known to also carry `.email`.
- *Closed* (`ᶜ`): the value carries no key the type does not mention, as a literal does.
  *Open* (`ᵒ`): it may carry any other key, at any instance, as a type a user writes does.
  I omit the mark when it does not matter.

A tag with no record (`None`) or an opaque builtin (`Symbol`) is a `Rec` with a name and no slots, which the context knows is not a record (§3.3).

**A slot says whether a key is present, and at what instance.**
A key is declared once with a type that may have variables, `decl: [a] .value a`, i.e. `.value : ∀a. a`.
Each record holds the key at an instance of its own: `{.value 1}` is `{.value⟨Int⟩}`, and reading `.value` off it gives `Int`.
This is how a single global key serves records holding different types without making every record type polymorphic in every key.

**`{S & α}` is a value of `α` that is also known to carry `S`.**
It is the type of `with(r, .k v)` when `r` has a variable type: the result is still "whatever `r` was", now with `.k`.
Read it as the intersection `α ∧ {S}` in *positive* position (D21).
MLsub's polar types admit intersections only in negative position, so MLsub cannot type `with`; MLstruct can, with a full Boolean algebra.
Bridje admits only this fragment, a variable met with a record, never two records met, which keeps decomposition deterministic: `{S & β} ≤ R` splits into what `S` supplies and what `β` must.
It never appears as a variable's concrete bound, and never in an upper bound (§6).

**Rigid variables** are the skolems of a declaration being checked (§10.2); they behave as opaque type constants.

### 2.2 Host classes

`C⟨τ̄⟩` names a JVM class at type arguments.
The classes the runtime represents a primitive by are that primitive, wherever they are named (`hostClassType`): `java.lang.Long` is `Int`, `Double` is `Double`, `String` is `Str`, `Boolean` is `Bool`, `BigInteger` is `BigInt`, `BigDecimal` is `BigDec`.
A generic class written with no arguments, `C⟨⟩`, is the raw class: wherever it is compared, it gets fresh arguments (`Host.applied()`).

## 3. The declaration context

`Π` (`TypeCtx`, built by `typeCtx` in `Runtime.kt`) holds what the namespaces declare.

### 3.1 Keys

```
K(k) = ∀ᾱ. τₖ          (decl: [a…] .k τ)          every variable of τₖ is among ᾱ
K(k)⟨σ̄⟩ = τₖ[ᾱ ↦ σ̄]     the key's type at instance σ̄, |σ̄| = |ᾱ|
mono(k)  ⇔  ᾱ is empty   the key has one type everywhere
```

A key is declared before anything uses it; an undeclared key is an error (`has no declared type`).
`mono(k)` matters throughout: a key with one type cannot disagree with itself, so many rules that must be careful about instances of a polymorphic key are free for a monomorphic one.

### 3.2 Tags and enums

```
T = ⟨E?, ᾱ, payload⟩
payload ::= none                      tag: None
          | record(k̄, args)           tag: [a] Box{.value(a)}: keys k̄, and for each polymorphic key
                                      its instance in terms of ᾱ, args(.value) = ⟨a⟩
          | opaque(n)                 a builtin meta, Symbol, Var, File: n untracked arguments

E = {T₁, …, Tₙ}                      enum: Result(a, e) tag: Ok{.value(a)} tag: Err{.error(e)}
```

The variants of an enum share its parameters.
Enums are flat: an enum's variants are tags, not enums. Nested enums, with the join of two tags as their least common ancestor, are designed (D25) and not built.
The reader's forms are an enum, `Form`, whose variants are the `brj.rdr` tags; the anomaly tags are the variants of the enum `Anomaly`.
An opaque payload's arguments are not typed at all (§12.3).

### 3.3 Derived notions

These are the functions on `TypeCtx` the rules below use.

**`isRecord(N)`**: a value of `N` is certainly a record.
True of no name; of a tag with a record payload; of an enum all of whose variants have one.
`Maybe` (with `None`) is not a record; `Result` is.

**`mayCarry(N)`**: a value of `N` may carry keys at all: no name, a tag with a record, an enum with some variant with a record.

**`decl(N, τ̄)`**: the keys the name itself guarantees, as *facets* `(required, instances)`:

- For a tag `T⟨τ̄⟩`: each of its keys, required, at the one instance `args(k)[ᾱ ↦ τ̄]`.
- For an enum `E⟨τ̄⟩`: a key some variant declares, provided it is monomorphic or *every* variant with a record declares it; it is required if every variant (record or not) declares it, and its instances are the set of the variants' instances.

  The reason: a variant with a record that does *not* declare a polymorphic `k` may still carry `k` at runtime (records are open), at an instance nothing records, so the enum cannot promise one.

**`slots(R)`** for `R = N⟨τ̄⟩{S}`: `decl(N, τ̄)` overlaid with `S`; a key in both is required if either says so and has both instance sets.
This is "everything this record type says about its keys".

**`N ≤ₙ N'`** (`nameBelow`): `N' = ·` and `isRecord(N)`; or `N = N'`; or `N` is a tag of the enum `N'`.
A tag is below its enum, and a record of any name is below no name.

**`N ⊔ₙ N'`** (`commonName`): equal names, or a tag and its enum, or two tags of one enum, give that enum; otherwise none.
**`N ⊓ₙ N'`** (`specificName`): the more specific of the two where one is below the other (no name being above everything); otherwise undefined.

**Variance** (`paramVariance`, `keyVariance`): each parameter of a key, a tag or an enum is `co`, `contra`, `inv` or `phantom`, from where it occurs:

- a key's parameter, by its polarity in `τₖ` (function parameters flip polarity; host class arguments occur both ways);
- a tag's parameter, by its polarity in the instances it gives its keys, composed with those keys' variances;
- an enum's parameter, the union over its variants.

Occurrences are computed to a least fixpoint from "nowhere" (`settle`), as enums and keys may be mutually recursive.
Occurring both ways is `inv`; nowhere is `phantom`.
So under `decl: [a] .h Fn([a] Bool)`, `tag: [a] H{.h(a)}` is contravariant in `a`, and `None`'s argument in `Maybe` is phantom.

## 4. Bounds, schemes and what a variable means

### 4.1 Bound environments

A bound environment `B` maps each variable to a lower and an upper bound, each kept in a normal form (`LowerBound`, `UpperBound` in `Type.kt`):

```
lower(α) = ⟨ J, V, M ⟩       J   one concrete type (not a variable), the join of every concrete lower bound,
                                 ⊥ when none; nil flowing in sets J's nil bit
                             V   variables β with β ≤ α
                             M   at most one {S & β} per variable β, with {S & β} ≤ α

upper(α) = ⟨ m, W, num ⟩     m   one concrete type or none, the meet of every concrete upper bound
                             W   variables β with α ≤ β, or α ≤ β? when the edge is marked nil-ok
                             num α is numeric: what flows in is an Int or a Double, and never nil
```

So where MLsub keeps a variable's bounds as sets and lets types contain unions, Bridje merges each side into one concrete type as it goes: lower bounds by join, upper bounds by meet (§7).
Variable–variable edges are kept as edges.

`num` is a predicate, not a type: "`Int` or `Double`" is a union, which this lattice cannot write.
It is what `add`, `sub`, `lt` and the rest demand of their operands, and renders as `Num(a)`.

### 4.2 Meaning

A *ground substitution* `ρ` maps every variable to a ground type, extended structurally, with `ρ((α, 1)) = ρ(α)?`.

```
ρ ⊨ B  ⇔  for every α in B:
            ρ(t) ≤ ρ(α)       for every t in lower(α)            (J, each β ∈ V, each {S & β} ∈ M)
            ρ(α) ≤ ρ(u)       for every u in upper(α)            (m, each β ∈ W, β? for nil-ok edges)
            num(α) ⇒ ρ(α) ∈ {Int, Double, ⊥}
```

A type `τ` *under* `B` stands for the set `{ ρ(τ) | ρ ⊨ B }`: one bounded-quantified type.

Where an inference leaves a variable bounded in a cycle (`α ≤ [α]`), no finite type satisfies it.
Read the ground types as possibly infinite regular trees, as MLsub does; values are finite, so such a type is inhabited only by values that never reach the cycle.
The simplifier keeps such a variable named (§11).

### 4.3 Schemes

```
σ ::= ∀B. τ       every variable of τ, and of the bounds reachable from it, is quantified
```

Bridje generalises only at the top level (§10.1), so there is no enclosing environment whose variables must stay free, and a scheme quantifies everything it mentions.
`∀B. τ` stands for the union over `ρ ⊨ B` of `ρ(τ)`: a value of the scheme has every such type.

**Instantiation** (`Scheme.instantiate`) renames every variable reachable from `τ` through `B` to a fresh one, rigid where it was rigid, and copies their bounds.

## 5. Subtyping

This section gives `≤` on types as a declarative relation; §6 gives the algorithm that decides it with variables present.
The relation is reflexive throughout.

### 5.1 Nil, bottom, primitives

```
(S-Bot)    ⊥ ≤ τ                                 Nothing is below everything
(S-Nil)    (b, n) ≤ (b', n')   if b ≤ b' and n ≤ n'      (b' not a variable)
(S-Prim)   ι ≤ ι'              iff ι = ι'
```

- **Nil fits only where nil is allowed.** `Str? ≤ Str` fails: `Str? is nullable, but must not be`. `nil = ⊥? ≤ τ?` for every `τ`.
- **No numeric widening.** `Int ≤ Double` fails, as does `Int ≤ BigInt`.
- When the upper side is a variable, a nil on the lower side is recorded as nil in the variable's lower bound (§6), since a variable may be instantiated at a nullable type.

### 5.2 Functions

```
(S-Fn)     Fn(σ₁…σₙ) → ρ  ≤  Fn(σ'₁…σ'ₙ) → ρ'    iff  σ'ᵢ ≤ σᵢ for all i, and ρ ≤ ρ'
(S-Elide)  Fn(σ₁…σₙ σₙ₊₁) → ρ  ≤  Fn(σ'₁…σ'ₙ) → ρ'   iff  the above, and {}ᵒ ≤ σₙ₊₁
```

Contravariant in parameters, covariant in the result, as usual.

`S-Elide` is the trailing-options convention (D45): the runtime passes an empty record for each missing trailing argument (`fitted`, `brj/runtime/CallConvention.kt`), so a function whose last parameter accepts the empty record may be called without it.
The runtime fills any number of missing arguments; the checker admits exactly one, which is a restriction, not a hole.
The premise uses the *open* `{}`, which is a stronger demand than the closed empty record the runtime actually passes, so it is safe.
Any other arity difference is `function arity mismatch`, and a surplus argument is always an error, statically and at runtime.

### 5.3 Collections and protocols

```
(S-Vec)     [τ] ≤ [τ']                iff τ ≤ τ'
(S-Set)     #{τ} ≤ #{τ'}              iff τ ≤ τ'
(S-Iter)    b ≤ Iterable(τ')          iff iterEl(b) = τ and τ ≤ τ'
(S-Itor)    b ≤ Iterator(τ')          iff itorEl(b) = τ and τ ≤ τ'

iterEl([τ]) = iterEl(#{τ}) = iterEl(Iterable(τ)) = τ
iterEl(C⟨τ̄⟩) = C⟨τ̄⟩'s argument to java.lang.Iterable, where C implements it
itorEl(Iterator(τ)) = τ;  itorEl(C⟨τ̄⟩) = its argument to java.util.Iterator
```

Vectors, sets and the protocol types are covariant: they are immutable to Bridje.
`Iterable` and `Iterator` are interop capabilities, not classes; the relationships live in the checker.

### 5.4 Host classes

```
(S-Host)   C⟨τ̄⟩ ≤ D⟨σ̄⟩    iff  C is D or a subclass of it, and C⟨τ̄⟩'s arguments to D, as the
                               class declarations give them (C⟨τ̄⟩ ↑ D), equal σ̄:  (C⟨τ̄⟩ ↑ D)ᵢ ≤ σᵢ ≤ (C⟨τ̄⟩ ↑ D)ᵢ
```

Host type arguments are invariant, as Java's are.
`C⟨τ̄⟩ ↑ D` is computed by walking the reflected generic supertypes (`HostTypeHierarchy`): `ArrayList⟨Int⟩ ↑ Iterable = ⟨Int⟩`, and `Path ↑ Iterable = ⟨Path⟩`.
A wildcard, or a raw supertype on the way, gives a fresh variable for that argument.
A raw class `C⟨⟩` is first given fresh arguments, which makes it compatible with any instantiation (§12.3).

### 5.5 Records

For `R = N⟨τ̄⟩{S}` and `R' = N'⟨τ̄'⟩{S'}` (`Solve.constrainRec`):

```
(S-Rec)  R ≤ R'  iff
  (name)      N ≤ₙ N'
  (args)      if N' ≠ ·:  τᵢ ⊑ᵥ τ'ᵢ  for each parameter i of N, at its variance v
  (present)   every .k in S' that is required is required in slots(R)
  (instance)  for each k in S':  if k ∈ slots(R), each instance σ̄ of k there satisfies σ̄ ⊑_{var(k)} S'(k)'s instance
              if k ∉ slots(R), then R is closed or mono(k)
  (closed)    if R' is closed:  R is closed or not mayCarry(N), and keys(slots(R)) ⊆ keys(slots(R'))

where  τ ⊑_co τ' is τ ≤ τ';  τ ⊑_contra τ' is τ' ≤ τ;  τ ⊑_inv τ' is both;  τ ⊑_phantom τ' always
```

Reading each clause:

- **name**: a tag is below its enum, anything that is certainly a record is below no name, and two different tags are unrelated.
- **args**: compared through the variance the parameters have from the keys they instantiate, not declared variance. `None⟨Int⟩ ≤ Maybe⟨Str⟩` holds, as `None` does not use its argument.
- **present**: width subtyping. More keys is more specific: `{.a, .b} ≤ {.a}`. A required key must be certainly present.
- **instance**: a key's value must be at an instance below the demanded one, through the key's variance.
  If `R` does not mention `k` at all, an `.?k` demand can still be met: a closed `R` certainly lacks `k`, so the read gives nil; an open `R` may carry `k` *at any instance*, which is only harmless where `k` has one type. So `.?value` on an open record that does not mention a polymorphic `.value` is `may carry .value at any type`.
- **closed**: a closed demand admits no key it does not mention.

The derived instances:

- `{.a, .b}ᶜ ≤ {.a, .?b}ᵒ ≤ {.a}ᵒ`; `{.a}ᵒ ≰ {.a}ᶜ`.
- `Ok⟨Int, e⟩ ≤ Result⟨Int, e⟩ ≤ {.?value⟨Int⟩, .?error⟨e⟩}`… only where `decl(Result, …)` permits (§3.3).
- `User{.email} ≤ User`, and not the other way.
- `Maybe⟨a⟩ ≰ {.?value⟨a⟩}`: `Maybe` is not a record (its `None` has none), so it is below no unnamed record type, even one whose keys are all optional. Whether it should be is Q12.

**Closed demands barely arise.**
Literals produce closed types only in positive positions, and every type form a user writes is open, so the only closed type that reaches an upper bound is a record-less standalone tag demanded by `case` (§9.9).
The *closed* clauses of `S-Rec`, `recMeet` and `S-OnVarL` are therefore close to dead code; §12.3 notes one place this matters.

### 5.6 Records on variables

```
(S-OnVarR)  τ ≤ {S & α}          iff  τ ≤ α  and  τ ≤ {S}ᵒ
(S-OnVarL)  {S & α} ≤ R'         iff  for each k in S, S(k)'s instance meets R'’s demand of k (through var(k)),
                                      and, for any variant of N' declaring k, that declaration's instance;
                                      and α ≤ R'' where R'' is R' less the keys S already supplies certainly
                                      (and, if R' is closed, with S's keys added as optional at fresh instances)
(S-OnVarA)  {S & α} ≤ α          iff  mono(k) for every k in S
(S-OnVarAny) {S & α} ≤ {S'}ᵒ     where every key S' requires, S gives certainly: nothing is left to demand of α
```

`{S & α}` is "α, with `S` replaced or added".
`S-OnVarL` splits a demand between the keys the `with` gave and the variable: what `with` supplied satisfies the demand for those keys, and `α` must satisfy the rest.
`S-OnVarA` is the absorption that makes `if: p with(r, .dirty true) r` type as `r`'s own type: adding a key of one type to an `α` leaves a value that is still an `α`. Width subtyping already allows an `α` to carry extra keys, and a monomorphic key cannot be at the wrong instance. For a polymorphic key, `with` may have changed its instance, so the result need not be an `α`.

### 5.7 Variables

Variables are related through their bounds (§4.2): `τ ≤ α` holds under `B` when `ρ(τ) ≤ ρ(α)` for every `ρ ⊨ B`.

A rigid `α̂` is an unknown fixed type: under any `B`, only `⊥`, `α̂` itself, `{S & α̂}` with every `k ∈ S` monomorphic, and a flexible variable whose lower bounds are all of those, are below it; and `α̂` is below only itself and flexible variables all of whose upper bounds it is below.
`α̂ ≤ β̂` for distinct rigid variables fails.

### 5.8 What is not a subtype

There is no rule relating different kinds: a record is not an `Iterable`, a vector is not a host class, and a function is not a record.
Since there is no top, there is no type every value has, which is why two kinds cannot join (§7).

## 6. Constraint solving

`B.constrain(τ, σ)` (`Solve` in `Solver.kt`) returns the least extension of `B` under which `τ ≤ σ` holds, or fails with a type error.
It follows Simple-sub: a constraint is decomposed structurally until it meets a variable; a bound added to a variable is checked against every bound already on the other side; and a cache of the constraints in flight makes cyclic bounds terminate.
Where it departs, it is in merging each side's concrete bounds into one.

The cases of `constrain(τ = (b, n), σ = (b', n'))`, in the order the code tries them:

1. **Done**: `τ = σ`, or `(τ, σ)` is already in the cache.
2. **Nil**: if `n = 1` and `n' = 0`: if `b'` is a variable `β`, record nil in `lower(β)` (`addNil`: fails if `β` is rigid or numeric, else propagates `nil` to `β`'s upper bounds); otherwise fail (`may be nil`). Then continue with `b` against `σ`.
3. **`b = ⊥`**: holds.
4. **`b' = {S & β}`**: `constrain(b, β)` and `constrain(b, {S}ᵒ)` (`S-OnVarR`).
5. **`b = α`, `b' = β`**: record the edge `α ≤ β` (marked nil-ok if `n' = 1`; an existing strict edge already implies the nil-ok one).
   If both are rigid, fail.
   If `β` is numeric and the edge is strict or `α` is rigid, mark `α` numeric (`markNumeric`, which checks `α`'s existing lower bounds and recurses into variables below `α`).
   Add `α` to `lower(β).V` if the edge is strict or `α` is rigid, absorbing a `{S & α}` already in `lower(β).M` where `S` is monomorphic.
   Propagate: each of `α`'s lower bounds `≤ β` (as `β?` if nil-ok); if `α` is rigid, `α ≤` each of `β`'s upper bounds.
6. **`b = α̂` rigid, `b'` concrete**: fail.
7. **`b'` = `β̂` rigid, `b` concrete**: holds only for `{S & α}` with `S` monomorphic, as `α ≤ β̂`; otherwise fail.
8. **`b = α`, `b'` concrete**: `upper(α).m := upper(α).m ⊓ σ`; if that changed anything, check every lower bound of `α` against `σ`.
9. **`b` concrete, `b' = β`**: add `b` to `lower(β)` (`addLower`):
   - fail if `β` is numeric and `b` is not `Int` or `Double`;
   - a `{S & α}`: absorbed if `α` is `β` or already in `lower(β).V` and `S` monomorphic; else `J` must be a record kind (or `⊥`), and it joins `M(α)` by `joinGiven`;
   - otherwise `J := J ⊔ b` (fails across kinds; fails if `M` is non-empty and `b` is not a record kind).

   If that changed anything, check the new `J` (or the new `{S & α}`) against every upper bound of `β`.
10. **`b = {S & α}`, `b'` concrete**: `S-OnVarL`.
11. Otherwise, decompose by the rules of §5: primitives, functions, collections, protocols, host classes, records. Anything else fails: `X is not a subtype of Y`.

**Two properties the rest relies on.**

- *Closure*: after `constrain` returns, for every `α`, every lower bound of `α` has been checked against every upper bound of `α`, recursively. The cache holds every pair seen for the life of one `Solve`, and skips it thereafter; as bounds only grow, a pair once checked stays checked.
- *Merging is sound*: steps 8 and 9 store `m ⊓ σ` and `J ⊔ b` in place of the separate bounds. This is sound because `m ⊓ σ ≤ m, σ` and `b, J ≤ J ⊔ b` (§7), so a lower bound checked against the merged upper bound is below each original one, and an upper bound checked against the merged lower bound is above each original one.
  It may be *incomplete* where the computed `⊓` is not the greatest lower bound (e.g. functions of different arities, §7.2).

Computing a join or meet may itself add constraints: a component joined "via" a fresh variable (`joinVia`) is that variable with both components below it.
Where one side is a variable already known to bound the other, it is that variable instead, so that a bound flowing round a `loop`/`recur` cycle settles rather than minting a new variable each pass.

The solver also reports *provenance*: a failure deep inside a structural decomposition names the top-level constraint it came from (`while checking L ≤ U`).

## 7. Joins and meets

Joins combine lower bounds (what may flow in), meets combine upper bounds (what is demanded).
Each is computed per kind; nil composes as `n ⊔ n' = n ∨ n'` and `n ⊓ n' = n ∧ n'`.
Where an element type is joined or met, it is joined or met *via a variable*: `τ ⊔ᵥ σ` is a fresh `γ` with `τ ≤ γ` and `σ ≤ γ` (or one of `τ`, `σ` where it already bounds the other), and `⊓ᵥ` dually.

### 7.1 Joins (`joinBase`)

A second lower bound of a different kind is an error where it is added (D20): that, and not the absence of a top, is what keeps a union out of every type.
Vectors, sets, `Iterable`s and iterable host classes count as one kind until traits arrive (D33), and so do records, tags and enums.

| `a ⊔ b` | result |
|---|---|
| `a ⊔ a` | `a` |
| `⊥ ⊔ b` | `b` |
| `ι ⊔ ι'`, `ι ≠ ι'` | **error**: `Cannot join Int with Str` |
| `Fn(σ̄) → ρ ⊔ Fn(σ̄') → ρ'` | `Fn(σᵢ ⊓ᵥ σ'ᵢ) → (ρ ⊔ᵥ ρ')` when arities match, else **error** |
| `[τ] ⊔ [τ']` | `[τ ⊔ᵥ τ']` |
| `#{τ} ⊔ #{τ'}` | `#{τ ⊔ᵥ τ'}` |
| `C⟨τ̄⟩ ⊔ D⟨σ̄⟩` | `D⟨…⟩` if `C` is a subclass of `D` (arguments equated), symmetrically `C⟨…⟩`; else as the next row |
| two iterable bases (`[τ]`, `#{τ}`, `Iterable(τ)`, an iterable class) | `Iterable(iterEl(a) ⊔ᵥ iterEl(b))` |
| two iterator bases | `Iterator(itorEl(a) ⊔ᵥ itorEl(b))` |
| `R ⊔ R'` | `recJoin`, below |
| anything else | **error**: `Cannot join a with b` |

Two host classes neither of which is the other's superclass have no least upper bound in Java (only a set of common interfaces), so they join only through the `Iterable`/`Iterator` protocols or not at all.

**Records** (`recJoin`), `R = N⟨τ̄⟩{S}` and `R' = N'⟨τ̄'⟩{S'}`:

```
name   N ⊔ₙ N'; if none, both must be records (isRecord), and the join has no name
args   τᵢ ⊔ τ'ᵢ through the variance of parameter i (co: ⊔ᵥ, contra: ⊓ᵥ, inv: both equal to one γ);
       an argument phantom on one side takes the other's
keys   for each k in slots(R) ∪ slots(R'), less those the joined name declares:
         in both          →  .k (required iff required in both), at the join of all instances
         in one only      →  .?k, if the other side is closed, or mono(k); otherwise dropped
closed both closed
```

So:

- `{.a 1, .b 2} ⊔ {.a 1, .c 3}` = `{.a, .?b, .?c}ᶜ`: each literal is closed, so each certainly lacks the other's extra key, and the join may or may not have it.
- `Ok{.value 1} ⊔ Err{.error "s"}` = `Result⟨Int, Str⟩{.?value⟨Int⟩, .?error⟨Str⟩}`.
- `Ok{…} ⊔ Just{…}` (different enums) = the plain record of the keys they share (D34).
- `None ⊔ Some` for a standalone `None`: `None` is not a record and shares no name with `Some`: **error**.

**Records on variables** join only with record kinds, and `{S₁ & α} ⊔ {S₂ & α}` (`joinGiven`) keeps the keys both give (at the join of their instances); a key only one gives is `α`'s own value on the other side, so it is dropped where monomorphic and an **error** where polymorphic (`may differ from α's own`).

### 7.2 Meets (`meetBase`)

Different kinds have no common inhabitant but `⊥`, so their meet is `⊥` rather than an error: a variable demanded to be both an `Int` and a `Str` accepts only `⊥` (a `throw`), and the error arises when a value flows in.

| `a ⊓ b` | result |
|---|---|
| `a ⊓ a` | `a` |
| `⊥ ⊓ b` | `⊥` |
| `Fn(σ̄) → ρ ⊓ Fn(σ̄') → ρ'` | `Fn(σᵢ ⊔ᵥ σ'ᵢ) → (ρ ⊓ᵥ ρ')` when arities match, else `⊥` |
| `[τ] ⊓ [τ']`, `#{…}`, `Iterable(…)`, `Iterator(…)` | elementwise `⊓ᵥ` |
| `C⟨τ̄⟩ ⊓ C⟨σ̄⟩` | `C` with arguments equated |
| `C⟨τ̄⟩ ⊓ D⟨σ̄⟩`, `C` a subclass of `D` | `C⟨τ̄⟩`, with `C⟨τ̄⟩ ↑ D` equated to `σ̄`; symmetrically; unrelated: `⊥` |
| `[τ] ⊓ Iterable(σ)` | `[τ ⊓ᵥ σ]`; likewise `#{…}` |
| `C⟨τ̄⟩ ⊓ Iterable(σ)` | `C⟨τ̄⟩`, adding `iterEl(C⟨τ̄⟩) ≤ σ`; `⊥` if `C` is not iterable; likewise `Iterator` |
| `R ⊓ R'` | `recMeet`, below |
| anything else | `⊥` |

The arity row is the one place the meet is not greatest: `Fn(A, B) → C` with `{} ≤ B` is below both `Fn(A) → C` and `Fn(A, B) → C` by `S-Elide`, but their computed meet is `⊥`. This loses completeness, not soundness.

**Records** (`recMeet`):

```
name   N ⊓ₙ N'; ⊥ if undefined and either side is named.
       If one side is unnamed and the other's name is not a record (an enum with a record-less variant),
       the name is that enum's one variant with a record, if there is exactly one; else ⊥.
       (Maybe ⊓ {.value} = Just: the only Maybe that is a record.)
args   both named: τᵢ ⊓ τ'ᵢ through variance, an argument phantom on one side taking the other's;
       else the named side's
keys   for each k either side demands:
         a closed side that does not mention k (and whose name does not declare it) lacks it:
           if either side requires k → the meet is ⊥; else k is dropped
         if the name declares k: each declared instance must meet the demanded ones (constraints added);
           kept as an extra slot only if demanded required but declared perhaps
         otherwise: .k, required if either side requires it, at the meet of the demanded instances
       a closed side not mentioning a key the name declares → ⊥
closed either closed
```

## 8. The typing judgement

```
Π ⊢ e ⇒ ⟨Δ; τ⟩ ⊣ B ; R
```

reads: *in the global context `Π`, the expression `e` produces a value of type `τ`, provided each free local `x` it uses holds a value of type `Δ(x)`, under the bounds `B`. `R` records what `recur` inside `e` sends each loop binding.*
I omit `R` where it is empty or just passed up.

**This is the dual of HM's `Γ ⊢ e : τ`.**
In Algorithm W the environment flows *down*: `Γ` tells a sub-expression what each local is, and unification threads one substitution through the whole definition.
Here the demands flow *up*: an expression reports what it *needs* of each local (`Δ`, a negative position), alongside what it *produces* (`τ`, a positive one).
Each occurrence of a local gets its own fresh variable; where two sub-expressions use the same local, their demands are met in a fresh variable at the parent.
So a sub-expression's typing depends on its children's typings and nothing else, and no substitution is threaded through anything.
This is Dolan's "typing schemes", after Trifonov and Smith.

**Meaning.**
For every `ρ ⊨ B`, and every assignment `η` of values to locals with `η(x) : ρ(Δ(x))` for each `x ∈ dom(Δ)`, evaluating `e` under `η` diverges, throws an anomaly, or produces a value of type `ρ(τ)`.
A local not in `dom(Δ)` is not used by `e`, so `e` places no demand on it.

### 8.1 Combining children

Every compound rule is an instance of one operation (`Infer.build`):

```
combine(T₁, …, Tₘ; C) with result τ,   where Tᵢ = ⟨Δᵢ; τᵢ⟩ ⊣ Bᵢ ; Rᵢ

  Δ(x) = δ                     if exactly one Δᵢ demands x, as δ
       = γₓ, fresh, with γₓ ≤ δᵢ for each demand δᵢ of x, otherwise
  B    = (B₁ ∪ … ∪ Bₘ).constrain(C ∪ { γₓ ≤ δᵢ })        the Bᵢ have disjoint variables
  R    = R₁ ∪ … ∪ Rₘ                                     concatenating each binding's flows
```

`γₓ ≤ δ₁, γₓ ≤ δ₂` is the "meet as a variable" of two demands on one local: whatever `x` is must satisfy both.
If the constraints fail, `e` is ill-typed, and the error is reported at the top-level form.

A binder removes its locals from `Δ` after relating them to what binds them (`Typing.without`).

## 9. Typing each expression

Each rule is given as the result type `τ`, the children combined, and the constraints `C` the construct adds.
`γ` is always fresh.
Expression syntax is Bridje's surface syntax; the analyser has already resolved names, so a symbol is known to be a local, a global, a key, a tag constructor and so on.

### 9.1 Literals and locals

```
(T-Lit)    42 ⇒ Int;  1.5 ⇒ Double;  1N ⇒ BigInt;  1M ⇒ BigDec;  "s" ⇒ Str;  true ⇒ Bool;  'f ⇒ Form
(T-Nil)    nil ⇒ ⊥?
(T-Local)  x ⇒ ⟨{x ↦ γ}; γ⟩ ⊣ ∅                 also a captured local
```

`T-Local` is where the duality shows: a use of `x` produces some `γ` and demands that `x` be a `γ`.
Nothing yet says what `γ` is; the parent will bound it.

### 9.2 Globals

```
(T-Global)  g ⇒ ⟨∅; τ'⟩ ⊣ B'       where Π(g) = ∀B. τ and (τ', B') = instantiate(∀B. τ)
            g ⇒ ⟨∅; γ⟩ ⊣ ∅          where g has no scheme (it failed to type)
```

Each use of a global is a fresh instance of its scheme, so `first` is used at `[Int]` in one place and at `[Str]` in another, as with HM's `let`-polymorphism, but only for top-level definitions.
A definition that failed to type is a fresh, unbounded variable to the forms after it, so its error is reported once rather than at every use.
An effect variable (`log`) is typed the same way from its `defx` scheme.

### 9.3 Keys

```
(T-Key)     .k  ⇒ Fn({.k⟨ᾱ⟩}ᵒ) → K(k)⟨ᾱ⟩          ᾱ fresh, one per parameter of k
(T-OptKey)  .?k ⇒ Fn({.?k⟨ᾱ⟩}ᵒ) → K(k)⟨ᾱ⟩?
```

A key is a function from a record carrying it to its value at the instance the record holds it at.
`.?k` accepts a record that may carry `k` and returns nil where it does not.

### 9.4 Tags

For a tag `T = ⟨E?, ᾱ, payload⟩`, an instance at fresh `β̄`:

```
own(T⟨β̄⟩)   = {.k⟨args(k)[ᾱ ↦ β̄]⟩ for each k of the payload}ᵒ        the tag's own record

(T-Ctor)     T             ⇒ Fn(own(T⟨β̄⟩)) → T⟨β̄⟩ᵒ                    a constructor used as a value
(T-Opaque)   Symbol, Var…  ⇒ Fn(γ₁ … γₙ) → T                         untracked arguments
(T-Single)   None          ⇒ None⟨β̄⟩ᶜ                                 a tag with no record
(T-TagLit)   T{.k₁ e₁ … .kₘ eₘ}
               ⇒ T⟨β̄⟩{X}ᶜ   with C = { τᵢ ≤ K(kᵢ)⟨instance of kᵢ⟩ }
               where every own key of T is among k̄ (else: `{k̄} lacks {…}`),
               X = the kᵢ that are not T's own, each at a fresh instance,
               the instance of an own key is args(k)[ᾱ ↦ β̄], and eᵢ ⇒ τᵢ
```

`T-TagLit` applies only when a tag constructor's one argument is a record literal, `T{…}` or `T({…})`; any other call of a constructor is `T-Ctor` under `T-App`.
It is more precise than `T-Ctor` applied to `T-Rec`: the result is closed and carries the extra keys.
So `Box{.value 1}` is `Box⟨Int⟩` (with `β ≥ Int`), and `User{.name "a", .email "b"}` is `User{.email}`.

`T-Opaque`'s `γᵢ` are unconstrained: `Symbol(42)` type-checks and fails at runtime (§12.3).

### 9.5 Records

```
(T-Rec)    {.k₁ e₁ … .kₘ eₘ}  ⇒  {.k₁⟨σ̄₁⟩ … .kₘ⟨σ̄ₘ⟩}ᶜ           σ̄ᵢ fresh instances
           C = { τᵢ ≤ K(kᵢ)⟨σ̄ᵢ⟩ }                              eᵢ ⇒ τᵢ

(T-With)   with(r, .k₁ e₁ … .kₘ eₘ)  ⇒  withKeys(τᵣ, S)         S = {.kᵢ⟨σ̄ᵢ⟩}, σ̄ᵢ fresh
           C = { τᵣ ≤ {}ᵒ } ∪ { τᵢ ≤ K(kᵢ)⟨σ̄ᵢ⟩ }
```

A literal is closed: it carries exactly its keys.
The value type flows into the key's declared type at a fresh instance, so `{.name 42}` under `.name : Str` fails, and `{.value 1}` under `.value : ∀a. a` gets `σ ≥ Int`.

`withKeys(τ, S)` (`Solver.kt`) is the type of a record with `S` written over it:

- `τ` nullable: **error** (`may be nil, and with needs a record`).
- `⊥`: `⊥`.
- `α`: `{S & α}`.
- `{S' & α}`: `{S' S & α}`, `S` overriding `S'`.
- `N⟨τ̄⟩{S'}` with `isRecord(N)`:
  - if no key of `S` is polymorphic *and* declared by any variant of `N`: the same name and arguments, with `S` added to the extra slots (bar keys `N` already requires), keeping closedness;
  - otherwise the name cannot keep its arguments (a new instance of `N`'s own polymorphic key would change them), so the result is the plain record of `slots(N⟨τ̄⟩{S'})` overridden by `S`, keeping closedness; each key's instances must be one, or **error**.
- anything else: **error** (`is not a record`).

### 9.6 Control

```
(T-Do)     do: e₁ … eₙ                  ⇒  τₙ            C = ∅
(T-If)     if: p a b                    ⇒  γ             C = { τₚ ≤ Bool, τₐ ≤ γ, τ_b ≤ γ }
(T-Let)    let: [x e₁] e₂               ⇒  τ₂            C = { τ₁ ≤ Δ₂(x) }, then x removed from Δ
(T-IfLet)  ifLet: [x e₁] a b            ⇒  γ             C = { τ₁ ≤ Δₐ(x)?, τₐ ≤ γ, τ_b ≤ γ }, x removed
```

- **`if` joins its branches in a variable**, so `if: p 1 "s"` fails when `Int` and `Str` meet in `γ`'s lower bound, and `if: p x 1` does *not* force `x` to be an `Int`: it puts `x`'s demand variable and `Int` both below `γ`.
- **`let` is monomorphic.** The bound expression's type flows into what the body demands of `x`; nothing is generalised.
- **`ifLet` strips nil.** `τ₁ ≤ δ?` means: whatever `e₁` is, apart from nil, is a `δ`. So `fn: f(x) ifLet: [y x] y 0` is `Fn(Int?) → Int`.

(Where a binder's local is unused, its demand is absent and the constraint is dropped.)

### 9.7 Functions and calls

```
(T-Fn)     fn: f(x₁ … xₙ) body          ⇒  Fn(δ₁ … δₙ) → τ_body
           δᵢ = Δ_body(xᵢ), or fresh if xᵢ is unused
           C = { ρ ≤ δᵢ for each ρ that recur in body sends xᵢ };   x̄ removed from Δ and R

(T-App)    f(a₁ … aₙ)                   ⇒  γ             C = { τ_f ≤ Fn(τ_{a₁} … τ_{aₙ}) → γ }
```

A function's parameter types are exactly what its body demands of them.
An application constrains the callee to be a function from the argument types to a fresh result, as in Simple-sub; `S-Fn` then decomposes it.

### 9.8 Loops

```
(T-Loop)   loop: [x₁ e₁ … xₙ eₙ] body   ⇒  τ_body
           C = { τ_{eᵢ} ≤ Δ_body(xᵢ) } ∪ { ρ ≤ Δ_body(xᵢ) for each ρ that recur sends xᵢ }
(T-Recur)  recur(a₁ … aₙ)               ⇒  ⊥            R = { xᵢ ↦ τ_{aᵢ} } for the enclosing binder's x̄
```

`recur` never returns, so it is `⊥` and fits any branch.
What it sends each binding is carried up in `R` until the binder (a `loop` or a `fn`) relates it to that binding's demand.

### 9.9 `case`

```
case: s  P₁ e₁ … Pₙ eₙ                 ⇒  γ             C ∋ τᵢ ≤ γ for each branch
```

The tag patterns `T{k̄}`, `T(r)`, `T` (bare) all instantiate one family at shared fresh arguments `β̄`:

- **One enum**: every tag pattern must be a variant of the same enum `E`. The scrutinee is demanded `τₛ ≤ E⟨β̄⟩ᵒ`.
- **One standalone tag**, no default: `τₛ ≤ T⟨β̄⟩ᵒ`, or `T⟨β̄⟩ᶜ` for a tag with no record.
- **Tags of different enums**: **error** (`belong to different enums`).
- **Standalone tags with a default**: no demand on the scrutinee (and different standalone tags, each at its own fresh arguments). This is unsound for a tag with type parameters (§12.3).

Within a branch:

```
T{.k₁ x₁ …}   each kᵢ must be one of T's own keys;  K(kᵢ)⟨args(kᵢ)[ᾱ ↦ β̄]⟩ ≤ Δ(xᵢ)
T(r)          T⟨β̄⟩ᵒ ≤ Δ(r)                     the value matched, as its tag
              (for a builtin meta: own(T⟨β̄⟩) ≤ Δ(r), its record)
x  (catch-all) the scrutinee narrowed ≤ Δ(x):    T'⟨β̄⟩ᵒ if exactly one variant T' of E remains unhandled
                                                  at this branch; else τₛ
e  (default, a trailing expression)  nothing
```

**Exhaustiveness**: with no default or catch-all, every variant of `E` must be handled, else **error** (`non-exhaustive case: missing Err`).
This is checked outside the lattice, by counting the tags handled.

A pattern destructures only the tag's own keys: the binding sees only what the tag declares. Any other key is read off the whole binding with `.?k`.
The analyser rejects the two patterns these rules do not cover: `T(r)` on an opaque builtin tag (`has no record to bind`), and `T{k}` on a key `T` does not declare.

### 9.10 `try`/`catch` and `throw`

```
(T-Try)   try: body catch: P₁ h₁ … finally: f   ⇒  γ        C = { τ_body ≤ γ, τ_{hᵢ} ≤ γ }
          a tag pattern must be a variant of Anomaly, bound as in §9.9 at its own fresh arguments
          a catch-all binding x:  Anomaly ≤ Δ(x)
          the finally expression is typed and its type discarded
```

`throw` is a builtin of type `Fn(Anomaly) → ⊥`, so `throw("boom")` is `Str is not a subtype of Anomaly`.

### 9.11 Effects, interop, the rest

```
(T-WithFx)  withFx: [fx₁ h₁ …] body   ⇒  τ_body
            each τ_{hᵢ} is checked against Π(fxᵢ) rigidly, as a declaration is (§10.2)
(T-Lang)    a foreign-language block  ⇒  its declared type, trusted
(T-Host)    an undeclared host member, host constructor or raw host object  ⇒  γ, unbounded
(T-Error)   an expression the analyser rejected  ⇒  γ, unbounded
```

A handler serves every use of the effect in the body, each at whatever instance of the effect's declared type it takes, so it must be as general as that type.

A declared host member (`decl: Inst/now() Inst`) is a global with the declared scheme, trusted.

### 9.12 Builtins

The builtins' schemes (`brj/builtins/Builtins.kt`, `brj/NsEnv.kt`) are axioms: nothing checks them against their implementations.

```
add, sub, mul, div : ∀{num(α)}. Fn(α, α) → α
lt, gt, lte, gte   : ∀{num(α)}. Fn(α, α) → Bool
eq, neq, isSame    : ∀α. Fn(α, α) → Bool
not                : Fn(Bool) → Bool
println            : ∀α. Fn(α) → α
nth                : ∀α. Fn([α], Int) → α
meta               : ∀α. Fn(α) → {}ᵒ
withMeta           : ∀α. Fn(α, {}ᵒ) → α                   wider than the runtime (§12.3)
throw              : Fn(Anomaly) → ⊥
itr                : ∀α. Fn(Iterable(α)) → Iterator(α)
itrHasNext         : ∀α. Fn(Iterator(α)) → Bool
itrNext            : ∀α. Fn(Iterator(α)) → α
spawn              : ∀α. Fn(Fn() → α) → BridjeFuture⟨α⟩      a host class
await              : ∀α. Fn(BridjeFuture⟨α⟩) → α
gensym             : γ, unbounded: it takes zero or one arguments, which no function type says
```

`num(α)` makes the solver reject any non-numeric or nil lower bound of `α` (§6, steps 5 and 9); `Num` is `Int` or `Double` (D41).
`add(1, 2.0)` fails because `Int ⊔ Double` is an error, not because of `num`.

## 10. Top-level forms: generalisation, declarations, effects

### 10.1 Generalisation

```
(Def)   Π ⊢ e ⇒ ⟨∅; τ⟩ ⊣ B           def: x e   gives   Π(x) = ∀B. τ
```

A top-level definition has no free locals (`Δ = ∅`), and every variable in `τ` and `B` is quantified.
This is the only place generalisation happens: there is no `let`-polymorphism for locals.
A top-level expression that is not a definition is typed and its type discarded.
A `def`'s metadata expression is type-checked too.

**Recursion.** A global cannot refer to itself in its own definition (the name does not resolve until the `def` has run); self-recursion is by `recur` (§9.8).
Redefining a global replaces its scheme; definitions already typed against the old one are not retyped.

### 10.2 Declarations: subsumption with rigid variables

A `decl:` before a `def:` gives its declared scheme `∀B_d. τ_d`, whose `B_d` may mark variables numeric (`decl: [a, Num(a)] double(a) a`).
Writing a tag or enum in a type form without its arguments gives it fresh ones, which the declaration quantifies.
A type form `N{…}` drops the keys every variant of `N` already declares, so `User{.name}` is `User` where `User` declares `.name`.
A `decl:` after the `def:` it names is stored and never checked (Q14).

```
(Decl)  inferred ∀B. τ,  declared ∀B_d. τ_d
        (τ', B') = instantiate(∀B. τ)
        α̂ fresh rigid for each variable of τ_d;  B'' = B' ∪ B_d[α ↦ α̂]
        B''.constrain(τ', τ_d[α ↦ α̂]) succeeds
        ───────────────────────────────────────────
        Π(x) = ∀B_d. τ_d
```

This is the usual check that the inferred scheme is *at least as general* as the declared one: for every instance of the declaration (the rigid variables stand for an arbitrary choice), the definition has that type.
Failure at a rigid variable is `declared type … is more general than the definition`; any other failure surfaces as the subtyping error itself (`X is not a subtype of Y`).
The declaration, not the inferred scheme, is then what callers see, so a declaration may narrow a type but never widen it.

Rigid variables only meet themselves, `⊥` and flexible variables (§5.7), so `decl: [a] f(a) Bool` over `def: f(x) not(x)` fails at `α̂ ≤ Bool`.

### 10.3 Macros, effects

- **A macro** is checked as `decl: m(Form, …) Form`: each parameter a `Form`, a rest parameter a `[Form]`.
- **`defx: fx τ default`** declares `fx : τ`; the default is checked against `τ` as a definition is against a declaration (§10.2), and `withFx` handlers likewise (§9.11).
  A `defx` type takes no leading vector, so no `Num`; a tag or enum named in it without arguments still gets fresh ones, which its scheme quantifies.

### 10.4 Errors

A type error is reported at the top-level form it arose in (D47).
The forms after it are still analysed and type-checked (a failed definition being an unbounded variable, §9.2), but not evaluated.

## 11. Simplification

The scheme a definition exports is the raw `∀B. τ`, with every intermediate variable.
`Scheme.simplify` (`Simplify.kt`) reduces it to something readable, for error messages and for rendering a var's `declaredType`.
**It is display only**: no checking depends on its output, so it bears on soundness only in that a declared scheme read back is the declaration itself.

The passes, in order (`Simplifier.run`):

### 11.1 Occurrence analysis (`walk`, `site`)

Walk the type from the root, tracking polarity: positive in a result, negative in a parameter, both in an invariant argument; a tag's, enum's or key's arguments by their computed variance.
At a variable occurring at polarity `p`, the variables reachable from it through edges of that polarity (lower edges if positive, strict upper edges if negative) all *occur* at `p`, and are recorded as *co-occurring* with each other (each variable's co-occurrence set is intersected with each site's reach, as in Simple-sub).
Concrete bounds reached are walked in turn: a positive variable's lower bound positively, a negative variable's upper bound negatively.

### 11.2 Merging (`unifyCycles`, `unifyCoOccurring`)

- **Cycles**: variables each of which is below the other (through lower-bound edges) are one variable.
- **Co-occurring at both polarities**: two variables each of which co-occurs with the other at both polarities are indistinguishable, and merged, provided their concrete bounds agree and both or neither are numeric.
- **Co-occurring at one polarity**: as the results of two joined calls are. They are merged where their bounds *the other way* are the same (closing over variable edges, excluding the pair), so neither would take on a bound only the other had, and their concrete bounds this way agree; at negative polarity, both or neither must be numeric, where a variable fed only by numeric ones counts as numeric (`numericLike`).

### 11.3 Resolution (`resolve`)

Each merged variable `α` is resolved to a type, or kept as a variable:

- **Only positive**: `α` is the join of what flows into it (`produced`). If that is exactly one thing (one concrete type, one kept variable, or one record on a variable), `α` is that, with nil if nil flows in; if nothing flows in, `⊥` (or `nil`). If several remain, `α` is kept.
- **Only negative**: `α` is its one demand (`consumed`), if there is exactly one, concrete or a variable.
- **Both, or recursive**: kept.

A variable that reaches itself during resolution is kept, so a recursive type renders as its variable and constraints.
A numeric variable resolves to a single part only where that part is itself numeric.

### 11.4 Constraints of kept variables (`lowersOf`, `uppersOf`)

For each kept variable, its lower and upper bounds are collected, composing away unnamed (resolved) variables between kept ones, so that a constraint mentions only concrete types and kept variables.
Composing through a record on an unnamed variable carries its keys along.
Through an unnamed variable holding a primitive or a record, an upper bound gains that kind's demand (`joinDemand`): with no top, only an `Int` may flow into a variable already holding one.
Demands of one kind on one variable merge where they are compatible (`mergeUppers`).
This repeats until no new variable surfaces.

Then:

- a pair `α ≤ β`, `β ≤ α` keeps one direction;
- a lower bound below another lower bound of the same variable, or an upper above another upper, is dropped, as is `{}` above a variable a record flows into (all compared with no variables present, by the solver itself).

### 11.5 Folding (`fold`)

- **A primitive bound is the primitive.** `ι ≤ α` or `α ≤ ι` (non-null): since joins are per kind, only `⊥` lies below a primitive besides itself, so `α` is replaced by `ι` throughout.
- **A record demand on a parameter goes on the parameter.** `α ≤ {.k₁ … .kₙ}ᵒ`, unnamed, its keys required and monomorphic, where `α` occurs negatively and nowhere invariantly: each negative occurrence becomes `{.k̄ & α}`, and a positive `{S & α}` with `S ⊆ k̄` becomes `α`.

### 11.6 Rendering

Kept variables are named `a`, `b`, … in order of appearance.
A numeric kept variable shows `Num(a)`, unless it flows into another numeric one among the constraints.
Each remaining constraint `L ≤ U` shows as `isa(L, U)`.
So:

```
[a] Fn([[a]] a)                          first
[a, Num(a)] Fn([a, a] a)                 add
[a] Fn([Bool, {& a}] a)                  fn: (p r) if: p with(r, .dirty true) r
[a, isa([Int], a)] Fn([Bool, a] a)       fn: (p x) if: p x [1]
```

The third reads: for any record type `a`, given a `Bool` and an `a`, return an `a`; the `with` branch's `{.dirty & a}` was absorbed into `a` by `S-OnVarA`.
The fourth needs a constraint because `x`'s type and `[Int]` join in the result, and with no unions the join depends on what `a` is (D32).

D29 sets the rule a rendering is held to: it may show a supertype of the real type where marked as approximate, and must never show one that would reject a program the checker accepts.

## 12. Soundness

### 12.1 The claim

Soundness here means: **a program the checker accepts does not fail at runtime with a type error** — no `ClassCastException`, no call of a non-function, no missing key on `.k`, no `with` on a non-record, no non-exhaustive match, no nil where a non-nullable value is required, no wrong arity — except for anomalies the program throws and host code that fails.

### 12.2 The argument, in outline

**The system is not sound as built**: §12.3 lists programs that type-check and then fail with a type error, five of them run to confirm it.
What follows is the argument for everything else, and each hole in §12.3 is a place one of its steps fails.

1. **The solver's output is satisfiable.**
   By closure (§6), every lower bound of every variable has been checked against every upper bound.
   Fix each rigid variable at an arbitrary type, as the declaration being checked quantifies over all of them, and take `ρ(α)` to be the join of `α`'s lower bounds: its concrete `J`, its variable edges, and its records on variables `M`, each resolved under `ρ`, as a regular tree where bounds are cyclic.
   Each lower bound is below `ρ(α)` because a join is an upper bound (step 2), and `ρ(α)` is below each upper bound because the stored meet is below each one (step 2) and every part of `ρ(α)` was checked against it.
   The numeric flag is checked against every lower bound as it arrives.
   So `ρ ⊨ B`.
2. **Join and meet are sound approximations.**
   Every row of §7.1 gives an upper bound of both arguments, and every row of §7.2 a lower bound, under the constraints computing it adds.
   - Where they are not *least* or *greatest* (functions of different arity; host classes with only interfaces in common), the checker rejects programs it could accept, which costs completeness only.
   - One qualification: `S-OnVarA`'s absorption and `joinGiven`'s dropping of a key one side gives can leave a *closed* type that does not mention a monomorphic key the value carries. Closedness is only ever relied on for polymorphic keys (`S-Rec`, *instance*: a monomorphic key has its one type wherever it is), and closed demands barely arise (§5.5), so the bound is exact for every use the rules make of it.
3. **Each typing rule is sound for its construct.**
   With the judgement's meaning (§8), each rule in §9 is checked against what the runtime does: `T-If` needs both branches below the result; `T-Key` needs the record to carry the key at an instance whose type is the result; `T-With` mirrors the runtime's record update for every type the runtime treats as a record; `T-Recur` is `⊥` as it does not return.
   The `case` rule's exhaustiveness check, and the tag literal's own-key check, are what keep a `case` from falling through and a tag's record from lacking a key it declares.
4. **Generalisation is sound at the top level** for definitions whose value is a function or an immutable value: `Δ = ∅` there, so no local's type is captured by the quantified variables. A mutable host object is the exception (Q20).
5. **Declarations are sound** because the declared scheme is exported only after every rigid instance of it has been shown to be an instance of the inferred one (§10.2).
6. **Records and keys are sound** because a key has one declared type per instance, a slot records the instance, and the rules refuse to let an open record answer for a polymorphic key it does not mention (`S-Rec`, *instance*) or for `with` to keep a name whose arguments it has changed (`withKeys`). This leans on a key's type being instantiated correctly, which it is not where the type contains `{S & α}` (§12.3).

This is an outline, not a proof: the record, enum and `{S & α}` rules are argued case by case and exercised by the tests in `language/src/test/kotlin/brj/types`.

### 12.3 Known gaps

**Confirmed: these programs type-check and fail at runtime.**
Each was run against the checker as of this file, and each is a step of §12.2 failing.

- **A key type containing `{S & α}` keeps `α` when the key is instantiated at a concrete type** (Q16, step 6).
  `KeyType.at` substitutes the key's parameters with `Type.substitute`, which keeps a record-on-variable's base where the substitute is not a bare variable (`Scheme.kt`), so the key's own parameter leaks out unbounded into another form's typing.

  ```
  decl: .x Int, .y Int, .zz Int
  decl: [a] .k {.x & a}
  tag: Foo{.k({.y})}
  def: g(foo)
    case: foo
      Foo{k} .zz(k)
  g(Foo{.k {.x 1, .y 2}})                  // reads .zz off {.x 1, .y 2}
  ```

  The same `case` inside a single form is rejected, as there the leaked variable is still bounded. Rebuilding the type with `withKeys(substitute, S)` in `KeyType.at`, or forbidding `{S & α}` in a key's type, closes it.
- **`case` over a standalone tag with a default places no demand on the scrutinee** (§9.9, step 3), so a parametric tag's arguments are fresh and a destructured key is anything.

  ```
  decl: [a] .value a
  tag: [a] Box{.value(a)}
  def: f(x)
    case: x
      Box{value} add(value, 1)
      0
  f(Box{.value "s"})                        // add("s", 1)
  ```

  Q15 asks whether such a `case` may mix families; this is the same rule failing for one.
- **A form, an anomaly and a loc are records to the checker and not at runtime** (D50, step 3): `with('foo, .k 1)` type-checks and fails with `with needs a record, got foo`. They become records at runtime under #151.
- **An opaque builtin tag's arguments are untracked** (`T-Opaque`, step 3): `Symbol(42)` type-checks and fails with a `ClassCastException`.
- **`withMeta`'s scheme is wider than the runtime** (§9.12, step 3): `withMeta(1, {})` type-checks and fails with `object does not support metadata`; only vectors, sets, records, forms and vars carry metadata.

**Accepted on purpose: the checker vouches for nothing here.**

- **Untyped values are anything.** An undeclared host member, a raw host object, `gensym`, an expression the analyser rejected, and a definition that failed to type (D47) are each a fresh, unbounded variable (`T-Host`, `T-Error`, `T-Global`), which flows anywhere: a dynamic type.
- **Declarations at the boundary are trusted.** Host member declarations, foreign-language blocks and the builtins' schemes have the type they are declared at; nothing checks them against the host. An interop receiver takes no type arguments, so `decl: [a] AL/.add(a) Bool` admits any element.
- **Raw generic classes are compatible with any arguments** (§5.4), as Java's raw types are, which makes subtyping through them non-transitive.
- **No value restriction** (Q20, step 4). A `def` of a mutable host object generalises: `def: xs AL/new()` is usable at `ArrayList(Int)` and `ArrayList(Str)` at once, so a read typed `Int` can yield a `Str`. The route proposed on #129 generalises only syntactic values.
- **The numeric demand is a predicate on a variable, not a type**, so a numeric variable with no lower bound (dead code such as `add(throw(…), throw(…))`) still joins with a non-numeric value.
- **Redefinition** of a global, tag, enum or key retypes nothing typed against the old one, and **a `decl:` after its `def:`** is never checked (Q14).
- **A macro or an effectful definition used first-class** types as the plain function it is defined as.

**Latent: unreachable today.**

- **`{S & α} ≤ R'` with `R'` closed does not check that `R'` admits `S`'s keys** (`S-OnVarL`): they are passed to `α` as optional slots, so `{.value⟨Str⟩ & α} ≤ {}ᶜ` holds. The only closed demand the rules produce is a record-less tag, which meets `with`'s `{}` demand at `⊥`; it wants fixing before a closed type form exists.
