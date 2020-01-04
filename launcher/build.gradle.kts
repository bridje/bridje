plugins {
    kotlin("jvm") version "1.3.61"
}

repositories {
    jcenter()
}

fun truffle(module: String) = "org.graalvm.truffle:truffle-$module:19.3.0"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":language"))

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

tasks.compileKotlin {
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    group = "build"
    archiveBaseName.set("brj-launcher")

    manifest {
        attributes("Main-Class" to "brj.MainKt")
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
