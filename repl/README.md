# Bridje nREPL Server

This directory contains an nREPL-compatible server implementation for Bridje.

## Starting the Server

### Using Gradle

```bash
./gradlew :repl:run
```

Or with a specific port:

```bash
./gradlew :repl:run --args="7888"
```

### Using the Compiled JAR

```bash
java -jar repl/build/libs/bridje-repl.jar [port]
```

If no port is specified, the server will bind to a random available port.

## Port File

When the server starts, it creates a `.nrepl-port` file in the current directory containing the port number. 
This file is automatically deleted when the server shuts down.

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

## Limitations

This is a basic nREPL implementation supporting the core operations. 
Advanced middleware features from the Clojure nREPL (such as completion, debugging, or printing middleware) are not yet implemented.

## References

- [nREPL Protocol Documentation](https://nrepl.org/)
- [nREPL Operations](https://nrepl.org/nrepl/ops.html)
- [Bencode Specification](https://wiki.theory.org/BitTorrentSpecification#Bencoding)
