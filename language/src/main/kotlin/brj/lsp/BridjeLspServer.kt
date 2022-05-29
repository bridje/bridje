package brj.lsp

import brj.runtime.BridjeContext
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleContext
import com.oracle.truffle.api.source.Source
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.*
import java.net.URI
import java.util.concurrent.CompletableFuture

internal class BridjeLspServer(
    private val ctx: BridjeContext
) : LanguageServer, LanguageClientAware {

    private lateinit var client: LanguageClient
    private val currentDocState: MutableMap<URI, String> = mutableMapOf()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val serverCapabilities = ServerCapabilities().apply {
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
        }
        return CompletableFuture.completedFuture(InitializeResult(serverCapabilities))
    }

    override fun initialized(params: InitializedParams) {
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
    }

    override fun getTextDocumentService(): TextDocumentService {
        return object : TextDocumentService {
            override fun didOpen(params: DidOpenTextDocumentParams) {
            }

            override fun didChange(params: DidChangeTextDocumentParams) {
                currentDocState[URI(params.textDocument.uri)] = params.contentChanges.first().text
            }

            override fun didClose(params: DidCloseTextDocumentParams) {
                currentDocState.remove(URI(params.textDocument.uri))
            }

            override fun didSave(params: DidSaveTextDocumentParams) {
                currentDocState.remove(URI(params.textDocument.uri))
            }
        }
    }

    override fun getWorkspaceService(): WorkspaceService {
        return object : WorkspaceService {
            override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
                TODO("Not yet implemented")
            }

            override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
                TODO("Not yet implemented")
            }

            override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
                return super.executeCommand(params)
            }
        }
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    data class LspEvalBufferParams(val textDocument: TextDocumentItem)
    data class LspEvalDefunParams(val textDocument: TextDocumentItem, val position: Position)
    data class LspEvalResult(val res: String)

    private fun <R> TruffleContext.inContext(f: TruffleContext.() -> R): R {
        val prev = enter(null)
        return try {
            f()
        } finally {
            leave(null, prev)
        }
    }

    @JsonRequest("brj/evalBuffer", useSegment = false)
    fun evalBuffer(msg: LspEvalBufferParams): CompletableFuture<LspEvalResult> {
        val truffleEnv = ctx.truffleEnv

        val uri = URI(msg.textDocument.uri)
        val updatedSrc = currentDocState[uri]

        val source = if (updatedSrc != null) {
            Source.newBuilder("brj", updatedSrc, uri.path).build()
        } else {
            Source.newBuilder("brj", truffleEnv.getPublicTruffleFile(uri)).build()
        }

        val res = truffleEnv.context.inContext { truffleEnv.parsePublic(source).call() }

        return CompletableFuture.completedFuture(LspEvalResult(res.toString()))
    }

    @JsonRequest("brj/evalDefun", useSegment = false)
    internal fun evalDefun(msg: LspEvalDefunParams): CompletableFuture<LspEvalResult> {
        System.err.println(msg)
        return CompletableFuture.completedFuture(LspEvalResult("success!"))
    }
}

@TruffleBoundary
internal fun startLspServer(bridjeCtx: BridjeContext) {
    val server = BridjeLspServer(bridjeCtx)

    val serverLauncher =
        LSPLauncher.createServerLauncher(server, bridjeCtx.truffleEnv.`in`(), bridjeCtx.truffleEnv.out())

    server.connect(serverLauncher.remoteProxy)
    serverLauncher.startListening().get()
}