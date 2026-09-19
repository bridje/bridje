---
name: bridje-testing
description: How tests are run in the Bridje repo — you run the Gradle task and redirect its output, the gradle-test-results agent reads the log, plus the mid-run edit freeze, the test tasks and filters, and what a tree-sitter grammar change obliges. Read this before running or delegating any test run in this repo.
---

# Running tests in Bridje

Read this before you run a test.

You run the test task yourself and redirect Gradle's output to a log.
`gradle-test-results` then reads that log and tells you what happened — delegation is for the reading, not the running.

This is the split the global "delegate to `gradle-tests`" rule defers to, so it is these instructions that apply here, not that one.

Interpret MUST, MUST NOT, SHOULD, SHOULD NOT, MAY per RFC 2119.

## The rules you MUST NOT get wrong

1. You MUST run the test task yourself, redirecting its output to `build/test-run.log` — never delegate the run, and never let a test run's output into your context.
2. You MUST hand that log to `gradle-test-results` rather than reading it yourself — see [Keeping the output out of your context](#keeping-the-output-out-of-your-context).
3. You MUST NOT edit files the build compiles while your run is compiling them — see [The build phase is what is frozen](#the-build-phase-is-what-is-frozen).
4. A test that fails after your change is a test *you* broke — see [When a test fails](#when-a-test-fails).
5. You MUST stop a run the moment you know you'll re-run it, rather than letting it finish — see [A run you already know you'll redo is waste](#a-run-you-already-know-youll-redo-is-waste).

The rest of this document is the mechanics behind those five.

## The loop

Two commands, from the project root:

```sh
./gradlew :language:testClasses                                        # compile — see the edit freeze below
mkdir -p build; ./gradlew test --tests 'brj.DefTest' > build/test-run.log 2>&1
```

Then hand the log to `gradle-test-results`:

> Read `/abs/path/to/tree/build/test-run.log` and the result files under `/abs/path/to/tree`, and report what happened.
> `./gradlew test --tests 'brj.DefTest'` exited N.

- **You SHOULD run the test command with `run_in_background: true`** for anything wider than a single test class, and get on with work the freeze doesn't cover while it runs.
- **You MUST NOT run more than one `./gradlew` invocation at a time in a worktree** — concurrent invocations can corrupt the build cache, particularly with Kotlin.
  Combine every class you want covered into a single invocation (`--tests 'brj.DefTest' --tests 'brj.FnTest'`) and let Gradle parallelise internally.

### Delegating the reading

`gradle-test-results` is defined in `.claude/agents/`, and holds no `./gradlew`, no `Edit` and no `Write`.

**Give it the tree as an absolute path.**
It inherits your working directory, the Task tool has no parameter that pins it, and worktrees here live under `.claude/worktrees/` — *inside* the main checkout — so an agent in the wrong tree finds a plausible set of result files rather than an error.

Whether a failure it reports is yours or a known flake is [yours to judge](#when-a-test-fails).

### Keeping the output out of your context

A bare `./gradlew test` puts megabytes of task progress, stack traces and daemon chatter into your context.

- You MUST NOT read the log of a run that reached the tests — not with `Read`, not with `cat`, `tail` or `grep`.
- **A compile, kapt or configuration failure is the exception**: nothing ran, so the log is a few hundred lines with the error in the last thirty of them.
  `tail -30 build/test-run.log` and fix it, rather than paying a delegation round-trip to be told what the compiler already said.
- One path, overwritten by each run, so there is only ever one file to name and no chance of reading the last run's.
- `build/` is gitignored, so the log never shows up in `git status`, and `clean` disposes of it.

You SHOULD run the relevant tests proactively after a code change rather than waiting to be asked.

## A comment-only change needs a compile, not a test run

"Rerun after every change, however trivial" is about changes that can alter behaviour.
A change confined to `//` comments or KDoc cannot, so a clean `./gradlew :language:testClasses` is the whole of the verification it needs.

The carve-out is narrow: **an annotation is not a comment.**
`@Test`, `@Operation`, `@Specialization`, `@ConstantOperand`, `@JvmStatic`, `@Suppress` all change what kapt, the Bytecode DSL processor or the runner does, and the first four change generated code.
Where a diff is comments *plus* code, it is a code change — run the tests.

## A run you already know you'll redo is waste

A run only ever describes the tree it compiled.
So the moment you decide on a material change — a bug you spotted reviewing the diff, a fix for a failure the run has already reported, anything at all that touches a file the build reads — that run's verdict is void, and every remaining minute of it buys nothing.

Kill it and re-run with the change in.

- You MUST NOT let a run you have already invalidated play out on the grounds that its remaining results might still be worth having.
  They are not reportable: you cannot claim a class passed in a tree you are about to change, and sorting the failures that survive your edit from the ones that don't costs more than the re-run.
- Reading the diff while a run is in flight is the right use of the wait, and finding something is the expected outcome.
  A finding is a reason to stop the run — not something to sit on until it finishes.
- The run is your own process, so stopping it is yours to do: `TaskStop` on a backgrounded run, or interrupt the foreground call.
- Stopping the run is also what lifts the edit freeze, so the order is: stop it, confirm no test worker is still up, then edit.

## The build phase is what is frozen

The freeze is scoped to the worktree the run is compiling from, and within it to **the files that run compiles** — Kotlin and Java sources, `grammar.js` and the tree-sitter C sources, build scripts, and anything on the main or test resource path, which includes `language/src/main/brj` and `language/src/test/brj`.
Recompiling under a running build produces **bogus type errors and cascading failures** that look exactly like real breakage, and chasing them costs far more than waiting did.
If you have already edited mid-compile, discard that run's results entirely and re-run once the tree is stable — do not try to reason about which failures were real.

Compilation is the part a concurrent edit corrupts, and here it is the expensive part: `:language` runs kapt over the Truffle DSL processor, which regenerates the Bytecode DSL interpreter from `BridjeRootNode` on every change to it.
Step 1 of [the loop](#the-loop) exists to get that over with while you are still waiting anyway:

1. `./gradlew :language:testClasses`.
   That is the whole compile phase behind `test`, including `:tree-sitter:buildTreeSitter` and `:copyQueries` feeding `processResources`.
   Its output is small enough to read directly, so it needs no redirect and no delegate — but the freeze applies while it is in flight.
2. The test command. Its compile tasks are up-to-date and clear in a few seconds on a warm daemon, and nothing is compiled after that.
3. Edit from that point on.

**The report describes the tree you compiled, not the tree you now have.**
Anything edited after step 1 is untested until the next run, whatever the report says, so you MUST NOT report green for a file you edited during the run that produced it.
The split lets you get on with the *next* increment while a run confirms the last one; it does not let you fix a failure and claim the same run vindicates the fix.

Everything the build never reads is outside the freeze, and you SHOULD carry on with it rather than idling: `docs/`, `README`s, `AGENTS.md`, `.claude/`, and the editor integrations under `vscode/`, `emacs/` and `nvim/`.

Runs in other worktrees do not concern you, and you MUST NOT check for them or wait on them.
Each worktree has its own `build/` and `.gradle/`, and Gradle takes cross-process locks over the shared `~/.gradle` caches, so a build elsewhere on the machine cannot corrupt yours.
Those locks block rather than fail, so a run that stalls early — typically reporting that it is waiting to acquire a lock — is that mechanism working; wait it out rather than killing the run.

## Test tasks

`:language` is the only module with a test source set, so `./gradlew test` and `./gradlew :language:test` are the same run.

- `./gradlew test` — the JUnit 5 suite.
- `./gradlew :tree-sitter:testTreeSitter` — the grammar's corpus tests, via the `tree-sitter` CLI.
- `./gradlew check` — both, since `testTreeSitter` is wired into `:tree-sitter:check`.

Each takes the same redirect: `./gradlew check > build/test-run.log 2>&1`.

### A grammar change obliges both

`grammar.js` and `tree-sitter/src/scanner.c` feed two consumers.
`testTreeSitter` checks the parse trees against `tree-sitter/test/corpus/`, and `buildTreeSitter` produces the `.so` that `:language`'s `processResources` bundles and the reader loads at runtime — so a grammar change that the corpus accepts can still break `brj.ReaderTest`.

Touching either file, you MUST run `./gradlew check`, not `./gradlew test`.

`testTreeSitter` writes no JUnit XML — it is an `Exec` wrapping the CLI, so its whole record is the log, and `gradle-test-results` knows to report it from there.

## Test filtering

- `./gradlew test --tests 'brj.DefTest'` — one class.
- `./gradlew test --tests 'brj.DefTest.def value'` — one method.
- `./gradlew test --tests 'brj.*Ns*Test'` — a wildcard over classes.
- `./gradlew test --tests 'brj.DefTest.*def*fn*'` — a wildcard over methods, which is how a backtick name with spaces is easiest reached.
- **Kotlin backtick method names carry a trailing `()` in the result XML** but not in the `--tests` pattern, so a report's `def value()` is `--tests 'brj.DefTest.def value'`.
- Re-running the *same* `--tests` invocation is cached as UP-TO-DATE and does nothing.
  Add `--rerun-tasks` whenever the point of the run is to re-execute — verifying an intermittent failure, or re-checking after a change the build didn't notice.

## When a test fails

**All tests pass on `main`. There are no pre-existing failures.**
If a test fails after your change, you broke it.
Investigate your own diff, find the bug, fix it.

- You MUST NOT speculate that a failure might be pre-existing.
- You MUST NOT stash your changes or check out `main` to "verify" that theory.
- You MUST NOT disable, skip or loosen an assertion to get to green.

The one carve-out is the global one: an open issue labelled `flaky` on `bridje/bridje` matching the failure in front of you is the only admissible evidence that it isn't yours.
Absent one, it is yours.

### A failure inside `ctx.eval` surfaces as a `PolyglotException`

`withContext` / `evalBridje` run Bridje source through the polyglot API, so an analyser or runtime error arrives at the assertion wrapped, with the guest frames under a `Caused by:`.
The `<bridje>` frames and the source locations in them are the failure; the `org.graalvm.polyglot` frames around them are how it got out.
