# brj.main - Bridje Application Runner

`brj.main` is the entry point class for running standalone Bridje applications.

## Usage

```bash
java -cp <classpath> brj.main -m <namespace> [args...]
```

### Arguments

- `-m <namespace>` - Required. Specifies the namespace containing the main function (e.g., `my:app`)
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
  do:
    java:lang:System/out.println("Hello from Bridje!")
    java:lang:System/out.println(java:lang:String/format("Args: %s", args))
```

### Running

```bash
# Build a jar with your application and dependencies
./gradlew shadowJar

# Run the application
java -jar myapp.jar -m my:app arg1 arg2
```

## Integration

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

### Gradle Application Plugin

```kotlin
application {
    mainClass.set("brj.main")
    applicationDefaultJvmArgs = listOf("-m", "my:app")
}
```

## Error Handling

`brj.main` will exit with status code 1 if:
- No arguments are provided
- The `-m` flag is missing or incorrect
- The specified namespace is not found on the classpath
- The namespace does not define a `main` function
- An error occurs during execution

Exit codes from the application itself are preserved and propagated.
