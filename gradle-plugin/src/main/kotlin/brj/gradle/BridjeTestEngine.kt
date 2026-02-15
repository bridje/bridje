package brj.gradle

import brj.GlobalVar
import brj.NsEnv
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.interop.InteropLibrary
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import java.io.File

class BridjeTestEngine : TestEngine {

    override fun getId(): String = "bridje"

    override fun getGroupId() = java.util.Optional.of("dev.bridje")

    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Bridje Tests")

        val classSelectors = request.getSelectorsByType(ClassSelector::class.java)
        val roots = request.getSelectorsByType(ClasspathRootSelector::class.java)

        if (classSelectors.isEmpty() && roots.isEmpty()) return engineDescriptor

        // Collect .brj files from classpath
        val brjFiles = mutableMapOf<String, Pair<String, String>>() // nsName -> (relativePath, content)
        collectBrjFiles(brjFiles)

        if (brjFiles.isEmpty()) return engineDescriptor

        // Eval all namespaces to find ^test vars during discovery
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                ctx.enter()
                try {
                    for ((nsName, pathAndContent) in brjFiles) {
                        val (relativePath, content) = pathAndContent
                        val source = Source.newBuilder("bridje", content, relativePath)
                            .mimeType("text/brj")
                            .build()

                        try {
                            val result = ctx.eval(source)
                            val nsEnv = result.`as`(NsEnv::class.java)
                            val testVars = nsEnv.vars.values.filter { hasTestMeta(it) }

                            if (testVars.isNotEmpty()) {
                                val nsId = engineDescriptor.uniqueId.append("ns", nsName)
                                val nsDescriptor = BridjeNsDescriptor(nsId, nsName, relativePath, content)
                                engineDescriptor.addChild(nsDescriptor)

                                for (globalVar in testVars) {
                                    val testId = nsId.append("test", globalVar.name)
                                    nsDescriptor.addChild(BridjeTestDescriptor(testId, "${nsName}/${globalVar.name}"))
                                }
                            }
                        } catch (_: Exception) {
                            // Skip namespaces that fail to eval
                        }
                    }
                } finally {
                    ctx.leave()
                }
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

        listener.executionStarted(engineDescriptor)

        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                ctx.enter()
                try {
                    for (nsDescriptor in engineDescriptor.children) {
                        executeNamespace(ctx, nsDescriptor as BridjeNsDescriptor, listener)
                    }
                } finally {
                    ctx.leave()
                }
            }

        listener.executionFinished(engineDescriptor, TestExecutionResult.successful())
    }

    private fun executeNamespace(ctx: Context, nsDescriptor: BridjeNsDescriptor, listener: EngineExecutionListener) {
        listener.executionStarted(nsDescriptor)

        try {
            val source = Source.newBuilder("bridje", nsDescriptor.sourceContent, nsDescriptor.resourcePath)
                .mimeType("text/brj")
                .build()
            val result = ctx.eval(source)
            val nsEnv = result.`as`(NsEnv::class.java)

            for (child in nsDescriptor.children.toList()) {
                val testDesc = child as BridjeTestDescriptor
                listener.executionStarted(testDesc)

                try {
                    val varName = testDesc.displayName.substringAfter("/")
                    val globalVar = nsEnv.vars[varName]!!
                    val fn = globalVar.value!!
                    val fnValue = ctx.asValue(fn)
                    val testResult = fnValue.execute()

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
        } catch (e: Exception) {
            listener.executionFinished(nsDescriptor, TestExecutionResult.failed(e))
        }
    }

    private fun hasTestMeta(globalVar: GlobalVar): Boolean {
        val meta = globalVar.meta
        if (meta === BridjeRecord.EMPTY) return false
        return try {
            val interop = InteropLibrary.getUncached()
            interop.isMemberReadable(meta, "test") && interop.readMember(meta, "test") == true
        } catch (_: Exception) {
            false
        }
    }
}

private class BridjeNsDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    val resourcePath: String,
    val sourceContent: String,
) : AbstractTestDescriptor(uniqueId, displayName) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER
}

private class BridjeTestDescriptor(
    uniqueId: UniqueId,
    displayName: String,
) : AbstractTestDescriptor(uniqueId, displayName) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
}