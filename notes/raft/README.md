# Allium and Bridje: Raft Side-by-Side


Allium and Bridje approach the same problem from opposite directions.
Allium takes informal specs and gives them formal structure.
Bridje takes executable code and tries to make it read like a spec.

This document puts them side by side on the same protocol (Raft consensus) to see how close Bridje gets to reading like a spec, and where the gaps remain.

See raft.allium and raft.brj for the full versions.

---

**TL;DR:** Bridje gets surprisingly close to spec-like for executable code.
Domain logic, effects, and types all read well.
The main gaps are postconditions (no `ensures:` equivalent) and first-class invariants.
Preconditions are well served by `cond:` and `case:`.
The proc model with select functions brings Bridje closer to the declarative style of the spec — timeouts and behaviour are declared, not imperatively managed.
The real opportunity is bridging the two — generating test properties from Allium invariants to verify Bridje implementations.

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
decl: {.term Int, .index Int, .command Str}
tag: LogEntry({.term, .index, .command})

decl:
  {.cluster Set
   .currentTerm Int
   .votedFor ServerId
   .log Vec
   .commitIndex Int
   .lastApplied Int
   .role ServerRole
   .id ServerId}

tag: ServerState({.id, .cluster, .currentTerm, .votedFor, .log, .commitIndex, .lastApplied, .role})

type: ServerRole
  Sum:
    Follower({.knownLeader})
    Candidate({.votesReceived})
    Leader({.nextIndex, .matchIdx})

def: lastLogIndex(ServerState({log}))
  count(log)

def: lastLogTerm(ServerState({log}))
  if: pos?(count(log))
    .term(last(log))
    0
```

### Commentary

The `decl:`/`tag:` separation is a Clojure-ism that pays for itself when keys are reused — `.from`, `.to`, `.term` appear in four message types and are declared once.
The batch `decl:` form with `.` sigil on members keeps this compact — for a single type in isolation it's comparable ceremony to Allium's inline declarations.

The `Sum:` block is compact and closed, which is good for exhaustive pattern matching.
Derived fields (`lastLogIndex`, `lastLogTerm`) become standalone functions with destructuring, which is clean but means they live separately from the type definition.

**How close to the spec?**
Close enough.
The type declarations are slightly noisier but carry the same information.
A reviewer could compare the two data models without difficulty.

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

        follower.election_deadline = now + random(election_timeout_min, election_timeout_max)
}
```

### Bridje

```bridje
// The handler — pure state transform + effects
def: startElection(state)
  let: [state
        with(state,
          .currentTerm inc(.currentTerm(state)),
          .role Candidate{.votesReceived #{.id(state)}},
          .votedFor .id(state))]
    doseq: [peer .cluster(state)]
      when: neq(peer, .id(state))
        .sendVoteRequest(net, peer,
          VoteRequest:
            {.from .id(state)
             .to peer
             .term .currentTerm(state)
             .lastLogIndex lastLogIndex(state)
             .lastLogTerm lastLogTerm(state)})
    state

// The select function — declares when startElection fires
def: serverProc(rpcCh)
  fn: [state]
    let: [rpc proc/Recv(rpcCh, handleRpc)
          el proc/Timeout(#dur("PT0.15S"), startElection)]
      case: .role(state)
        Leader(_) [rpc, proc/Timeout(#dur("PT0.02S"), sendHeartbeats)]
        Candidate(_) [rpc, el]
        Follower(_) [rpc, el]
```

### Commentary

The handler is a pure function: takes state, returns new state, calls effects.
No agent ref, no timeout re-arming, no concurrency plumbing.
The `with` updates read close to the spec's declarative assignments — `with(state, .currentTerm inc(.currentTerm(state)))` vs `follower.current_term = follower.current_term + 1`.

The election timeout is expressed in the select function, not in the handler.
`proc/Timeout(#dur("PT0.15S"), startElection)` says "if nothing happens for 150ms, call startElection" — the same concept as the spec's `election_deadline <= now`, but declared as a select branch rather than a temporal condition.
The select function is re-evaluated after each handler, so when the role changes to Leader, the timeout naturally switches to heartbeat interval.

**Where mechanism leaks in:**
The effect indirection (`.sendVoteRequest(net, ...)` rather than just `sendVoteRequest(...)`) is a small tax for substitutability.
Worth it for testing, but it does add a layer that the spec doesn't have.

**How close to the spec?**
Very close.
The core logic (increment term, become candidate, broadcast vote requests) is all there and readable.
The timeout, previously the most visible mechanism, is now a declarative select branch — closer to the spec's approach than the old imperative style.

## A Complex Rule: HandleAppendRequest

### Allium

```allium
rule HandleAppendRequest {
    when: request: AppendRequest.created

    let receiver = request.to

    let log_consistent =
        request.prev_log_index = 0
        or (receiver.log.count >= request.prev_log_index
            and receiver.log.at(request.prev_log_index).term = request.prev_log_term)

    ensures:
        if request.term >= receiver.current_term:
            if request.term > receiver.current_term:
                receiver.current_term = request.term
                receiver.voted_for = null

            receiver.role = Follower
            receiver.known_leader = request.from
            receiver.election_deadline = now + random(...)

            if log_consistent:
                receiver.log = receiver.log.take(...).append(request.entries)
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
def: handleAppendRequest(state, req)
  cond:
    lt(.term(req), .currentTerm(state))
      do:
        .sendAppendResponse(net, .from(req),
          AppendResponse:
            {.from .id(state)
             .to .from(req)
             .term .currentTerm(state)
             .success false
             .matchIndex 0})
        state

    not(logConsistent(state, req))
      let: [state ->: state
              stepUpTerm(.term(req))
              with(.role Follower{.knownLeader .from(req)})]
        .sendAppendResponse(net, .from(req),
          AppendResponse:
            {.from .id(state)
             .to .from(req)
             .term .currentTerm(state)
             .success false
             .matchIndex 0})
        state

    do:
      let: [state ->: state
              stepUpTerm(.term(req))
              with(.role Follower{.knownLeader .from(req)},
                   .log ->: .log(state) take(.prevLogIndex(req)) append(.entries(req)))
            state if: gt(.leaderCommit(req), .commitIndex(state))
                    with(state, .commitIndex min(.leaderCommit(req), count(.log(state))))
                    state]
        .sendAppendResponse(net, .from(req),
          AppendResponse:
            {.from .id(state)
             .to .from(req)
             .term .currentTerm(state)
             .success true
             .matchIndex add(.prevLogIndex(req), count(.entries(req)))})
        state
```

### Commentary

**What works well:**
`cond:` flattens the three cases (stale term, inconsistent log, success) to the same visual level.
`logConsistent` and `stepUpTerm` as extracted helpers keep the main function focused.
The immutable state threading with `->: state stepUpTerm(.term(req)) with(.role ...)` chains transforms fluently.

**What doesn't work as well:**
The `stepUpTerm` + `with(.role ...)` sequence is duplicated across the two non-stale branches.
The spec avoids this because its nesting puts "recognise the leader" outside the log consistency check.
Bridje's `cond:` trades that structural fidelity for flatness — whether that's a net win depends on the reader.

**How close to the spec?**
The branching logic is all present and the cases are clear.
The structure differs — flat `cond:` vs nested `if`/`else` — but the same decisions are being made.
This is probably the function where Bridje diverges most from the spec's structure, and it's still followable.

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
The runtime figures out when to check.

### Bridje

```bridje
def: serverProc(rpcCh)
  fn: [state]
    let: [rpc proc/Recv(rpcCh, handleRpc)
          el proc/Timeout(#dur("PT0.15S"), startElection)]
      case: .role(state)
        Leader(_) [rpc, proc/Timeout(#dur("PT0.02S"), sendHeartbeats)]
        Candidate(_) [rpc, el]
        Follower(_) [rpc, el]
```

### Commentary

The proc model is `State -> Select(State)` — a function that, given the current state, declares what to wait for and which handler to call for each branch.
This is CSP with explicit state.
The runtime owns the loop.

The select function is re-evaluated after each handler, so state changes naturally change the behaviour.
When `winElection` sets the role to Leader, the next select evaluation returns a heartbeat timeout instead of an election timeout.
No manual re-arming needed.

This is the closest Bridje gets to Allium's declarative rules.
The select function is a data description of "what this process is willing to do next" — inspectable, testable, and the foundation for deterministic simulation testing.

**How close to the spec?**
The proc model bridges most of the gap between imperative code and declarative rules.
The remaining difference is framing: Allium declares conditions (`election_deadline <= now`), Bridje declares timeouts (`proc/Timeout(#dur("PT0.15S"), ...)`).
Both express "if nothing happens within this window, do this."

## Structural Differences

### What Allium provides that Bridje doesn't

| Allium concept | Purpose | Bridje equivalent |
|---|---|---|
| `when:`/`requires:` | Preconditions, triggers | `cond:` guards, `case:` pattern matching |
| `ensures:` | Postconditions | No direct equivalent (would need `assert`) |
| `invariant:` | Safety properties as first-class declarations | Tests, property checks |
| `surface` | API boundaries with caller/context | `trait` + `defx` |
| `deferred` | Explicit "not yet specified" markers | Comments or TODOs |
| `open_question` | Tracked unknowns | Comments or issues |
| `external entity` | Boundary with unspecified systems | `trait` with no default impl |

### What Bridje provides that Allium doesn't

| Bridje concept | Purpose |
|---|---|
| `defx`/`withFx` | Substitutable effect implementations for testing |
| `proc/Recv`/`proc/Timeout` | Declarative select-function proc model |
| Destructuring | `ServerState({cluster})` in function params |
| `cond:`/`case:` | Pattern matching and multi-way branching |
| `cond->` | Conditional state transforms (like Clojure's `cond->`) |
| `with` (variadic) | Immutable state updates, like Clojure's `assoc` |
| Reusable helpers | `stepUpTerm`, `logConsistent` as chainable transforms |

## Critique: How Spec-Like Is the Bridje?

### What Bridje does well

1. **The domain logic reads clearly.**
Function names (`startElection`, `handleVoteRequest`, `advanceCommitIndex`) map directly to protocol concepts.
Immutable state updates (`with(state, .currentTerm ...)`) read close to the spec's declarative assignments.
A reader familiar with Raft could follow the code without Bridje experience.

2. **Effects separate concerns cleanly.**
`.sendVoteRequest(net, ...)` makes it clear this is an external interaction without burying the call in infrastructure.
The effect system achieves what the spec takes for granted — that "send a message" is a simple operation — while keeping it substitutable.

3. **Types carry protocol structure.**
`ServerRole` as a sum type means the compiler enforces that you handle all roles.
Tagged constructors (`VoteRequest: {...}`) make message creation visually match the spec's `VoteRequest.created(...)`.

4. **Shared keys reduce cross-type noise.**
Declaring `.from`, `.to`, `.term` once and reusing them across message types is genuinely less repetitive than redeclaring them per entity.

5. **The proc model makes behaviour declarative.**
The select function declares what the process is willing to do in each state.
Timeouts and channel handlers are data, not imperative callbacks.
This is the single biggest improvement over the earlier agent+onTimeout model — the concurrency plumbing is gone from the essential layer.

### Where Bridje falls short of spec-like

1. **No postconditions.**
Preconditions are well served by `cond:` guards and `case:` pattern matching — `case: .role(state) Leader(l)` is an explicit "this requires the server to be a leader" check, enforced by the compiler's exhaustiveness checking.
Postconditions are harder. There's no `ensures:` equivalent — you'd need something like `assert` to express "after this function, commitIndex >= old commitIndex."
The spec's postcondition framing is genuinely something Bridje lacks.

2. **Rule decomposition doesn't come naturally.**
The spec splits vote handling into `HandleVoteGranted` and `StepDownOnVoteResponse` — separate, focused rules.
Bridje merges them into one function with nested branching because that's what functions do.
You *could* split them, but the language doesn't encourage it the way Allium's `rule` blocks do.

3. **No first-class invariants.**
"At most one leader per term" is a property test in Bridje, not a declaration next to the protocol.
The invariant exists, but it lives in a test file, not alongside the code it constrains.

4. **Timeout framing differs.**
Allium expresses timeouts as state conditions (`election_deadline <= now`).
Bridje expresses them as select branches (`proc/Timeout(duration, handler)`).
Both work, but the spec's version is more declarative — it doesn't say *when* to check, just *what must hold*.

### The honest summary

Bridje gets surprisingly close to spec-like for an executable language.
The proc model closed the biggest gap — concurrency plumbing is no longer visible in the essential layer.
Immutable state with `with` reads closer to declarative assignment than `set!` did.

The remaining gaps are *structural*: specs have postconditions, invariants, and rule boundaries as first-class concepts.
Bridje has functions and select branches.
Functions are powerful and general, but they don't carve the protocol at the same joints.

## Deterministic Simulation Testing

This is where Bridje goes beyond what Allium can offer.

Because the proc model is `State -> Select(State)` — data in, data out — a test harness can drive the select function directly.
The harness controls which branch fires, when timeouts elapse, and what messages arrive.
Same code, no mocks, fully deterministic.

### What the user provides

A simulation test needs three things:

1. **The proc function** — already written as `serverProc`. No changes needed.
2. **Effect implementations** — test versions of `net` and `sm` that record calls instead of performing I/O. The network effect captures outgoing messages so the harness can deliver them to other servers.
3. **Invariants** — properties that must hold after every state transition. For Raft: at most one leader per term, logs match at committed indices, committed entries are never lost.

### How the harness works

The harness doesn't run the proc's real loop.
Instead, it calls the select function directly.

For each proc in the simulation, the harness calls the select function with the proc's current state.
This returns a `Select(State)` — a data description of what the proc is waiting for: which channels, what timeout duration, which handler for each.

The harness now has a global view: every proc's pending select, every in-flight message, every pending timeout.
It uses a seeded random to choose what happens next — deliver a message to a channel, fire a timeout, drop a message (simulating network failure), reorder deliveries.
Whatever it chooses, it calls the corresponding handler, gets new state, and checks invariants.

Because the select is data, the harness can inspect it without running it.
Because the handlers are pure functions (state in, state out, effects recorded), there's no hidden state, no thread timing, no races.
The seed determines the entire execution — same seed, same sequence of choices, same outcome.

Each iteration uses a different seed, exploring different scheduling paths.
When an invariant fails, the seed is reported — replay with that seed to reproduce exactly.
Then attach a debugger or add logging to that specific run.

The key insight: **the same `serverProc` function that runs in production drives the simulation**.
No separate model, no test-specific version, no mocks of the proc machinery.
The effect interfaces (`net`, `sm`) are the only substitution point, and they're already designed for this.

### What this catches

- **Election safety**: two leaders in the same term (scheduling-dependent race)
- **Log divergence**: different servers applying different entries at the same index
- **Liveness**: the cluster gets stuck and never elects a leader (timeout ordering issue)
- **Stale message handling**: a delayed VoteResponse arrives after a term change

These are exactly the bugs that are hard to find with unit tests and hard to reproduce in integration tests.
Simulation testing explores thousands of scheduling paths systematically.

### The advantage over Allium

Allium can declare invariants and generate test skeletons, but it can't *run* the protocol.
It specifies what should be true, not whether it's true for a given implementation.

Bridje's proc model is both the spec (the select function declares behaviour) and the implementation (the handlers execute it).
Simulation testing verifies the implementation against invariants across thousands of concurrent execution paths — something a specification language fundamentally cannot do.

The two remain complementary: Allium for capturing intent and surfacing ambiguities before implementation, Bridje for executable verification that the implementation holds up under adversarial scheduling.

## The Gap Between Them

The most interesting observation is what happens *between* the two artifacts.

Right now, the Allium spec and the Bridje implementation are independent documents.
A reader can compare them manually, but there's no formal connection — no way to check that the Bridje code satisfies Allium's `ensures:` clauses, or that Allium's invariants hold over the Bridje state transitions.

Potential bridges:
- Generating property-test skeletons from Allium invariants for Bridje to fill in
- Checking that Bridje's state transition functions preserve Allium's `ensures:` postconditions
- Using Allium's `surface` declarations to verify that Bridje's `trait` + `defx` cover the same API boundary
- Generating simulation test invariants from Allium's `invariant:` declarations

The value of having both isn't that one replaces the other — it's that they could check each other.
