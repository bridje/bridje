package brj.gradle

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import java.io.File

class BridjeTestEngine : TestEngine {

    private var ctx: Context? = null
    private val nsValues = mutableMapOf<String, Value>()

    override fun getId(): String = "bridje"

    override fun getGroupId() = java.util.Optional.of("dev.bridje")

    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Bridje Tests")

        val classSelectors = request.getSelectorsByType(ClassSelector::class.java)
        val roots = request.getSelectorsByType(ClasspathRootSelector::class.java)

        if (classSelectors.isEmpty() && roots.isEmpty()) return engineDescriptor

        val brjFiles = mutableMapOf<String, Pair<String, String>>()
        collectBrjFiles(brjFiles)

        if (brjFiles.isEmpty()) return engineDescriptor

        val context = Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
        ctx = context
        context.enter()

        try {
            for ((nsName, pathAndContent) in brjFiles) {
                val (relativePath, content) = pathAndContent
                val source = Source.newBuilder("bridje", content, relativePath)
                    .mimeType("text/brj")
                    .build()

                try {
                    val result = context.eval(source)
                    val testNamesValue = result.getMember("__test_var_names__")
                    val testVarNames = mutableListOf<String>()
                    if (testNamesValue != null && testNamesValue.hasArrayElements()) {
                        for (i in 0 until testNamesValue.arraySize) {
                            testVarNames.add(testNamesValue.getArrayElement(i).asString())
                        }
                    }

                    if (testVarNames.isNotEmpty()) {
                        nsValues[nsName] = result
                        val nsId = engineDescriptor.uniqueId.append("ns", nsName)
                        val nsDescriptor = BridjeNsDescriptor(nsId, nsName)
                        engineDescriptor.addChild(nsDescriptor)

                        for (varName in testVarNames) {
                            val testId = nsId.append("test", varName)
                            nsDescriptor.addChild(BridjeTestDescriptor(testId, varName))
                        }
                    }
                } catch (_: Exception) {
                    // Skip namespaces that fail to eval
                }
            }
        } catch (e: Exception) {
            context.close()
            ctx = null
            throw e
        } finally {
            context.leave()
        }

        return engineDescriptor
    }

    private fun collectBrjFiles(brjFiles: MutableMap<String, Pair<String, String>>) {
        val classLoader = Thread.currentThread().contextClassLoader ?: return

        val roots = classLoader.getResources("").toList()
        for (url in roots) {
            if (url.protocol == "file") {
                collectFromDirectory(brjFiles, File(url.toURI()))
            }
        }

        val classpath = System.getProperty("java.class.path") ?: return
        for (entry in classpath.split(File.pathSeparator)) {
            val file = File(entry)
            if (file.isDirectory) {
                collectFromDirectory(brjFiles, file)
            }
        }
    }

    private fun collectFromDirectory(brjFiles: MutableMap<String, Pair<String, String>>, dir: File) {
        if (!dir.isDirectory) return
        dir.walkTopDown()
            .filter { it.extension == "brj" }
            .forEach { file ->
                val relativePath = file.relativeTo(dir).path
                val nsName = relativePath.removeSuffix(".brj").replace('/', ':')
                if (nsName !in brjFiles) {
                    brjFiles[nsName] = relativePath to file.readText()
                }
            }
    }

    override fun execute(request: ExecutionRequest) {
        val engineDescriptor = request.rootTestDescriptor
        val listener = request.engineExecutionListener
        val context = ctx ?: return
        context.enter()

        try {
            listener.executionStarted(engineDescriptor)

            for (nsDescriptor in engineDescriptor.children) {
                executeNamespace(nsDescriptor as BridjeNsDescriptor, listener)
            }

            listener.executionFinished(engineDescriptor, TestExecutionResult.successful())
        } finally {
            context.leave()
            context.close()
            ctx = null
            nsValues.clear()
        }
    }

    private fun executeNamespace(nsDescriptor: BridjeNsDescriptor, listener: EngineExecutionListener) {
        listener.executionStarted(nsDescriptor)

        val nsValue = nsValues[nsDescriptor.displayName]
        if (nsValue == null) {
            listener.executionFinished(nsDescriptor, TestExecutionResult.failed(
                IllegalStateException("Namespace ${nsDescriptor.displayName} not found")))
            return
        }

        for (child in nsDescriptor.children.toList()) {
            val testDesc = child as BridjeTestDescriptor
            listener.executionStarted(testDesc)

            try {
                val fn = nsValue.getMember(testDesc.displayName)
                val testResult = fn.execute()

                if (testResult.isBoolean && testResult.asBoolean()) {
                    listener.executionFinished(testDesc, TestExecutionResult.successful())
                } else {
                    listener.executionFinished(
                        testDesc,
                        TestExecutionResult.failed(AssertionError("Test returned: $testResult"))
                    )
                }
            } catch (e: Exception) {
                listener.executionFinished(testDesc, TestExecutionResult.failed(e))
            }
        }

        listener.executionFinished(nsDescriptor, TestExecutionResult.successful())
    }
}

private class BridjeNsDescriptor(
    uniqueId: UniqueId,
    displayName: String,
) : AbstractTestDescriptor(uniqueId, displayName) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER
}

private class BridjeTestDescriptor(
    uniqueId: UniqueId,
    displayName: String,
) : AbstractTestDescriptor(uniqueId, displayName) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
}
