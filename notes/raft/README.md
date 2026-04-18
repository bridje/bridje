# Allium and Bridje: Raft Side-by-Side

Allium and Bridje approach the same problem from opposite directions.
Allium takes informal specs and gives them formal structure.
Bridje takes executable code and tries to make it read like a spec.

This document puts them side by side on the same protocol (Raft consensus) to see how close Bridje gets to being a spec that also executes.

The Bridje examples are from a clean-room implementation (`raft-clean.brj`) — written by an LLM given only the language reference docs and the Raft paper, with no access to the Allium spec or the original hand-written implementation.

See `raft.allium` and `raft-clean.brj` for the full versions.

---

**TL;DR:** For this example at least, Bridje gets close enough that a reader familiar with Raft could verify the implementation against the spec rule-by-rule.
The type definitions are near 1:1.
The handler logic maps directly to Allium's rules.
The main incidental noise is state threading (`with(state, ...)`) — a predictable pattern that doesn't obscure the essential logic.
The proc/select model makes concurrency declarative, bridging the biggest gap between executable code and declarative specs.

## Data Model

### Allium

```allium
value LogEntry {
    term: Integer
    index: Integer
    command: String
}

entity Server {
    cluster: Cluster
    current_term: Integer
    voted_for: Server?
    log: List<LogEntry>
    commit_index: Integer
    last_applied: Integer
    role: Leader | Follower | Candidate

    -- Derived
    last_log_index: log.count
    last_log_term: if log.count > 0: log.last.term else: 0
}

variant Leader : Server {
    next_index: Map<Server, Integer>
    match_index: Map<Server, Integer>
}
```

### Bridje

```bridje
decl:
  {:id Int
   :cluster [Int]
   :currentTerm Int
   :votedFor Int?
   :log [LogEntry]
   :commitIndex Int
   :lastApplied Int
   :role ServerRole}

tag: LogEntry({:term, :index, :command})
tag: ServerState({:id, :cluster, :currentTerm, :votedFor, :log, :commitIndex, :lastApplied, :role})

enum: ServerRole
  tag: Follower({:knownLeader})
  tag: Candidate({:votesReceived})
  tag: Leader({:nextIndex, :matchIndex})

def: lastLogIndex(ServerState({log}))
  if: empty?(log)
    0
    :index(last(log))

def: lastLogTerm(ServerState({log}))
  if: empty?(log)
    0
    :term(last(log))
```

### Commentary

Near 1:1 after accounting for naming conventions (`snake_case` vs `camelCase`).

Bridje's `decl:` + `tag:` separation is a Clojure-ism that pays for itself when keys are reused — `:from`, `:to`, `:term` appear in four message types and are declared once.
The parameterised collection types (`[LogEntry]`, `Map(Int, Int)`, `#{Int}`) carry the same information as Allium's `List<LogEntry>`, `Map<Server, Integer>`, `Set<Server>`.

Allium's `variant Leader : Server` maps to Bridje's enum with per-variant keys.
Derived fields (`lastLogIndex`, `lastLogTerm`) become standalone functions with destructuring rather than inline computed fields — they live separately from the type definition, but they're right next to it.

## A Simple Rule: Starting an Election

### Allium

```allium
rule FollowerStartsElection {
    when: follower: Follower.election_deadline <= now

    requires: follower.role = Follower

    ensures:
        follower.current_term = follower.current_term + 1
        follower.role = Candidate
        follower.voted_for = follower
        follower.votes_received = { follower }

        for each peer in follower.cluster.servers:
            if peer != follower:
                VoteRequest.created(
                    from: follower, to: peer,
                    term: follower.current_term,
                    last_log_index: follower.last_log_index,
                    last_log_term: follower.last_log_term
                )
}
```

### Bridje

```bridje
// The handler — pure state transform + effects
def: _startElection(state)
  let: [newTerm inc(:currentTerm(state))
        votes #{:id(state)}
        newState with(state, :currentTerm newTerm, :votedFor :id(state), :role Candidate{:votesReceived votes})]
    do:
      doseq: [peer :cluster(newState)]
        when: neq(peer, :id(newState))
          :sendVoteRequest(net, peer,
            VoteRequest:
              {:term newTerm
               :from :id(newState)
               :lastLogIndex lastLogIndex(newState)
               :lastLogTerm lastLogTerm(newState)})
      newState

// The select function — declares when _startElection fires
def: serverProc(rpcCh)
  fn: [state]
    let: [rpc proc/Recv(rpcCh, handleRpc)
          electionTimeout proc/Timeout(t/dur("PT0.15S"), onElectionTimeout)]
      case: :role(state)
        Leader(l) [rpc, proc/Timeout(t/dur("PT0.05S"), onHeartbeatTimeout)]
        Candidate(c) [rpc, electionTimeout]
        Follower(f) [rpc, electionTimeout]
```

### Commentary

The handler is a pure function: takes state, returns new state, calls effects.
No agent ref, no timeout re-arming, no concurrency plumbing.

The state update `with(state, :currentTerm newTerm, :votedFor :id(state), :role Candidate{:votesReceived votes})` reads close to the spec's declarative assignments.
The `doseq`/`when` loop maps directly to the spec's `for each peer ... if peer != follower`.
`:sendVoteRequest(net, ...)` maps to `VoteRequest.created(...)` — same information, different verb.

The election timeout lives in the select function, not in the handler.
`proc/Timeout(t/dur("PT0.15S"), onElectionTimeout)` says "if nothing happens for 150ms, call onElectionTimeout" — the same concept as the spec's `election_deadline <= now`, but as a select branch rather than a temporal condition.
When the role changes to Leader, the next select evaluation returns a heartbeat timeout instead — no manual re-arming.

**Incidental noise:** The `with(state, ...)` call and `newState` return at the end.
This is the tax for executability — Allium just says `follower.current_term = follower.current_term + 1` and it's done.
Bridje has to build and return a new state.
It's predictable and mechanical, not confusing — just more verbose.

## A Complex Rule: HandleAppendRequest

### Allium

```allium
rule HandleAppendRequest {
    when: request: AppendRequest.created

    let receiver = request.to

    let log_consistent =
        request.prev_log_index = 0
        or (receiver.log.count >= request.prev_log_index
            and receiver.log.at(request.prev_log_index):term = request.prev_log_term)

    ensures:
        if request.term >= receiver.current_term:
            if request.term > receiver.current_term:
                receiver.current_term = request.term
                receiver.voted_for = null

            receiver.role = Follower
            receiver.known_leader = request.from

            if log_consistent:
                receiver.log = receiver.log.take(...):append(request.entries)
                if request.leader_commit > receiver.commit_index:
                    receiver.commit_index = min(request.leader_commit, receiver.log.count)
                AppendResponse.created(... success: true ...)
            else:
                AppendResponse.created(... success: false ...)
        else:
            AppendResponse.created(... success: false ...)
}
```

### Bridje

```bridje
def: _handleAppendRequestInTerm(state, {from, term, prevLogIndex, prevLogTerm, entries, leaderCommit})
  if: not(_logConsistent(state, prevLogIndex, prevLogTerm))
    do:
      :sendAppendResponse(net, from, AppendResponse{:term :currentTerm(state), :from :id(state), :to from, :success? false, :lastLogIndex lastLogIndex(state)})
      state
    let: [withEntries _appendEntries(state, prevLogIndex, entries)
          newCommitIndex if: gt(leaderCommit, :commitIndex(withEntries))
            min(leaderCommit, lastLogIndex(withEntries))
            :commitIndex(withEntries)
          updated with(withEntries, :commitIndex newCommitIndex)
          applied _applyCommitted(updated)]
      do:
        :sendAppendResponse(net, from, AppendResponse{:term :currentTerm(applied), :from :id(applied), :to from, :success? true, :lastLogIndex lastLogIndex(applied)})
        applied

def: handleAppendRequest(state, req)
  cond:
    lt(:term(req), :currentTerm(state))
      do:
        :sendAppendResponse(net, :from(req), AppendResponse{:term :currentTerm(state), :from :id(state), :to :from(req), :success? false, :lastLogIndex lastLogIndex(state)})
        state

    gt(:term(req), :currentTerm(state))
      let: [stepped stepDown(state, :term(req), :from(req))]
        _handleAppendRequestInTerm(stepped, req)

    let: [asFollower case: :role(state)
                       Follower(f) with(state, :role Follower{:knownLeader :from(req)})
                       stepDown(state, :term(req), :from(req))]
      _handleAppendRequestInTerm(asFollower, req)
```

### Commentary

The clean-room implementation splits this into two functions: `handleAppendRequest` handles term-stepping, then `_handleAppendRequestInTerm` handles the log consistency check and entry application.

This differs from the Allium spec's structure (one rule, nested `if`/`else`) but the same decisions are being made.
The `cond:` flattens the three top-level cases (stale term, newer term, current term) to the same visual level.
The destructuring in `_handleAppendRequestInTerm(state, {from, term, prevLogIndex, prevLogTerm, entries, leaderCommit})` unpacks the request record directly in the parameter list.

The helper extraction (`_logConsistent`, `_appendEntries`, `_applyCommitted`, `stepDown`) keeps each function focused but means the logic is spread across more definitions than the single Allium rule.
Whether that's a win depends on the reader — for verifying against the spec, one function would be closer; for understanding and maintaining the code, the extracted helpers are clearer.

## The Proc Model: Behaviour as Data

### Allium

```allium
rule FollowerStartsElection {
    when: follower: Follower.election_deadline <= now
    ...
}

rule HeartbeatTimeout {
    when: leader: Leader.last_append_at + heartbeat_interval <= now
    ...
}
```

Allium's rules are standing declarations — "when this condition holds, this happens."
No loop, no re-arming.

### Bridje

```bridje
def: serverProc(rpcCh)
  fn: [state]
    let: [rpc proc/Recv(rpcCh, handleRpc)
          electionTimeout proc/Timeout(t/dur("PT0.15S"), onElectionTimeout)]
      case: :role(state)
        Leader(l) [rpc, proc/Timeout(t/dur("PT0.05S"), onHeartbeatTimeout)]
        Candidate(c) [rpc, electionTimeout]
        Follower(f) [rpc, electionTimeout]
```

### Commentary

The select function is `State -> [Select(State)]` — given the current state, declare what to wait for and what to do when each event arrives.
The runtime owns the loop.

The select function is re-evaluated after each handler, so state changes naturally change behaviour.
When `_initLeaderState` sets the role to Leader, the next select evaluation returns a heartbeat timeout instead of an election timeout.

This is the closest Bridje gets to Allium's declarative rules.
Allium declares conditions (`election_deadline <= now`), Bridje declares timeouts (`proc/Timeout(t/dur("PT0.15S"), ...)`).
Both express "if nothing happens within this window, do this" — the framing differs but the information content is the same.

Crucially, the select function is data — inspectable, testable, and the foundation for deterministic simulation testing.
Specification languages like TLA+ can also be simulation tested (via TLC), but the tested artifact is the spec, not the production code.
Bridje's advantage is that the same code is both the tested artifact and the production implementation.

## Structural Comparison

### What Allium provides that Bridje doesn't

| Allium concept | Purpose | Bridje equivalent |
|---|---|---|
| `when:`/`requires:` | Preconditions, triggers | `cond:` guards, `case:` pattern matching |
| `ensures:` | Postconditions | No direct equivalent |
| `invariant:` | Safety properties as first-class declarations | Property tests (separate from implementation) |
| `surface` | API boundaries with caller/context | `trait` + `defx` |
| `deferred` | Explicit "not yet specified" markers | Comments or TODOs |
| `open_question` | Tracked unknowns | Comments or issues |
| `external entity` | Boundary with unspecified systems | `trait` with no default impl |

### What Bridje provides that Allium doesn't

Allium's non-executability is a deliberate design choice, not a limitation — it forces you to think about the *what* before the *how*, which is valuable in its own right.
But for a language aiming to be a single artifact (spec + implementation + test subject), these are the additions:

| Bridje concept | Purpose |
|---|---|
| Execution | It runs — the spec is the implementation |
| Static type checking | Compiler enforces the domain model |
| `defx`/`withFx` | Substitutable effects for testing |
| `proc/Recv`/`proc/Timeout` | Declarative select-function proc model |
| Simulation testing | Same code drives production and deterministic simulation |
| Destructuring | `ServerState({cluster})` in function params |

## How Close Is Bridje to Being the Spec?

### Where it works

1. **Type definitions are near 1:1.**
   A reviewer could compare the two data models side by side without difficulty.

2. **Handler logic maps to rules.**
   `_startElection` maps to `FollowerStartsElection`. `handleVoteRequest` maps to `HandleVoteRequest`. The function names are the rule names. The logic inside them makes the same decisions in the same order.

3. **Effects read like spec actions.**
   `:sendVoteRequest(net, peer, VoteRequest{...})` reads close to `VoteRequest.created(from: ..., to: ...)`. The `net.` prefix is a small tax for substitutability.

4. **The proc model makes behaviour declarative.**
   The select function declares what the process is willing to do in each state — data, not imperative control flow.

### Where it falls short

1. **State threading.**
   `with(state, :currentTerm newTerm)` vs `follower.current_term = follower.current_term + 1`. The Allium version is shorter and more direct. The Bridje version is the price of immutability and executability. It's predictable noise — you learn to read past it — but it's there.

2. **Helper extraction fragments the rules.**
   The spec has one `HandleAppendRequest` rule. Bridje has `handleAppendRequest`, `_handleAppendRequestInTerm`, `_logConsistent`, `_appendEntries`, `stepDown`. The factoring serves implementation (DRY, testability) but makes it harder to verify against the spec as a single unit.

3. **Postconditions and invariants live in tests, not inline.**
   Allium's `ensures:` and `invariant:` are first-class declarations.
   Bridje could support inline `assert:` — but a clean-room experiment found that for pure state machine code, inline assertions add negligible value.
   Every handler is a pure function where the code *is* the postcondition — asserting what the code visibly does is redundant.
   The genuinely valuable invariants (Election Safety, Log Matching, Leader Completeness) are cross-server properties that no single server can check from its own state.
   These belong in the simulation test harness, which has a god's-eye view of the whole cluster.

4. **Timeout framing differs.**
   Allium: `election_deadline <= now` (a condition that becomes true). Bridje: `proc/Timeout(t/dur("PT0.15S"), ...)` (an explicit wait). Same information, different framing.

### The honest assessment

Bridje gets close enough that a single artifact can serve as both spec and implementation for the essential domain logic.
The incidental noise (state threading, effect prefixes) is mechanical and predictable — it doesn't obscure the essential logic.

The gaps that remain are structural: rule boundaries and deferred/open-question markers are first-class in a spec language but implicit in an implementation language.
The postconditions gap is smaller than it first appears — for pure state machines, the code is the postcondition, and the important invariants are cross-server properties that belong in simulation tests rather than inline assertions.

## Deterministic Simulation Testing

Specification languages can be simulation tested — TLA+/TLC does exactly this via model checking.
The difference is that the tested artifact is the spec, not the production code.
If the spec passes but the implementation diverges, you still have bugs.
Bridje's proc model means the same code that runs in production is the code that gets simulated.

Because the proc model is `State -> [Select(State)]` — data in, data out — a test harness can drive the select function directly.
The harness controls which branch fires, when timeouts elapse, and what messages arrive.
Same code, no mocks, fully deterministic.

### How it works

For each proc in the simulation, the harness calls the select function with the proc's current state.
This returns a list of select branches — a data description of what the proc is waiting for.

The harness has a global view: every proc's pending select, every in-flight message, every pending timeout.
It uses a seeded random to choose what happens next — deliver a message, fire a timeout, drop a message (simulating network failure), reorder deliveries.
Whatever it chooses, it calls the corresponding handler, gets new state, and checks invariants.

The seed determines the entire execution.
Same seed, same outcome.
When an invariant fails, the seed is reported — replay to reproduce exactly.

### What this catches

- **Election safety**: two leaders in the same term (scheduling-dependent race)
- **Log divergence**: different servers applying different entries at the same index
- **Liveness**: the cluster gets stuck and never elects a leader (timeout ordering)
- **Stale message handling**: a delayed VoteResponse arrives after a term change

These are exactly the bugs that are hard to find with unit tests and hard to reproduce in integration tests.

### The key insight

The same `serverProc` function that runs in production drives the simulation.
No separate model, no test-specific version, no mocks of the proc machinery.
The effect interfaces (`net`, `sm`) are the only substitution point, and they're already designed for this.

## Validation: Clean-Room Test

The Bridje implementation shown in this document (`raft-clean.brj`) was produced by an LLM given:
- The language reference docs (`notes/TYPES.md`, `notes/SYNTAX.md`)
- The proc model description (issue #37)
- A brief stdlib summary
- Knowledge of the Raft protocol

It had no access to the Allium spec, the original hand-written Bridje implementation, or the ADR.

The result:
- Structurally matches the hand-written version (types -> helpers -> handlers -> lifecycle)
- Expresses the same essential domain logic with minimal incidental noise
- Uses effects for I/O, pure functions for protocol logic, the select function for concurrency
- Maps recognisably to the Allium spec rule-by-rule

A subsequent code review (also by an LLM reading only the language docs) caught one Raft correctness bug (leader's own `matchIndex` not initialised), dead code, and minor syntax inconsistencies — but no fundamental misunderstandings of either the language or the protocol.

This validates the approach: the language reference docs are sufficient for an LLM to produce idiomatic, spec-like Bridje for a non-trivial protocol.
