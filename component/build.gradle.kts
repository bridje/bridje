plugins {
    base
}

evaluationDependsOn(":language")
evaluationDependsOn(":launcher")
evaluationDependsOn(":native-image")

tasks.register<Jar>("component") {
    group = "build"
    description = "Build component JAR suitable for adding into GraalVM"
    archiveFileName.set("brj-component.jar")

    manifest {
        attributes(
            "Bundle-Symbolic-Name" to "brj",
            "Bundle-RequireCapability" to """org.graalvm; filter:="(&(os_arch=amd64))"""",
            "Bundle-Name" to "Bridje",
            "Bundle-Version" to project.version,
            "x-GraalVM-Polyglot-Part" to "True")
    }

    into("META-INF") {
        from("META-INF")
    }

    into("lib/brj") {
        from(project(":launcher").tasks["jar"].outputs)
    }

    into("languages/brj") {
        from(project(":language").tasks["jar"].outputs)

        into ("bin") {
            from(project(":native-image").tasks["nativeImage"].outputs)
        }
    }
}
