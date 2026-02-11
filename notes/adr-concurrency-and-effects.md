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

**Decision**: Interfaces are declared with `definterface`, effect vars are declared with `defx`, and effects are bound with `withFx`.
Implemented as dictionary (implicit parameter) passing.
The effect declaration is a bindable var of an interface type, not a special kind of interface.
Default implementations can be provided at the declaration site.

```bridje
definterface: RaftNetwork
  decl: sendVoteRequest(to, req)

defx: raftNet RaftNetwork
  reify: RaftNetwork
    def: sendVoteRequest(to, req)
      <default impl>
```

Call sites just call the function — `raftNet.sendVoteRequest(to, req)` — with no indication that it's an effect.
The compiler tracks which effects a function transitively requires.

**Why not algebraic effects (Koka-style)?**
Algebraic effects add handler composition and resumable continuations.
For the "code reads like the spec" goal, that machinery isn't needed — the value is in the separation of interface from implementation, not in the handler mechanics.
An interface of functions with implicit passing achieves the same readability and testability with less conceptual overhead.

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
Explored during discussion — `Async(T)` as a first-class value (a future) raises questions about structured concurrency (futures escaping their scope).
Virtual threads make this unnecessary: everything can block transparently.

**Why virtual threads?**
Bridje targets GraalVM.
Java's virtual threads and structured concurrency APIs are approaching stability.
Leaning on the platform avoids reinventing concurrency and keeps the language focused on its novel contributions.

## Decision 3: Structured concurrency via scopes

**Decision**: Concurrency primitives are `scope:` and `s.fork:`, thin wrappers over Java's `StructuredTaskScope`.
Scopes are `iso` (unique ownership) — they can't be aliased or stashed, so the compiler prevents them escaping their lexical block.

```bridje
scope: s
  s.fork: sendAppendEntries(server, peer1)
  s.fork: sendAppendEntries(server, peer2)
```

**Why iso for scopes?**
A scope must be the sole owner because it's responsible for cleanup — cancelling children, propagating exceptions.
This is literally what `iso` means in the capability system.
No special "scope can't escape" rule needed — the capability system already provides it.

**Why not full compile-time scope safety (Rust lifetimes)?**
Explored during discussion.
The additional type system machinery is significant for a narrow problem.
If the syntax encourages the right pattern (scope and forks visually nested), runtime enforcement of the rare misuse case is pragmatically sufficient.
Pony-style capabilities (specifically `iso`) give most of the safety without the complexity of full lifetime tracking.

## Decision 4: Agents for serialised stateful processes

**Decision**: Agents are Clojure-style — a mutable state behind a serialised queue.
You `send` a `State -> State` function to the agent.
One special method: `onTimeout(duration, f)` — if no message arrives within the duration, `f` is sent to the agent.
The timeout resets on every message.

```bridje
let: [server agent(initialState)]
  server.onTimeout(electionTimeout(), startElection)
  server
```

Sending messages:

```bridje
server.send(handleVoteRequest(request))
```

Timeout behaviour is reconfigured from within sent functions (e.g. on role transition):

```bridje
def: becomeLeader(server, state)
  server.onTimeout(heartbeatInterval, sendHeartbeats)
  initLeaderState(state)
```

**Why not Erlang-style actors?**
Erlang actors couple process identity to the mailbox.
Agents are simpler — they're just a ref behind a queue.
The domain logic lives in plain functions (`State -> State`), not in actor method definitions.
This keeps the logic testable without the actor machinery.

**Why not Pony-style actors?**
Pony actors are tightly coupled to Pony's capability system — the actor is the unit of isolation, with a built-in mailbox.
This doesn't interact well with standalone typed channels or CSP patterns.
Agents with Pony-inspired capabilities (agent reference is `tag`, internal state is `ref`) get the safety benefits without the coupling.

**Why not actor classes with typed methods?**
Explored during discussion — actor classes with method-call syntax (`server.handleVoteRequest(request)`) read nicely.
But returning an `ActorAction` ADT from every method handler is boilerplate, especially in an expression language where the last value is the return.
Agents with `send` avoid this — the sent function is just a function, the timeout is mutable state on the agent.

**Why `onTimeout` instead of a return value?**
The timeout resets on every message — that's the default behaviour.
Making every handler return a timeout instruction is repetitive.
`onTimeout` sets the policy; handlers only interact with it when the policy changes (e.g. role transitions).

**Why not channels?**
Channels were explored as the primary concurrency primitive (CSP-style, like core.async or Kotlin channels).
Agents subsume the main use case — serialised message handling with timeouts.
Channels introduce lifecycle questions (who creates, who closes, what happens when one end disappears) that agents avoid by tying the mailbox to the agent's lifetime.
If unusual topologies (fan-out, merge) are needed later, channels could be added as a lower-level primitive.

## Decision 5: Pony-inspired capabilities (val/ref/iso/tag)

**Decision**: Three-and-a-half capabilities, with sensible defaults so most code never mentions them:

| Capability | Meaning | Default for |
|---|---|---|
| `val` | Immutable, shareable everywhere | Records, data types |
| `ref` | Mutable, only within owning process | Explicit opt-in |
| `iso` | Unique owner, for resource lifecycle | Scopes |
| `tag` | Opaque, can only send messages to | Agents |

`val` is transitive — a `val` cannot contain a `ref`.
Agent `send` and `fork` closures can only close over `val` and `tag`.
Effect implementations follow the same rules as any parameter — their capability is declared on the `defx`.

**Why not Pony's full six capabilities?**
Pony has `iso`, `trn`, `ref`, `val`, `box`, `tag`.
`trn` (write-unique) and `box` (read-only, possibly aliased) add complexity for edge cases.
With immutable-by-default, `val` covers the vast majority of values.
Three capabilities plus `iso` for resources covers the practical cases without the learning curve.

**Why not just immutable-by-default with no capability system?**
You need `ref` for agent internal state — that's the whole point of agents.
You need `iso` for scopes — to prevent them escaping.
You need `tag` for agent references — to prevent reading state from outside.
Without capabilities, these safety properties would need ad-hoc rules. The capability system provides them uniformly.

**Why defaults per type?**
So that most code never mentions capabilities.
A record is `val` — you don't write `val MyRecord`.
An agent reference is `tag` — you don't write `tag Server`.
The capability system helps without getting in the way, per the README's stated goal.

## Decision 6: Effect interfaces are just interfaces

**Decision**: No separate concept for "effect interfaces" vs "normal interfaces."
`definterface` declares an interface.
`defx` declares a bindable var of that interface type.
They're orthogonal — the same interface can be used with or without `defx`.

**Why not a special effect-interface?**
If effect interfaces and normal interfaces are different kinds, you end up with two parallel worlds — duplication, and awkward interop when something starts as one and becomes the other.
Kotlin has this mildly with `suspend` interfaces vs regular interfaces.
Keeping them the same means refactoring between "explicit parameter" and "implicit effect" is just adding or removing a `defx` — no interface changes needed.

## Summary

The core insight: effects (implicit parameter passing) and agents (serialised state + timeout) are the two novel concurrency/effect features.
Everything else is either standard type system features (sum types, records, interfaces) or thin wrappers over the JVM (structured concurrency, virtual threads).
Pony-inspired capabilities unify the safety story without dominating the syntax.
The result should be a language where protocol logic reads almost identically to a behavioural spec, with the implementation machinery (I/O, concurrency, resource lifecycle) handled by the type system and runtime, invisible at the call site.
