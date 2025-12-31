plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
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
}

gradlePlugin {
    plugins {
        create("bridje") {
            id = "dev.bridje"
            implementationClass = "brj.gradle.GradlePlugin"
        }
    }
}
