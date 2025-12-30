# Bridje nREPL Server

This directory contains an nREPL-compatible server implementation for Bridje.

## What is nREPL?

nREPL (network REPL) is a Clojure network REPL that provides a server and client for remote code evaluation. It's widely used by development tools like CIDER, Calva, Conjure, and vim-fireplace.

## Features

The Bridje nREPL server implements the following operations:

- **clone**: Create new sessions (optionally cloning from existing ones)
- **describe**: Returns server capabilities and version information
- **eval**: Evaluates Bridje code in a session context
- **close**: Closes a session and releases its resources
- **ls-sessions**: Lists all active session IDs

## Starting the Server

### Using Gradle

```bash
./gradlew :repl:nrepl
```

Or with a specific port:

```bash
./gradlew :repl:nrepl --args="7888"
```

### Using the Compiled JAR

```bash
java -jar repl/build/libs/bridje-repl.jar [port]
```

If no port is specified, the server will bind to a random available port.

## Port File

When the server starts, it creates a `.nrepl-port` file in the current directory containing the port number. This file is automatically deleted when the server shuts down.

Many nREPL clients automatically look for this file to determine which port to connect to.

## Protocol

The server uses bencode encoding (the default nREPL transport) for message exchange. Messages are dictionaries with an `op` key indicating the operation to perform.

### Example Messages

**Clone (create session):**
```clojure
{:op "clone" :id "1"}
```

**Evaluate code:**
```clojure
{:op "eval" :code "(+ 1 2 3)" :session "session-id" :id "2"}
```

**Describe server:**
```clojure
{:op "describe" :id "3"}
```

**List sessions:**
```clojure
{:op "ls-sessions" :id "4"}
```

**Close session:**
```clojure
{:op "close" :session "session-id" :id "5"}
```

## Connecting with nREPL Clients

### CIDER (Emacs)

```elisp
M-x cider-connect RET localhost RET <port>
```

### Calva (VS Code)

1. Run "Calva: Connect to a Running REPL Server"
2. Select "Generic"
3. Enter the hostname and port

### Conjure (Neovim)

```vim
:ConjureConnect localhost <port>
```

Or Conjure will automatically detect the `.nrepl-port` file.

### Command Line (using Python client)

A simple test client is available for testing:

```bash
python3 /tmp/test_nrepl.py <port>
```

## Implementation Details

### Architecture

- **Bencode.kt**: Encoder/decoder for the bencode format used by nREPL
- **Session.kt**: Session management with isolated GraalVM polyglot contexts
- **NReplHandler.kt**: Request handler that dispatches operations
- **NReplServer.kt**: Socket server that accepts connections and manages the lifecycle

### Session Isolation

Each nREPL session runs in its own GraalVM polyglot context, providing isolation between different evaluation sessions.

### Thread Safety

The server uses a cached thread pool to handle multiple concurrent client connections. Session management is thread-safe using concurrent data structures.

## Limitations

This is a basic nREPL implementation supporting the core operations. Advanced middleware features from the Clojure nREPL (such as completion, debugging, or printing middleware) are not yet implemented.

## References

- [nREPL Protocol Documentation](https://nrepl.org/)
- [nREPL Operations](https://nrepl.org/nrepl/ops.html)
- [Bencode Specification](https://wiki.theory.org/BitTorrentSpecification#Bencoding)
