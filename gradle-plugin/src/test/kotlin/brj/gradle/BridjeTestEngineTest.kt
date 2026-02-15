package brj.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridjeTestEngineTest {

    @TempDir
    lateinit var projectDir: File

    private val worktreeRoot: String = System.getProperty("worktreeRoot")

    @BeforeEach
    fun setUp() {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test"

            pluginManagement {
                includeBuild("$worktreeRoot")
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
            }

            includeBuild("$worktreeRoot")
            """.trimIndent()
        )

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("dev.bridje") version "0.x-SNAPSHOT"
            }

            repositories {
                mavenCentral()
            }

            bridje {
                version = "0.x-SNAPSHOT"
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(22)
                    vendor = JvmVendorSpec.GRAAL_VM
                }
            }
            """.trimIndent()
        )
    }

    private fun writeBrjFile(path: String, content: String) {
        val file = File(projectDir, "src/test/bridje/$path")
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun runner(): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("test", "--stacktrace")
            .forwardOutput()

    @Test
    fun `passing tests succeed`() {
        writeBrjFile("my/tests.brj", """
            ns: my:tests

            ^test
            def: twoPlusTwo()
              eq(add(2, 2), 4)
        """.trimIndent())

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
    }

    @Test
    fun `failing test fails the build`() {
        writeBrjFile("my/tests.brj", """
            ns: my:tests

            ^test
            def: thisOneFails()
              eq(1, 2)
        """.trimIndent())

        val result = runner().buildAndFail()

        assertTrue(result.output.contains("1 failed"))
    }

    @Test
    fun `mixed pass and fail`() {
        writeBrjFile("my/tests.brj", """
            ns: my:tests

            ^test
            def: twoPlusTwo()
              eq(add(2, 2), 4)

            ^test
            def: stringsWork()
              eq("hello", "hello")

            ^test
            def: thisOneFails()
              eq(add(1, 1), 3)
        """.trimIndent())

        val result = runner().buildAndFail()

        assertTrue(result.output.contains("3 tests completed"))
        assertTrue(result.output.contains("1 failed"))
    }

    @Test
    fun `no test metadata means no tests discovered`() {
        writeBrjFile("my/lib.brj", """
            ns: my:lib

            def: helper()
              add(1, 1)
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("dev.bridje") version "0.x-SNAPSHOT"
            }

            repositories {
                mavenCentral()
            }

            bridje {
                version = "0.x-SNAPSHOT"
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(22)
                    vendor = JvmVendorSpec.GRAAL_VM
                }
            }

            tasks.test {
                failOnNoDiscoveredTests = false
            }
            """.trimIndent()
        )

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
    }
}
