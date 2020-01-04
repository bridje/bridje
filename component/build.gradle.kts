plugins {
    base
}

tasks.register("component", type = Jar::class) {
    group = "build"
    description = "Build component JAR suitable for adding into GraalVM"
    archiveBaseName.set("${rootProject.name}-component")

    manifest {
        attributes(
            "Bundle-Symbolic-Name" to "brj",
            "Bundle-RequireCapability" to """org.graalvm; filter:="(&(graalvm_version=19.3.0)(os_arch=amd64))"""",
            "Bundle-Name" to "Bridje",
            "Bundle-Version" to "0.0.1",
            "x-GraalVM-Polyglot-Part" to "True")
    }

    into("languages/brj") {
        from(project(":language").tasks["uberjar"].outputs)
    }
}
