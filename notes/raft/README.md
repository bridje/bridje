# Allium and Bridje: Raft Side-by-Side


Allium and Bridje approach the same problem from opposite directions.
Allium takes informal specs and gives them formal structure.
Bridje takes executable code and tries to make it read like a spec.

This document puts them side by side on the same protocol (Raft consensus) to see how close Bridje gets to reading like a spec, and where the gaps remain.

See raft.allium and raft.brj for the full versions.

---

**TL;DR:** Bridje gets surprisingly close to spec-like for executable code.
Domain logic, effects, and types all read well.
The main gaps are postconditions (no `ensures:` equivalent) and visible agent lifecycle plumbing.
Preconditions are well served by `cond:` and `case:`.
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
defkey: term Int
defkey: index Int
defkey: command Str
type: LogEntry {term, index, command}

defkey: cluster Set
defkey: currentTerm Int
defkey: votedFor ServerId
defkey: log Vec
defkey: commitIndex Int
defkey: lastApplied Int
defkey: role ServerRole
defkey: id ServerId
type: ServerState {id, cluster, currentTerm, votedFor, log, commitIndex, lastApplied, role}

type: ServerRole
  Sum:
    Follower(knownLeader)
    Candidate(votesReceived)
    Leader(nextIndex, matchIdx)

def: lastLogIndex(ServerState({log}))
  log.count

def: lastLogTerm(ServerState({log}))
  if: log.count.gt(0)
    log.last.term
    0
```

### Commentary

The `defkey`/`type` separation is a Clojure-ism that pays for itself when keys are reused — `from`, `to`, `term` appear in four message types and are declared once.
For a single type in isolation it's more ceremony than Allium's inline declarations.

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
def: startElection(state, server)
  state.setCurrentTerm(state.currentTerm.inc())
  state.setRole(Candidate({votesReceived #{state.id}}))
  state.setVotedFor(state.id)

  doseq: [peer state.cluster]
    when: peer.neq(state.id)
      net.sendVoteRequest(peer,
        VoteRequest:
          {from state.id,
           to peer,
           term state.currentTerm,
           lastLogIndex lastLogIndex(state),
           lastLogTerm lastLogTerm(state)})

  server.onTimeout(clock.electionTimeout(), startElection, server)
```

### Commentary

The state transitions read clearly — `setCurrentTerm`, `setRole`, `setVotedFor` are self-explanatory.
The `doseq:` loop with `when:` guard maps naturally to "for each peer that isn't us, send a vote request."
The `VoteRequest:` constructor with named fields is easy to compare against the spec.

**Where mechanism leaks in:**
The effect indirection (`net.sendVoteRequest` rather than just `sendVoteRequest`) is a small tax for substitutability.
Worth it for testing, but it does add a layer that the spec doesn't have.

Note that `server.onTimeout(clock.electionTimeout(), startElection, server)` is *not* implementation detail — the election timeout is core Raft business logic.
The spec expresses it as a deadline (`election_deadline <= now`); Bridje expresses it as a callback registration.
Different framing, same protocol concept.

**How close to the spec?**
Close.
The core logic (increment term, become candidate, broadcast vote requests, set election timeout) is all there and readable.

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
def: handleAppendRequest(state, req, server)
  let: [reject (fn: []
                  net.sendAppendResponse(req.from,
                    AppendResponse:
                      {from state.id, to req.from,
                       term state.currentTerm,
                       success false, matchIndex 0}))]
    cond:
      req.term.lt(state.currentTerm) reject()

      not(logConsistent(state, req))
        do:
          stepUpTerm(state, req.term)
          state.setRole(Follower({knownLeader req.from}))
          server.onTimeout(clock.electionTimeout(), startElection, server)
          reject()

      do:
        stepUpTerm(state, req.term)
        state.setRole(Follower({knownLeader req.from}))
        server.onTimeout(clock.electionTimeout(), startElection, server)
        state.setLog(state.log.take(req.prevLogIndex).append(req.entries))

        when: req.leaderCommit.gt(state.commitIndex)
          state.setCommitIndex(min(req.leaderCommit, state.log.count))

        net.sendAppendResponse(req.from,
          AppendResponse:
            {from state.id, to req.from,
             term state.currentTerm, success true,
             matchIndex req.prevLogIndex.add(req.entries.count)})
```

### Commentary

**What works well:**
`cond:` flattens the three cases (stale term, inconsistent log, success) to the same visual level.
The `reject` thunk is a nice idiom — name the failure path once, call it twice.
`logConsistent` and `stepUpTerm` as extracted helpers keep the main function focused.

**What doesn't work as well:**
The `stepUpTerm` + `setRole` + `onTimeout` sequence is duplicated across the two non-stale branches.
The spec avoids this because its nesting puts "recognise the leader" outside the log consistency check.
Bridje's `cond:` trades that structural fidelity for flatness — whether that's a net win depends on the reader.

The `reject` thunk, while DRY, is an abstraction the spec doesn't have.
A reviewer comparing against the spec has to mentally inline it.

**How close to the spec?**
The branching logic is all present and the cases are clear.
The structure differs — flat `cond:` vs nested `if`/`else` — but the same decisions are being made.
This is probably the function where Bridje diverges most from the spec's structure, and it's still followable.

## Structural Differences

### What Allium provides that Bridje doesn't

| Allium concept | Purpose | Bridje equivalent |
|---|---|---|
| `when:`/`requires:` | Preconditions, triggers | `cond:` guards, `case:` pattern matching |
| `ensures:` | Postconditions | No direct equivalent (would need `assert`) |
| `invariant:` | Safety properties as first-class declarations | Tests, property checks |
| `surface` | API boundaries with caller/context | `definterface` + `defx` |
| `deferred` | Explicit "not yet specified" markers | Comments or TODOs |
| `open_question` | Tracked unknowns | Comments or issues |
| `external entity` | Boundary with unspecified systems | `definterface` with no default impl |

### What Bridje provides that Allium doesn't

| Bridje concept | Purpose |
|---|---|
| `defx`/`withFx` | Substitutable effect implementations for testing |
| `agent`/`onTimeout` | Concrete concurrency model |
| Destructuring | `ServerState({cluster})` in function params |
| `cond:`/`case:` | Pattern matching and multi-way branching |
| Capability system | `ref`/`val`/`tag`/`iso` compile-time safety |
| Reusable helpers | `stepUpTerm`, `logConsistent`, `reject` thunk |

## Critique: How Spec-Like Is the Bridje?

### What Bridje does well

1. **The domain logic reads clearly.**
Function names (`startElection`, `handleVoteRequest`, `advanceCommitIndex`) map directly to protocol concepts.
State transitions (`setCurrentTerm`, `setRole`) are self-documenting.
A reader familiar with Raft could follow the code without Bridje experience.

2. **Effects separate concerns cleanly.**
`net.sendVoteRequest(...)` makes it clear this is an external interaction without burying the call in infrastructure.
The effect system achieves what the spec takes for granted — that "send a message" is a simple operation — while keeping it substitutable.

3. **Types carry protocol structure.**
`ServerRole` as a sum type means the compiler enforces that you handle all roles.
Tagged constructors (`VoteRequest: {...}`) make message creation visually match the spec's `VoteRequest.created(...)`.

4. **Shared keys reduce cross-type noise.**
Declaring `from`, `to`, `term` once and reusing them across message types is genuinely less repetitive than redeclaring them per entity.

### Where Bridje falls short of spec-like

1. **No postconditions.**
Preconditions are well served by `cond:` guards and `case:` pattern matching — `case: state.role Leader(l):` is an explicit "this requires the server to be a leader" check, enforced by the compiler's exhaustiveness checking.
Postconditions are harder. There's no `ensures:` equivalent — you'd need something like `assert` to express "after this function, commitIndex >= old commitIndex."
The spec's postcondition framing is genuinely something Bridje lacks.

2. **Some mechanism is visible.**
`agent(state)` and `server.send(...)` are concurrency plumbing that a spec would omit.
The timeout and effect calls are protocol logic expressed concretely, which is fine — but the agent lifecycle is genuinely incidental.

3. **Imperative mutation is noisier than declarative assignment.**
`state.setCommitIndex(newCommit)` vs `server.commit_index = new_commit`.
The setter convention works, but it's a step removed from "this field now has this value."
It reads as an instruction rather than a fact.

4. **Rule decomposition doesn't come naturally.**
The spec splits vote handling into `HandleVoteGranted` and `StepDownOnVoteResponse` — separate, focused rules.
Bridje merges them into one function with nested branching because that's what functions do.
You *could* split them, but the language doesn't encourage it the way Allium's `rule` blocks do.

5. **No first-class invariants.**
"At most one leader per term" is a property test in Bridje, not a declaration next to the protocol.
The invariant exists, but it lives in a test file, not alongside the code it constrains.

### The honest summary

Bridje gets surprisingly close to spec-like for an executable language.
The main gap isn't readability — the code reads well.
The gap is *structural*: specs have preconditions, postconditions, invariants, and rule boundaries as first-class concepts.
Bridje has functions.
Functions are powerful and general, but they don't carve the protocol at the same joints.

## The Gap Between Them

The most interesting observation is what happens *between* the two artifacts.

Right now, the Allium spec and the Bridje implementation are independent documents.
A reader can compare them manually, but there's no formal connection — no way to check that the Bridje code satisfies Allium's `ensures:` clauses, or that Allium's invariants hold over the Bridje state transitions.

Potential bridges:
- Generating property-test skeletons from Allium invariants for Bridje to fill in
- Checking that Bridje's state transition functions preserve Allium's `ensures:` postconditions
- Using Allium's `surface` declarations to verify that Bridje's `definterface` + `defx` cover the same API boundary

The value of having both isn't that one replaces the other — it's that they could check each other.
