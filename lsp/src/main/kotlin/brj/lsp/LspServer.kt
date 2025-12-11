package brj.lsp

import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import java.lang.System.Logger.Level
import java.lang.System.Logger.Level.INFO
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

private val LOGGER = System.getLogger(LspServer::class.qualifiedName)

interface BridjeClient : LanguageClient

class LspServer : LanguageServer, LanguageClientAware {

    private var client: BridjeClient? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val graalCtx = Context.newBuilder("bridje").allowAllAccess(true).build()

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> =
        scope.future {
            LOGGER.log(INFO) { "Initializing LSP server..." }

            InitializeResult(ServerCapabilities().also {
                it.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
                it.executeCommandProvider = ExecuteCommandOptions(listOf("bridje/eval"))
            })
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun shutdown(): CompletableFuture<Any?> =
        GlobalScope.future {
            graalCtx.close(true)
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
            // TODO("Not yet implemented")
        }

        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
            // TODO("Not yet implemented")
        }

        private fun eval(args: List<Any>): String {
            val argsMap = args[0] as? JsonObject ?: error("Invalid arguments for eval command: ${args[0].javaClass}")
            val uri = argsMap["uri"].asString ?: error("Invalid URI")
            val code = argsMap["code"].asString ?: error("Invalid code")

            return graalCtx.eval(Source.newBuilder("bridje", code, uri).build()).toString()
        }

        override fun executeCommand(params: ExecuteCommandParams) = scope.future {
            val args = params.arguments

            when (params.command) {
                "bridje/eval" -> eval(args)

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
