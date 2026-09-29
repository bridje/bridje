---
title: Getting started
description: From nothing to a running Bridje program, a REPL and a passing test, in about five minutes.
---

By the end of this page you'll have a Bridje project that runs, a REPL connected to it, and a test that passes.

## What you'll need

- **JDK 25.**
  Bridje, and the Gradle build that runs it, need Java 25 or later.
  Any JDK will do, but [GraalVM](https://www.graalvm.org/downloads/) (the latest JDK 25 update) will run Bridje _much_ faster: Bridje is built on GraalVM's [Truffle](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/) framework, and it's GraalVM's compiler that turns a Truffle interpreter into optimised machine code.
  On any other JDK, Bridje runs as an interpreter only, and says so when it starts.
- **[Gradle](https://gradle.org/install/) 9.1 or later**, running on that JDK — you'll only need it once, to create the project's Gradle wrapper.
- **Linux on x64**, for now: Bridje's parser is a native library, and so far it's only built for this platform.

## A new project

Make a directory with three files in it:

```
hello/
├── settings.gradle.kts
├── build.gradle.kts
└── src/main/bridje/hello/core.brj
```

`settings.gradle.kts` tells Gradle where to find the Bridje plugin:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "hello"
```

`build.gradle.kts` applies it, and registers a `run` task for our program:

```kotlin
import brj.gradle.BridjeExec

plugins {
    id("dev.bridje") version "0.1.0"
}

repositories {
    mavenCentral()
}

bridje {
    version = "0.1.0"
}

tasks.register<BridjeExec>("run") {
    mainNamespace = "hello.core"
}
```

And `src/main/bridje/hello/core.brj` is our program:

```bridje
ns: hello.core

def: greeting(name)
  println(name)

def: main(args)
  greeting("Hello, world!")
```

Bridje source lives under `src/main/bridje`, one namespace per file: the namespace `hello.core` lives in `hello/core.brj`.
A namespace that defines `main` can be run; `main` is passed the command-line arguments.

Finally, create the Gradle wrapper, which the rest of the tooling — and your editor — will use:

```sh
gradle wrapper
```

## Running it

```sh
./gradlew run
```

The first run takes a little while, as Gradle fetches Bridje; after that you'll see, among Gradle's output:

```
> Task :run
Hello, world!
```

## A REPL

Rather than re-running the whole program for every change, a REPL lets you evaluate code against a running process, and build your program up a piece at a time.

```sh
./gradlew bridjeRepl
```

This starts an [nREPL](https://nrepl.org/) server, and writes its port to `.nrepl-port` in the project directory, where editors find it — [Editor setup](/getting-started/editors/) covers connecting from each.

Once you're connected:

- **Evaluate a whole file** — `core.brj` — to load its namespace.
- **Call into it** with a qualified name: `hello.core/greeting("from the REPL")`.
- **Evaluate any other expression** on its own: `mapv([1, 2, 3], #: mul(it, 2))`.

:::note
For now, each evaluation stands alone: a `def:` evaluated on its own isn't visible to the next evaluation.
To change a definition, edit the file and re-evaluate it.
:::

## A test

Tests live under `src/test/bridje`, and run through Gradle's usual `test` task.
Add `src/test/bridje/hello/core-test.brj`:

```bridje
ns: hello.core-test
  require:
    brj:
      as(test, t)

^t/.test
def: arithmetic()
  t/is(eq(4, add(2, 2)))
  t/is(eq(3, count([1, 2, 3])))
```

`^t/.test` marks `arithmetic` as a test; `t/is` records a failure if its expression is false.

```sh
./gradlew test
```

Change one of the `eq`s so that it fails, and run it again: Gradle reports `hello.core-test > arithmetic FAILED`, with the details in its HTML test report.

## Next steps

- [The tour](/tour/first-steps/) walks through the language — start there.
- If you've written Clojure, Java or Kotlin, the [Coming from…](/coming-from/clojure/) guides will get you productive faster.
