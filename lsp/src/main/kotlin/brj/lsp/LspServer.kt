package brj.lsp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.lang.System.Logger.Level
import java.lang.System.Logger.Level.INFO
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

private val LOGGER = System.getLogger(LspServer::class.qualifiedName)

interface BridjeClient : LanguageClient

class LspServer : LanguageServer, LanguageClientAware {

    private var client: BridjeClient? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(DelicateCoroutinesApi::class)
    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> =
        scope.future {
            LOGGER.log(INFO) { "Initializing LSP server..." }

            InitializeResult(ServerCapabilities().also {
                it.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
                it.executeCommandProvider = ExecuteCommandOptions(listOf("bridje/eval-defun"))
            })
        }

    override fun shutdown(): CompletableFuture<Any?> = completedFuture<Any?>(null)

    override fun exit() = Unit

    override fun getTextDocumentService() = object : TextDocumentService {

        override fun didOpen(params: DidOpenTextDocumentParams) {
            LOGGER.log(INFO, params)
        }

        override fun didChange(params: DidChangeTextDocumentParams) {
            LOGGER.log(INFO, params)
        }

        override fun didClose(params: DidCloseTextDocumentParams) {
            LOGGER.log(INFO, params)
        }

        override fun didSave(params: DidSaveTextDocumentParams) {
            LOGGER.log(INFO, params)
        }
    }

    override fun getWorkspaceService() = object : WorkspaceService {
        override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
            // TODO("Not yet implemented")
        }

        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
            // TODO("Not yet implemented")
        }

        private fun evalDefun(args: List<Any>): String {
            args[0] as? Map<*, *>
            return "hello!"
        }

        override fun executeCommand(params: ExecuteCommandParams) = scope.future {
            val args = params.arguments

            when(params.command) {
                "bridje/eval-defun" -> evalDefun(args)

                else -> {
                    LOGGER.log(Level.WARNING, "Unknown command: ${params.command}")
                }
            }
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
