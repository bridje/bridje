package brj

class GlobalEnv(private val vars: Map<String, GlobalVar> = emptyMap()) {
    operator fun get(name: String): GlobalVar? = vars[name]

    fun def(name: String, value: Any?): GlobalEnv =
        GlobalEnv(vars + (name to GlobalVar(name, value)))

    override fun toString(): String = "GlobalEnv(${vars.keys})"
}
