package brj.nodes

import brj.BridjeLanguage
import brj.GlobalVar
import brj.runtime.*
import brj.runtime.Anomaly.Companion.host
import brj.runtime.Anomaly.Companion.incorrect
import brj.runtime.Anomaly.Companion.interrupted
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.bytecode.BytecodeRootNode
import com.oracle.truffle.api.bytecode.ConstantOperand
import com.oracle.truffle.api.bytecode.GenerateBytecode
import com.oracle.truffle.api.bytecode.Operation
import com.oracle.truffle.api.bytecode.Variadic
import com.oracle.truffle.api.dsl.Bind
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Fallback
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode

/**
 * The single bytecode root node the whole language compiles to.
 *
 * Every Bridje function body, `loop` body and top-level form becomes one instance,
 * built by [brj.Emitter] through the generated `BridjeRootNodeGen.Builder`.
 *
 * A closure's captured values arrive as argument 0 and are read by [LoadCaptured];
 * its parameters therefore start at argument 1, which is why [LoadParam] takes an
 * absolute argument index rather than a parameter number.
 */
@GenerateBytecode(
    languageClass = BridjeLanguage::class,
    enableBlockScoping = false,
    boxingEliminationTypes = [Long::class, Boolean::class, Double::class],
)
abstract class BridjeRootNode protected constructor(
    language: BridjeLanguage,
    frameDescriptor: FrameDescriptor.Builder,
) : RootNode(language, frameDescriptor.build()), BytecodeRootNode {

    @Operation
    @ConstantOperand(type = GlobalVar::class)
    class LoadGlobalVar {
        companion object {
            @JvmStatic
            @Specialization
            fun read(globalVar: GlobalVar): Any? = globalVar.value
        }
    }

    @Operation
    @ConstantOperand(type = Int::class)
    class LoadCaptured {
        companion object {
            @JvmStatic
            @Specialization
            fun read(frame: VirtualFrame, index: Int): Any {
                @Suppress("UNCHECKED_CAST")
                val captures = frame.arguments[0] as Array<Any?>
                return captures[index]!!
            }
        }
    }

    /**
     * Reads argument [index], defaulting to an empty record where the caller passed fewer
     * arguments than the function declares.
     */
    @Operation
    @ConstantOperand(type = Int::class)
    class LoadParam {
        companion object {
            @JvmStatic
            @Specialization
            fun read(frame: VirtualFrame, index: Int): Any? {
                val args = frame.arguments
                return if (index < args.size) args[index] else BridjeRecord.EMPTY
            }
        }
    }

    @Operation
    class MakeVector {
        companion object {
            @JvmStatic
            @Specialization
            fun make(@Variadic els: Array<Any?>): Any = BridjeVector(els.toValueList())
        }
    }

    @Operation
    @ConstantOperand(type = Array<QSymbol>::class)
    class MakeRecord {
        companion object {
            @JvmStatic
            @Specialization
            fun make(keys: Array<QSymbol>, @Variadic values: Array<Any?>): Any =
                BridjeRecord(keys, values.toValueList())
        }
    }

    @Operation
    @ConstantOperand(type = QSymbol::class)
    class SetRecordKey {
        companion object {
            @JvmStatic
            @Specialization
            fun set(key: QSymbol, record: BridjeRecord, value: Any?): Any = record.set(key, value) ?: BridjeNull
        }
    }

    @Operation
    @ConstantOperand(type = Array<QSymbol>::class)
    class UpdateRecord {
        companion object {
            @JvmStatic
            @Specialization
            @ExplodeLoop
            fun update(keys: Array<QSymbol>, record: BridjeRecord, @Variadic values: Array<Any?>): Any {
                var result = record
                for (i in keys.indices) result = result.put(keys[i], values[i])
                return result
            }
        }
    }

    /**
     * A function with no captured values, shared across every evaluation of the `fn` expression
     * that produced it.
     *
     * The operand is the root node rather than its call target because a call target cannot be
     * requested while the enclosing unit is still being parsed.
     */
    @Operation
    @ConstantOperand(type = BridjeRootNode::class)
    class LoadFunction {
        companion object {
            @JvmStatic
            @Specialization
            fun load(
                rootNode: BridjeRootNode,
                @Cached(value = "createFunction(rootNode)", neverDefault = true) function: BridjeFunction,
            ): Any = function

            @JvmStatic
            fun createFunction(rootNode: BridjeRootNode): BridjeFunction = BridjeFunction(rootNode.callTarget)
        }
    }

    @Operation
    @ConstantOperand(type = BridjeRootNode::class)
    class MakeClosure {
        companion object {
            @JvmStatic
            @Specialization
            fun make(
                rootNode: BridjeRootNode,
                @Variadic captures: Array<Any?>,
                @Cached(value = "rootNode.getCallTarget()", neverDefault = true) callTarget: RootCallTarget,
            ): Any = BridjeFunction(callTarget, captures)
        }
    }

    @Operation
    class Invoke {
        companion object {
            @JvmStatic
            @Specialization
            fun invoke(
                fn: Any,
                @Variadic args: Array<Any?>,
                @Bind node: Node,
                @CachedLibrary(limit = "3") interop: InteropLibrary,
            ): Any? =
                try {
                    interop.execute(fn, *args)
                } catch (e: UnsupportedMessageException) {
                    throw incorrect("Not callable: $fn", node)
                } catch (e: ArityException) {
                    throw incorrect("Wrong arity: expected ${e.expectedMinArity}, got ${e.actualArity}", node)
                } catch (e: UnsupportedTypeException) {
                    throw incorrect("Unsupported argument type", node)
                }
        }
    }

    @Operation
    @ConstantOperand(type = Array<GlobalVar>::class)
    class BuildFxMap {
        companion object {
            @JvmStatic
            @Specialization
            @TruffleBoundary
            fun build(keys: Array<GlobalVar>, base: BridjeFxMap, @Variadic values: Array<Any?>): Any {
                val overrides = mutableMapOf<GlobalVar, Any?>()
                for (i in keys.indices) overrides[keys[i]] = values[i]
                return base.assoc(overrides)
            }
        }
    }

    @Operation
    @ConstantOperand(type = GlobalVar::class)
    class ReadFxEntry {
        companion object {
            @JvmStatic
            @Specialization
            fun read(effectVar: GlobalVar, fxMap: BridjeFxMap): Any? = fxMap[effectVar] ?: effectVar.value
        }
    }

    @Operation
    @ConstantOperand(type = TruffleObject::class)
    @ConstantOperand(type = String::class)
    class ReadHostMember {
        companion object {
            @JvmStatic
            @Specialization
            fun read(
                hostClass: TruffleObject,
                memberName: String,
                @CachedLibrary(limit = "3") interop: InteropLibrary,
            ): Any? = interop.readMember(hostClass, memberName)
        }
    }

    /** Unwraps a Bridje boolean for the `IfThenElse`, `Conditional` and `While` operations, which need a primitive. */
    @Operation
    class AsBoolean {
        companion object {
            @JvmStatic
            @Specialization
            fun ofBoolean(value: Boolean): Boolean = value

            @JvmStatic
            @Fallback
            fun notBoolean(value: Any?, @Bind node: Node): Boolean =
                throw incorrect("Expected a boolean, got: $value", node)
        }
    }

    /** A `case` scrutinee of Java `null` matches the `nil` branch, so normalise before dispatching. */
    @Operation
    class OrNil {
        companion object {
            @JvmStatic
            @Specialization
            fun orNil(value: Any?): Any = value ?: BridjeNull
        }
    }

    @Operation
    class IsNil {
        companion object {
            @JvmStatic
            @Specialization
            fun isNil(value: Any, @CachedLibrary(limit = "3") interop: InteropLibrary): Boolean = interop.isNull(value)
        }
    }

    @Operation
    class IsNotNil {
        companion object {
            @JvmStatic
            @Specialization
            fun isNotNil(value: Any, @CachedLibrary(limit = "3") interop: InteropLibrary): Boolean =
                !interop.isNull(value)
        }
    }

    /**
     * Whether [value] is an instance of the expected meta object carrying exactly the expected number of fields.
     *
     * A nullary tag is a singleton with no array elements, so the arity check is skipped for it —
     * requiring an element count would reject every such tag.
     */
    @Operation
    @ConstantOperand(type = Any::class)
    @ConstantOperand(type = Int::class)
    class MatchesTag {
        companion object {
            @JvmStatic
            @Specialization
            fun matches(
                expectedMeta: Any,
                arity: Int,
                value: Any,
                @CachedLibrary(limit = "3") interop: InteropLibrary,
            ): Boolean {
                if (!interop.hasMetaObject(value)) return false
                if (interop.getMetaObject(value) !== expectedMeta) return false
                if (arity == 0) return true
                if (!interop.hasArrayElements(value)) return false
                return interop.getArraySize(value) == arity.toLong()
            }
        }
    }

    @Operation
    @ConstantOperand(type = Int::class)
    class TagField {
        companion object {
            @JvmStatic
            @Specialization
            fun read(index: Int, value: Any, @CachedLibrary(limit = "3") interop: InteropLibrary): Any? =
                interop.readArrayElement(value, index.toLong())
        }
    }

    @Operation
    class NoMatch {
        companion object {
            @JvmStatic
            @Specialization
            fun noMatch(scrutinee: Any, @Bind node: Node): Any =
                throw incorrect("No matching case branch for: $scrutinee", node)
        }
    }

    /**
     * Narrows a caught exception to the [Anomaly] the `catch` branches match against.
     *
     * A host exception is wrapped; anything the interop library does not recognise as an
     * exception is rethrown, so `catch` never swallows what it cannot describe.
     */
    @Operation
    class ToAnomaly {
        companion object {
            @JvmStatic
            @Specialization
            fun anomaly(ex: Anomaly): Any = ex

            @JvmStatic
            @Fallback
            @TruffleBoundary
            fun hostException(ex: Any): Any {
                val truffleEx = ex as AbstractTruffleException
                val uncached = InteropLibrary.getUncached()
                if (!uncached.isException(truffleEx)) throw truffleEx

                val message =
                    if (uncached.hasExceptionMessage(truffleEx)) uncached.getExceptionMessage(truffleEx).toString()
                    else "Host exception"

                return when (uncached.getExceptionType(truffleEx)) {
                    ExceptionType.INTERRUPT -> interrupted(message, truffleEx)
                    else -> host(message, truffleEx)
                }
            }
        }
    }

    @Operation
    class Rethrow {
        companion object {
            @JvmStatic
            @Specialization
            fun rethrow(anomaly: Anomaly): Any = throw anomaly
        }
    }

    @Operation
    @ConstantOperand(type = String::class)
    class Unsupported {
        companion object {
            @JvmStatic
            @Specialization
            @TruffleBoundary
            fun unsupported(what: String, @Bind node: Node): Any =
                throw incorrect("Not yet supported: $what", node)
        }
    }
}

@ExplodeLoop
private fun Array<Any?>.toValueList(): List<Any> {
    val res = ArrayList<Any>(size)
    for (el in this) res.add(el!!)
    return res
}
