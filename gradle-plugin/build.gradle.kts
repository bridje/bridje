import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish")
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(22))
    vendor.set(JvmVendorSpec.GRAAL_VM)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":language"))
    implementation(libs.junit.platform.engine)
    implementation(libs.graal.sdk)
    implementation(libs.truffle.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()

    val worktreeRoot = project.rootDir.absolutePath
    systemProperty("worktreeRoot", worktreeRoot)
}

gradlePlugin {
    plugins {
        create("bridje") {
            id = "dev.bridje"
            implementationClass = "brj.gradle.GradlePlugin"
        }
    }
}

mavenPublishing {
    configure(GradlePlugin(JavadocJar.Javadoc(), sourcesJar = true))
    coordinates("dev.bridje", "gradle-plugin", version.toString())

    pom {
        name.set("Bridje Gradle Plugin")
        description.set("Gradle plugin for the Bridje programming language")
    }
}
