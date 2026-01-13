# brj.main - Bridje Application Runner

`brj.main` is the entry point class for running standalone Bridje applications.

## Usage

```bash
java -cp <classpath> brj.main run <namespace> [args...]
```

### Arguments

- `run` - Subcommand to execute a namespace's main function
- `<namespace>` - Required. Namespace containing the main function (e.g., `my:app`)
- `[args...]` - Optional. Command-line arguments passed to the main function as a vector

## Requirements

The target namespace must:
1. Be available on the classpath (as `<prefix>/<name>.brj`)
2. Define a `main` function that accepts a single argument (a vector of strings)

## Example

### Application Namespace (`my/app.brj`)

```bridje
ns: my:app

def: main(args)
  ...
```

### Running

```bash
# Build a jar with your application and dependencies
./gradlew shadowJar

# Run the application
java -jar myapp.jar run my:app arg1 arg2
```

## Integration

### BridjeExec Gradle Task (Development)

For development workflows, the Bridje Gradle plugin provides a `BridjeExec` task type that automatically configures the classpath and arguments:

```kotlin
tasks.register<BridjeExec>("runApp") {
    mainNamespace.set("my:app")
    args("--port", "8080", "--debug")
}
```

Then run with:

```bash
./gradlew runApp
```

The `BridjeExec` task:
- Extends `JavaExec` with bridje-specific configuration
- Automatically includes the runtime classpath
- Sets `brj.main` as the main class
- Prepends `-m <namespace>` to any provided arguments
- Belongs to the "bridje" task group

### Shadow Jar

In your `build.gradle.kts`:

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

shadowJar {
    manifest {
        attributes["Main-Class"] = "brj.main"
    }
}
```

Then run with: `java -jar myapp.jar run my:app`

### Gradle Application Plugin

```kotlin
application {
    mainClass.set("brj.main")
    applicationDefaultJvmArgs = listOf("run", "my:app")
}
```

Then run with: `./gradlew run --args="run my:app arg1 arg2"`

## Command Structure

The runner uses [Clikt](https://ajalt.github.io/clikt/) for command-line parsing:

- **Main command**: `brj.main` - Shows help if no subcommand provided
- **Subcommand**: `run <namespace> [args...]` - Executes namespace main function

## Error Handling

`brj.main` will exit with status code 1 if:
- No subcommand is provided
- The namespace argument is missing
- The specified namespace is not found on the classpath
- The namespace does not define a `main` function
- An error occurs during execution

Exit codes from the application itself are preserved and propagated.

## Implementation Details

- Uses `Source.newBuilder()` with `getResource()` to load namespace files from classpath
- Arguments are passed as a List which GraalVM converts to an interop list for the main function
- Proper context lifecycle management with try-with-resources
- Error messages written to stderr

