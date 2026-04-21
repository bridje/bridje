package brj.test.engine

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.FilePosition
import org.junit.platform.engine.support.descriptor.FileSource
import org.opentest4j.AssertionFailedError
import org.opentest4j.MultipleFailuresError
import java.io.File

class BridjeTestEngine : TestEngine {

    private var ctx: Context? = null
    private val nsValues = mutableMapOf<String, Value>()
    private val nsFiles = mutableMapOf<String, File>()

    override fun getId(): String = "bridje"

    override fun getGroupId() = java.util.Optional.of("dev.bridje")

    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Bridje Tests")

        val classSelectors = request.getSelectorsByType(ClassSelector::class.java)
        val roots = request.getSelectorsByType(ClasspathRootSelector::class.java)

        if (classSelectors.isEmpty() && roots.isEmpty()) return engineDescriptor

        val brjFiles = mutableMapOf<String, File>()
        collectBrjFiles(brjFiles)

        if (brjFiles.isEmpty()) return engineDescriptor

        val context = Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
        ctx = context
        context.enter()

        try {
            val bindings = context.getBindings("bridje")

            for ((nsName, file) in brjFiles) {
                // HACK: skip ns's already loaded (stdlib preload, or pulled in by another ns's
                // `require`). Re-eval'ing a loaded ns correctly cascades invalidation through
                // its dependents — that's a feature for REPL/LSP reloads — but here we're only
                // enumerating classpath files, not intentionally reloading. A proper fix would
                // eval in dependency order (topological sort on the `require` graph), but that
                // needs parsing each file first.
                if (bindings.hasMember(nsName)) continue

                val source = Source.newBuilder("bridje", file)
                    .mimeType("text/brj")
                    .build()

                try {
                    nsValues[nsName] = context.eval(source)
                    nsFiles[nsName] = file
                } catch (_: Exception) {
                    // Skip namespaces that fail to eval
                }
            }

            // Pick up ns's that were loaded transitively via requires but aren't in nsValues yet
            // (so discovery sees them and we can wire FileSource for their test fns too).
            for ((nsName, file) in brjFiles) {
                if (nsName !in nsValues && bindings.hasMember(nsName)) {
                    nsValues[nsName] = bindings.getMember(nsName)
                    nsFiles[nsName] = file
                }
            }

            if (nsValues.isNotEmpty()) discoverTests(context, engineDescriptor)
        } catch (e: Exception) {
            context.close()
            ctx = null
            throw e
        } finally {
            context.leave()
        }

        return engineDescriptor
    }

    private fun discoverTests(context: Context, engineDescriptor: EngineDescriptor) {
        val discoverSrc = Source.newBuilder(
            "bridje",
            "mapv(all-nses(), fn: nsL(ns) [ns, mapv(ns-vars(ns), fn: varL(v) [nth(v, 1), meta(v)])])",
            "<test-discovery>"
        ).mimeType("text/brj").build()
        val discovered = context.eval(discoverSrc)

        for (i in 0 until discovered.arraySize) {
            val entry = discovered.getArrayElement(i)
            val nsName = entry.getArrayElement(0).toString()
            if (nsName !in nsValues) continue

            val vars = entry.getArrayElement(1)
            val testVars = mutableListOf<Pair<String, FileSource?>>()
            for (j in 0 until vars.arraySize) {
                val pair = vars.getArrayElement(j)
                val meta = pair.getArrayElement(1)
                if (meta.hasMember("test") && meta.getMember("test").asBoolean()) {
                    val varName = pair.getArrayElement(0).toString()
                    testVars.add(varName to fileSource(meta))
                }
            }

            if (testVars.isEmpty()) continue

            val nsId = engineDescriptor.uniqueId.append("ns", nsName)
            val nsDescriptor = BridjeNsDescriptor(nsId, nsName, nsFileSource(nsName))
            engineDescriptor.addChild(nsDescriptor)

            for ((varName, src) in testVars) {
                val testId = nsId.append("test", varName)
                nsDescriptor.addChild(BridjeTestDescriptor(testId, varName, src))
            }
        }
    }

    private fun nsFileSource(nsName: String): FileSource? =
        nsFiles[nsName]?.let(FileSource::from)

    private fun fileSource(meta: Value): FileSource? {
        if (!meta.hasMember("loc")) return null
        val loc = meta.getMember("loc")
        val path = loc.getMember("path")
        if (path.isNull) return null
        val file = File(path.asString())
        val line = loc.getMember("start-line").asInt()
        val col = loc.getMember("start-column").asInt()
        return FileSource.from(file, FilePosition.from(line, col))
    }

    private fun collectBrjFiles(brjFiles: MutableMap<String, File>) {
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

    private fun collectFromDirectory(brjFiles: MutableMap<String, File>, dir: File) {
        if (!dir.isDirectory) return
        dir.walkTopDown()
            .filter { it.extension == "brj" }
            .forEach { file ->
                val relativePath = file.relativeTo(dir).path
                val nsName = relativePath.removeSuffix(".brj").replace('/', '.')
                if (nsName !in brjFiles) {
                    brjFiles[nsName] = file
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
                executeNamespace(nsDescriptor as BridjeNsDescriptor, listener, context)
            }

            listener.executionFinished(engineDescriptor, TestExecutionResult.successful())
        } finally {
            context.leave()
            context.close()
            ctx = null
            nsValues.clear()
            nsFiles.clear()
        }
    }

    private fun executeNamespace(nsDescriptor: BridjeNsDescriptor, listener: EngineExecutionListener, context: Context) {
        listener.executionStarted(nsDescriptor)

        val nsName = nsDescriptor.displayName
        if (nsName !in nsValues) {
            listener.executionFinished(nsDescriptor, TestExecutionResult.failed(
                IllegalStateException("Namespace $nsName not found")))
            return
        }

        for (child in nsDescriptor.children.toList()) {
            val testDesc = child as BridjeTestDescriptor
            listener.executionStarted(testDesc)
            listener.executionFinished(testDesc, runTest(context, nsName, testDesc.displayName))
        }

        listener.executionFinished(nsDescriptor, TestExecutionResult.successful())
    }

    private fun runTest(context: Context, nsName: String, varName: String): TestExecutionResult {
        val sourceText = "brj.test/run-test($nsName/$varName)"
        val source = Source.newBuilder("bridje", sourceText, "<run-test:$nsName/$varName>")
            .mimeType("text/brj")
            .build()

        val result = try {
            context.parse(source).execute()
        } catch (e: Throwable) {
            return TestExecutionResult.failed(e)
        }

        val failures = result.getMember("failures")
        val exn = result.getMember("exn")

        val errors = buildList<Throwable> {
            val n = failures.arraySize
            for (i in 0 until n) add(toAssertionError(failures.getArrayElement(i)))
            if (!exn.isNull) add(coerceThrowable(exn))
        }

        return when (errors.size) {
            0 -> TestExecutionResult.successful()
            1 -> TestExecutionResult.failed(errors[0])
            else -> TestExecutionResult.failed(MultipleFailuresError("$nsName/$varName", errors))
        }
    }

    private fun toAssertionError(failure: Value): AssertionFailedError {
        val form = failure.getMember("form")
        val message = failure.getMember("message")
        val text = if (message.isNull) "Assertion failed: $form"
        else "Assertion failed: $form — $message"
        return AssertionFailedError(text)
    }

    private fun coerceThrowable(exnValue: Value): Throwable =
        if (exnValue.isException) {
            try {
                exnValue.throwException()
                AssertionError("polyglot reported isException but throwException returned normally")
            } catch (t: Throwable) {
                t
            }
        } else {
            AssertionError("test threw non-exception value: $exnValue")
        }
}

private class BridjeNsDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    source: TestSource?,
) : AbstractTestDescriptor(uniqueId, displayName, source) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER
}

private class BridjeTestDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    source: TestSource?,
) : AbstractTestDescriptor(uniqueId, displayName, source) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
}
