# ADR: Concurrency and Effects in Bridje

## Motivation

[Allium](https://github.com/juxt/allium) is a behavioural specification language — it takes informal specs and gives them a more formal structure.
Bridje is coming from the other direction: it's a formal, executable language that aims to read as close as possible to a spec like Allium, while remaining fully executable.

The question driving this ADR: can idiomatic Bridje express a protocol (like Raft consensus) so clearly that comparing it to the Allium spec is trivial?

To achieve that, idiomatic Bridje should:

- **Separate essential from incidental complexity** — the protocol logic (essential) should not be interleaved with I/O, serialisation, or concurrency plumbing (incidental). (cf. Moseley & Marks, "Out of the Tar Pit")
- **Separate pure from side-effecting** — pure domain logic reads like a spec; side effects are declared, explicit, and substitutable.
- **Separate high-level from low-level** — the "what" of a protocol rule should be expressible without the "how" of network transport, persistence, or timer management.
- **Colocate related concepts** — an entity's state, its valid transitions, and the rules that trigger them should live together, not scattered across layers.

These goals complement the existing Bridje goals (see README): expression-oriented LISP core, immutability by default, static types that reflect the domain without hindering progress, side effect management, and structured concurrency.

## Context

Clojure is the reference point — already close to "spec as code", but with gaps.
The main gaps: side effects are invisible (mixed into domain logic), sum types are absent (roles/variants are ad-hoc maps), and concurrency is bolted on (core.async is powerful but not first-class).
The question: what's the minimal set of language features that closes those gaps?

## Decision 1: Effects as lexically-scoped implicit parameters

**Decision**: Traits define the interface shape, effect vars are declared with `defx`, and effects are bound with `withFx`.
The effect declaration is a bindable var of any type (often a trait), not a special kind of interface.
Effects are orthogonal to traits — a trait defines a record of functions, an effect is a scoped value.
Default implementations can be provided at the declaration site.

```bridje
trait: RaftNetwork
  decl: sendVoteRequest(ServerId, VoteRequest)

defx: net RaftNetwork

defx: log Fn(Str) println       // with a default
```

Call sites just call the function — `net.sendVoteRequest(to, req)` — with no indication that it's an effect.
The compiler fully infers the effect set of every expression.
Users do not annotate effects.

**Why not algebraic effects (Koka-style)?**
Algebraic effects add handler composition and resumable continuations.
For the "code reads like the spec" goal, that machinery isn't needed — the value is in the separation of interface from implementation, not in the handler mechanics.
Lexically-scoped implicit parameters achieve the same readability and testability with less conceptual overhead.

**Why not monads (Haskell-style)?**
Monadic do-notation changes how the code reads — the plumbing becomes visible.
The goal is that effectful code reads identically to pure code at the call site.

**Why not explicit parameter passing (Kotlin DI-style)?**
Threading effect implementations through every call is honest but noisy.
The spec doesn't say "pass the network implementation to handleVoteRequest" — it just says "send a VoteResponse."
Lexical scoping means the compiler can statically verify that all effects are bound, without the noise.

**Why lexically scoped, not dynamic vars?**
Dynamic vars (Clojure's `binding`) are thread-local and escape analysis is hard.
Lexical scoping means the compiler knows at every call site whether the effect is bound, because it can see the enclosing `withFx`.
The implementation is dictionary passing — same as explicit parameters, but the compiler threads them through.

**Why default implementations?**
The common case is zero wiring — production code just calls the functions and gets the default.
You only `withFx` when you want to override (tests, mostly).
Same pattern as Clojure's dynamic vars with root bindings, but with compile-time safety.

## Decision 2: No async/suspend in the type system

**Decision**: Bridje does not have `async`, `suspend`, `Async(T)`, or any other marker for asynchronous operations.
Everything looks like a synchronous function call.
The runtime uses JVM virtual threads — blocking a virtual thread is cheap, so there's no need to distinguish sync from async.

**Why not Kotlin-style suspend?**
`suspend` is function colouring by another name.
It infects signatures, splits the world into sync and async, and adds noise that doesn't correspond to anything in the spec.
The Allium spec doesn't distinguish "this rule is async" — it just says what happens.

**Why not `Async(T)` as a return type?**
`Async(T)` as a first-class value (a future) raises questions about structured concurrency (futures escaping their scope).
Virtual threads make this unnecessary: everything can block transparently.

**Why virtual threads?**
Bridje targets GraalVM.
Java's virtual threads and structured concurrency APIs are approaching stability.
Leaning on the platform avoids reinventing concurrency and keeps the language focused on its novel contributions.

## Decision 3: Structured concurrency via scopes

**Decision**: Concurrency primitives are `scope:` and `s.fork:`, thin wrappers over Java's `StructuredTaskScope`.

```bridje
scope: s
  s.fork: sendAppendEntries(server, peer1)
  s.fork: sendAppendEntries(server, peer2)
```

**Why not full compile-time scope safety (Rust lifetimes)?**
The additional type system machinery is significant for a narrow problem.
If the syntax encourages the right pattern (scope and forks visually nested), runtime enforcement of the rare misuse case is pragmatically sufficient.

## Decision 4: Procs — CSP-style state machines driven by a select function

**Decision**: A proc is a loop over `State -> [Select(State)]`: given the current state, declare what to wait for (channels, timeouts), and which handler to call for each branch.
The runtime owns the loop.
Each handler is a pure function `(State, Msg) -> State` that calls `defx` for effects.

```bridje
def: serverProc(rpcCh)
  fn: [state]
    let: [rpc proc:Recv(rpcCh, handleRpc)
          el proc:Timeout(#dur("PT0.15S"), startElection)]
      case: state.role
        Leader(_): [rpc, proc:Timeout(#dur("PT0.02S"), sendHeartbeats)]
        Candidate(_): [rpc, el]
        Follower(_): [rpc, el]
```

The select function is re-evaluated after each handler, so behaviour changes naturally with state.
No manual timeout re-arming, no imperative lifecycle management.

**Select is essential, channels are incidental.**
The select function (what to wait for in each state) is domain logic.
The channel implementations (Kafka, TCP, in-memory) are wiring.

**DST via inspectable select.**
The select function returns data.
A test harness calls the select function directly, inspects the branches, chooses which fires (seeded random), checks invariants.
Same proc function in production and simulation.

**Why not Clojure-style agents?**
Agents (a state behind a serialised queue) don't capture the select pattern — which channels to listen on varies with state.
The select function makes this explicit and inspectable.

**Why not Erlang-style actors?**
Erlang actors couple process identity to the mailbox.
Procs are simpler — the domain logic lives in plain functions, not in actor method definitions.
An "agent" is just a proc where one of the select's channels is a public mailbox that others can send to.

**Why not Pony-style capability types?**
Immutable-by-default makes sharing safe.
No iso/val/ref/tag annotations needed.
The proc runtime provides single-threaded execution; the type system doesn't need to enforce it.

See issue #37 for full design details and open questions.
See `notes/raft/raft.brj` for the Raft consensus example.

## The aim: one artifact, not two

The ideal is that Bridje code *is* the spec — not a separate artifact that can drift out of sync with a specification document.

A behavioural spec like Allium captures the essence of a protocol: the domain types, the rules, the invariants.
Bridje aims to express the same essential information in executable form, with the incidental machinery (I/O, concurrency, resource lifecycle) factored out to the edges.

The test: can someone who knows the Raft paper read the Bridje implementation and verify it implements the protocol correctly, without needing to understand the runtime, channels, or proc model?
The lifecycle and wiring should be a small, separate section at the bottom of the file — not interleaved with the protocol logic.

This is a stronger goal than "functional core, imperative shell."
It's closer to: the functional core should read like a specification, and the imperative shell should be data (the select function) rather than imperative control flow.

The proc/select model serves this directly: the select function is the only concurrency-aware piece, and it's expressed as data — what to wait for, and what to do when each event arrives.
Everything else is pure functions on immutable state.

This also enables simulation testing from the same artifact.
A test harness calls the select function directly, inspects the branches, chooses which fires, checks invariants.
Same code in production and simulation — no mocking, no special test mode.

## Summary

Two novel features enable this:

1. **Effects** (lexically-scoped implicit parameters) — separate pure domain logic from I/O without changing how the code reads at the call site.
2. **Procs** (CSP state machines with inspectable select) — express concurrency as data, enabling both execution and deterministic simulation from the same code.

Everything else is standard type system features (structural records, nominal tags, traits) or thin wrappers over the JVM (structured concurrency, virtual threads).

## Validation

A clean-room test was run: an LLM with no prior Bridje knowledge was given only the language reference docs (`notes/TYPES.md`, `notes/SYNTAX.md`) and the proc model description.
It produced a Raft implementation (`notes/raft/raft-clean.brj`) that:

- Follows the same file structure as the hand-written version (types → helpers → handlers → lifecycle)
- Expresses the same essential domain logic with minimal incidental noise
- Uses effects for I/O, pure functions for protocol logic, and the select function for concurrency
- Is recognisably close to the Allium spec for the same protocol — the type definitions are near 1:1, and the handler logic maps rule-by-rule

The main incidental noise in the executable version compared to a declarative spec is state threading (`state.with(...)`, returning new state) — a predictable, mechanical pattern that doesn't obscure the essential logic.

See `notes/raft/raft.allium` for the Allium spec and `notes/raft/README.md` for the side-by-side comparison.
