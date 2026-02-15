package brj.gradle

// Marker class copied into testClassesDirs to ensure Gradle forks the test JVM.
// Without at least one .class file, Gradle's Test task short-circuits before
// invoking JUnit Platform discovery, so our BridjeTestEngine never runs.
class BridjeTestMarker
