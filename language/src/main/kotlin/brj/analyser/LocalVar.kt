package brj.analyser

class LocalVar(val name: String, val slot: Int)

data class CapturedVar(val name: String, val outerLocalVar: LocalVar, val innerSlot: Int)
