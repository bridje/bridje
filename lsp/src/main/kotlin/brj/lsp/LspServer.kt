package brj.lsp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.future.future
import kotlinx.coroutines.job
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.lang.System.Logger.Level.INFO
import java.net.URI
import java.util.concurrent.CompletableFuture

private val LOGGER = System.getLogger(LspServer::class.qualifiedName)

interface BridjeClient : LanguageClient

class LspServer : LanguageServer, LanguageClientAware {

    private var client: BridjeClient? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> =
        scope.future {
            LOGGER.log(INFO) { "Initializing LSP server..." }

            InitializeResult(ServerCapabilities().also {
                it.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
            })
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun shutdown(): CompletableFuture<Any?> =
        GlobalScope.future {
            scope.coroutineContext.job.cancelAndJoin()
        }

    override fun exit() = Unit

    private val openFiles = mutableMapOf<URI, String>()

    override fun getTextDocumentService() = object : TextDocumentService {

        override fun didOpen(params: DidOpenTextDocumentParams) {
            openFiles[URI(params.textDocument.uri)] = params.textDocument.text
        }

        override fun didChange(params: DidChangeTextDocumentParams) {
            val contentChanges = params.contentChanges.first()
            openFiles[URI(params.textDocument.uri)] = contentChanges.text
        }

        override fun didClose(params: DidCloseTextDocumentParams) {
            openFiles.remove(URI(params.textDocument.uri))
        }

        override fun didSave(params: DidSaveTextDocumentParams) {
            LOGGER.log(INFO, params)
        }
    }

    override fun getWorkspaceService() = object : WorkspaceService {
        override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        }

        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        }
    }

    override fun setTrace(params: SetTraceParams) {
        LOGGER.log(INFO, params)
    }

    override fun connect(client: LanguageClient) {
        this.client = client as BridjeClient
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val server = LspServer()
            val launcher = LSPLauncher.Builder<BridjeClient>()
                .setLocalService(server)
                .setInput(System.`in`)
                .setOutput(System.out)
                .setRemoteInterface(BridjeClient::class.java)
                .create()

            launcher.startListening()
            LOGGER.log(INFO) { "LSP server listening..." }

            server.connect(launcher.remoteProxy)
        }
    }
}
