package brj.analyser

class LocalVar(val name: String, val slot: Int)

sealed interface CaptureSource
data class FrameSlotCapture(val slot: Int) : CaptureSource
data class TransitiveCapture(val captureIndex: Int) : CaptureSource

data class CapturedVar(val name: String, val outerLocalVar: LocalVar, val captureIndex: Int, val source: CaptureSource)
