# Releasing Bridje

## Snapshots

Publish snapshots to Maven Central:

```bash
./gradlew publishToMavenCentral
```

## Releases

Publish a release to Maven Central:

```bash
BRJ_VERSION=0.1.0 ./gradlew publishToMavenCentral
```

Then approve the release on [Central Portal](https://central.sonatype.com).

## Artifacts

| Module | Coordinates |
|--------|-------------|
| language | `dev.bridje:language` |
| lsp | `dev.bridje:lsp` |
| repl | `dev.bridje:repl` |
| gradle-plugin | `dev.bridje:gradle-plugin` |
| marker | `dev.bridje:dev.bridje.gradle.plugin` |

## Credentials

Credentials should be configured in `~/.gradle/gradle.properties`:

```properties
# Maven Central
mavenCentralUsername=<token-username>
mavenCentralPassword=<token-password>

# GPG signing
signing.gnupg.executable=gpg
signing.gnupg.keyName=<key-id>
```
