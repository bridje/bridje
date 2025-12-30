package brj.repl.nrepl

import kotlinx.coroutines.*
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import brj.repl.nrepl.Session.EvalResult

private typealias Request = Map<String, Any?>
private typealias Response = Map<String, Any?>

class NReplServer(private val port: Int = 0) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val serverSocket = ServerSocket(port)

    private val portFile = Path(".nrepl-port")

    private fun createPortFile(port: Int) {
        try {
            portFile.writeText(port.toString())
        } catch (e: IOException) {
            System.err.println("Warning: Could not create .nrepl-port file: ${e.message}")
        }
    }

    private inner class Connection(private val socket: Socket) : AutoCloseable {

        private fun Request.response(vararg pairs: Pair<String, Any?>) =
            mapOf("id" to this["id"], *pairs)

        private fun errorResponse(request: Request, message: String) =
            request.response(
                "status" to listOf("error", "done"), 
                "err" to message
            )

        // cloneFrom (request["session"]) accepted for nREPL protocol compatibility but currently ignored
        private fun handleClone(request: Request): List<Response> {
            val id = UUID.randomUUID().toString()
            sessions[id] = Session(id)
            return listOf(request.response("new-session" to id, "status" to listOf("done")))
        }

        private fun handleClose(request: Request): List<Response> {
            (request["session"] as? String)?.let { sessions.remove(it)?.close() }
            return listOf(request.response("status" to listOf("done")))
        }

        private fun handleDescribe(request: Request) = listOf(request.response(
            "ops" to mapOf(
                "clone" to emptyMap<String, Any>(), 
                "close" to emptyMap(), 
                "describe" to emptyMap(), 
                "eval" to emptyMap(), 
                "ls-sessions" to emptyMap()
            ),
            "versions" to mapOf("bridje" to mapOf("version-string" to "0.0.1")),
            "status" to listOf("done")
        ))

        private fun handleEval(request: Request): List<Response> {
            val sessionId = request["session"] as? String
                ?: return listOf(errorResponse(request, "Missing session"))
            val code = request["code"] as? String
                ?: return listOf(errorResponse(request, "Missing code"))
            val session = sessions[sessionId]
                ?: return listOf(errorResponse(request, "Unknown session"))

            println("code: $code")

            fun resp(vararg pairs: Pair<String, Any?>) = request.response("session" to sessionId, *pairs)

            return when (val result = session.eval(code)) {
                is EvalResult.Success -> listOf(
                    resp("ns" to "user", "value" to result.value),
                    resp("status" to listOf("done"))
                )

                is EvalResult.Error -> listOf(
                    resp("err" to (result.exception.message ?: "Evaluation error")),
                    resp("ex" to result.exception.javaClass.simpleName, "status" to listOf("eval-error", "done"))
                )
            }
        }

        private fun handleLsSessions(request: Request) =
            listOf(request.response(
                "sessions" to sessions.keys.toList(),
                "status" to listOf("done")
            ))

        private fun handle(request: Request): List<Response> {
            val op = request["op"] as? String
                ?: return listOf(errorResponse(request, "Missing op"))

            return when (op) {
                "clone" -> handleClone(request)
                "close" -> handleClose(request)
                "describe" -> handleDescribe(request)
                "eval" -> handleEval(request)
                "ls-sessions" -> handleLsSessions(request)
                else -> listOf(request.response("status" to listOf("error", "unknown-op", "done"), "op" to op))
            }
        }

        suspend fun run() = withContext(Dispatchers.IO) {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            try {
                while (isActive && !socket.isClosed) {
                    val request = try {
                        Bencode.decode(input) as? Map<*, *> ?: break
                    } catch (e: IOException) {
                        break
                    } catch (e: Exception) {
                        System.err.println("Error decoding request: ${e.message}")
                        break
                    }

                    val requestMap = request.mapKeys { it.key.toString() }

                    for (response in handle(requestMap)) {
                        try {
                            Bencode.write(output, response)
                        } catch (e: Exception) {
                            System.err.println("Error writing response: ${e.message}")
                            return@withContext
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActive) System.err.println("Error handling client: ${e.message}")
            } finally {
                println("Client disconnected")
            }
        }

        override fun close() {
            socket.close()
        }
    }

    fun start() {
        val actualPort = serverSocket.localPort

        createPortFile(actualPort)

        scope.launch {
            while (isActive) {
                try {
                    val client = serverSocket.accept()
                    println("Client connected: ${client.inetAddress.hostAddress}")
                    launch { Connection(client).use { it.run() } }
                } catch (e: IOException) {
                    if (isActive) System.err.println("Error accepting connection: ${e.message}")
                }
            }
        }

        println("nREPL server started on port $actualPort")
    }

    suspend fun join() {
        scope.coroutineContext.job.join()
    }

    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        println("Stopping nREPL server...")

        scope.cancel()
        serverSocket.close()
        sessions.values.forEach { it.close() }
        sessions.clear()
        portFile.deleteIfExists()

        println("nREPL server stopped")
    }
}

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 0

    NReplServer(port).use { server ->
        server.start()

        Runtime.getRuntime().addShutdownHook(Thread { server.close() })

        runBlocking { server.join() }
    }
}
