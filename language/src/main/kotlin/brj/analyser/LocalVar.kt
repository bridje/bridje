package brj.analyser

import brj.runtime.Symbol

class LocalVar(val name: Symbol, val slot: Int)

sealed interface CaptureSource
data class FrameSlotCapture(val slot: Int) : CaptureSource
data class TransitiveCapture(val captureIndex: Int) : CaptureSource

data class CapturedVar(val name: Symbol, val outerLocalVar: LocalVar, val captureIndex: Int, val source: CaptureSource)
