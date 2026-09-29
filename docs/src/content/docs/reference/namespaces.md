---
title: Namespaces
description: The ns form, require and import, qualified names, and how namespaces are found and loaded.
---

Bridje code is split into namespaces, one per file.

## Files

A namespace's name is its path on the classpath, with `.` for `/`: `todo.model` is `todo/model.brj`.
In a Gradle project, sources are under `src/main/bridje` and tests under `src/test/bridje`.

A namespace is loaded the first time it's required, and evaluated top to bottom, once.
A definition can only refer to what's defined above it.

## The ns form

`ns: name require: … import: …`
: Names the namespace, and declares what it uses. It must be the file's first form; both clauses are optional.

```bridje
ns: raft.server
  require:
    brj:
      as(concurrent, c)
      test
    raft:
      as(log, l)
  import:
    java.time:
      as(Instant, I)
      Duration
```

`require:`
: Bridje namespaces, grouped under a package prefix.
  Within a prefix, `as(name, alias)` requires `prefix.name` as `alias`; a bare `name` requires it as `name`.

`import:`
: Host classes, grouped under a package prefix, in the same way: `as(Instant, I)` imports `java.time.Instant` as `I`.
  See [host interop](/reference/interop/).

A namespace with no `ns:` form is anonymous.

## Qualified names

`alias/name`
: A var, tag, enum or effect from a required namespace: `c/spawn`, `l/Entry{…}`.

`alias/.key`
: A key from a required namespace.

`full.ns.name/name`
: A var from a namespace by its full name, if it's already loaded.

`Alias/member`, `Alias/.member`
: A static or instance member of an imported class.

Unqualified names resolve to, in order: locals, the current namespace's effects, `brj.core`'s effects, the current namespace's definitions, `brj.core`'s definitions, imported classes, then host classes by their fully qualified name.

## Running a namespace

A namespace that defines `main` can be run: `main` is called with the command-line arguments.

`brj.main run namespace args…`
: Runs `namespace`'s `main`, from the classpath.

In Gradle, the `BridjeExec` task does the same:

```kotlin
tasks.register<BridjeExec>("run") {
    mainNamespace = "hello.core"
    args("--port", "8080")
}
```

## Privacy

There's no access control: every definition in a namespace is visible to anything that requires it.
By convention, a name starting with `_` is private.
