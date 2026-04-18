package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import brj.runtime.BridjeFile
import brj.runtime.BridjeVector
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.strings.TruffleString

class FileNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val path = (frame.arguments[0] as TruffleString).toJavaStringUncached()
        return doCreate(path)
    }

    @TruffleBoundary
    private fun doCreate(path: String): BridjeFile =
        BridjeFile(BridjeContext.get(this).truffleEnv.getPublicTruffleFile(path))
}

class FsExistsNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doExists(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doExists(f: BridjeFile): Boolean = f.truffleFile.exists()
}

class FsIsFileNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doIsFile(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doIsFile(f: BridjeFile): Boolean = f.truffleFile.isRegularFile()
}

class FsIsDirNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doIsDir(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doIsDir(f: BridjeFile): Boolean = f.truffleFile.isDirectory()
}

class FsReadStringNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doReadString(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doReadString(f: BridjeFile): TruffleString {
        val text = f.truffleFile.newBufferedReader().use { it.readText() }
        return TruffleString.fromJavaStringUncached(text, TruffleString.Encoding.UTF_8)
    }
}

class FsListNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doList(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doList(f: BridjeFile): BridjeVector =
        BridjeVector(f.truffleFile.list().map { BridjeFile(it) })
}

class FsResolveNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val f = frame.arguments[0] as BridjeFile
        val path = (frame.arguments[1] as TruffleString).toJavaStringUncached()
        return doResolve(f, path)
    }

    @TruffleBoundary
    private fun doResolve(f: BridjeFile, path: String): BridjeFile =
        BridjeFile(f.truffleFile.resolve(path))
}

class FsNameNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doName(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doName(f: BridjeFile): TruffleString =
        TruffleString.fromJavaStringUncached(f.truffleFile.name, TruffleString.Encoding.UTF_8)
}

class FsPathNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doPath(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doPath(f: BridjeFile): TruffleString =
        TruffleString.fromJavaStringUncached(f.truffleFile.path, TruffleString.Encoding.UTF_8)
}
