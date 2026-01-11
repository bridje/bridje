package brj.analyser

data class NsDecl(
    val name: String,
    val requires: Map<String, String> = emptyMap(),
    val imports: Map<String, String> = emptyMap()
)

