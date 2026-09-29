---
title: Standard library
description: The namespaces that ship with Bridje, beyond brj.core.
---

Each of these is required by name — for example, `require: brj: as(concurrent, c)`.
[`brj.core`](/reference/core/) is available everywhere without one.

## brj.concurrent

Structured concurrency, on virtual threads.

`spawn(f)`
: Starts a task running the zero-argument function `f`, and returns its future: `c/spawn(#0: fetch(url))`.

`await(future)`
: Waits for a task, returning its result, or throwing what it threw.

`interrupt(future)`
: Cancels a task, and the tasks it started.

`ensureActive()`
: Throws `Interrupted` if the current task has been interrupted.

`sleepMs(ms)`
: Sleeps for `ms` milliseconds; throws `Interrupted` if interrupted meanwhile.

Effects bound with `withFx:` apply within the tasks spawned inside it.

## brj.time

`now()`
: The current instant, a `java.time.Instant`.

`inst(s)`
: Parses an ISO-8601 instant: `inst("2026-01-01T00:00:00Z")`.

`dur(s)`
: Parses an ISO-8601 duration: `dur("PT30S")`.

`durMs(ms)`, `durSec(s)`
: A `java.time.Duration` of that many milliseconds, or seconds.

## brj.fs

`file(path)`
: A `File` for `path`.

`exists(f)`, `isFile(f)`, `isDir(f)`
: Whether `f` exists; is a regular file; is a directory.

`readString(f)`
: `f`'s contents, as a `Str`.

`fromBytes(f)`
: `f`'s contents, as `Bytes`.

`list(f)`
: The entries of the directory `f`, as `File`s.

`resolve(f, name)`
: The `File` for `name` within `f`.

`name(f)`, `path(f)`
: `f`'s name; its full path.

## brj.bytes

`fromStr(s)`
: `s` encoded as UTF-8.

`count(bs)`
: The number of bytes.

`nth(bs, i)`
: The byte at `i`, as an `Int` from 0 to 255.

## brj.str

`fromBytes(bs)`
: `bs` decoded as UTF-8.

## brj.rdr

The reader's forms, as values. See [macros and quoting](/reference/macros/#forms).

`fromStr(s)`
: The forms read from `s`, as a `[Form]`.

`fromFile(f)`
: The forms read from the file `f`.

`SymbolForm`, `QSymbolForm`, `DotSymbolForm`, `QDotSymbolForm`, `List`, `Vector`, `Set`, `Record`, `Int`, `Double`, `String`, `BigInt`, `BigDec`
: The variants of the `Form` enum, with their keys `.sym`, `.ns`, `.member`, `.els` and `.value`.

`.loc`
: A form's source location, in its metadata, with `.source`, `.path`, `.startLine`, `.startColumn`, `.endLine` and `.endColumn`.

## brj.test

Tests, run by Gradle's `test` task through Bridje's JUnit engine.

`.test`
: Marks a zero-argument function as a test: `^t/.test def: arithmetic() …`.

`is(expr)`
: Records a failure, with the form of `expr`, if `expr` is false.
  A test passes if it records no failures and doesn't throw.

`runTest(qualifiedName)`
: Runs one test, returning a `TestResult{.failures, .exn}`, where each failure is a `Failure{.form, .message}`.
