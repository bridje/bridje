@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj.analyser

import brj.reader.ListForm
import brj.reader.RecordForm
import brj.reader.SetForm
import brj.reader.SymbolForm
import brj.runtime.QSymbol
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.mkSym
import brj.runtime.SymbolKind.TYPE_ALIAS_SYM
import brj.runtime.SymbolKind.VAR_SYM
import brj.types.MonoType
import kotlin.reflect.KClass

private val NS = mkSym("ns")
private val REFERS = mkSym(":refers")
private val ALIASES = mkSym(":aliases")
private val JAVA = mkSym("java")

internal sealed class Alias {
    abstract val ns: Symbol
}

internal data class BridjeAlias(override val ns: Symbol) : Alias()
internal data class JavaInteropDecl(val sym: Symbol, val type: MonoType)
internal data class JavaAlias(override val ns: Symbol, val clazz: KClass<*>, val decls: Map<Symbol, JavaInteropDecl>) : Alias()

internal data class NSHeader(val ns: Symbol,
                             val refers: Map<Symbol, QSymbol> = emptyMap(),
                             val aliases: Map<Symbol, Alias> = emptyMap()) {

    val deps by lazy {
        refers.values.mapTo(mutableSetOf()) { it.ns } +
            aliases.values.mapNotNull {
                when (it) {
                    is BridjeAlias -> it.ns
                    is JavaAlias -> null
                }
            }
    }

    companion object {
        private val exprAnalyser = ExprAnalyser(Resolver.NSResolver())

        private fun refersAnalyser(it: ParserState) =
            it.varargs {
                val nsSym = it.expectForm<SymbolForm>().sym
                it.nested(SetForm::forms) {
                    it.varargs {
                        val sym = it.expectForm<SymbolForm>().sym
                        sym to QSymbol.mkQSym(nsSym, sym)
                    }
                }
            }.flatten().toMap()

        private fun aliasesAnalyser(it: ParserState, ns: Symbol) =
            it.varargs {
                it.or({
                    it.maybe { it.expectSym(VAR_SYM) }?.let { sym -> sym to BridjeAlias(it.expectSym(VAR_SYM)) }
                }, {
                    it.maybe { it.expectSym(TYPE_ALIAS_SYM) }?.let { sym ->
                        it.nested(ListForm::forms) {
                            it.expectSym(JAVA)
                            sym to JavaAlias(
                                mkSym("$ns\$${sym}"),
                                Class.forName(it.expectSym(VAR_SYM).baseStr).kotlin,
                                it.varargs {
                                    val expr = exprAnalyser.declAnalyser(it) as VarDeclExpr
                                    expr.sym to JavaInteropDecl(expr.sym, expr.type.monoType)
                                }.toMap())
                        }
                    }
                }) ?: TODO()
            }.toMap()

        internal fun nsHeaderParser(it: ParserState): NSHeader? =
            it.maybe {
                it.nested(ListForm::forms) {
                    it.expectSym(NS)
                    it
                }
            }?.let {
                val ns = it.expectSym(VAR_SYM)

                var nsHeader = NSHeader(ns)

                if (it.forms.isNotEmpty()) {
                    it.nested(RecordForm::forms) {
                        it.varargs {
                            val sym = it.expectForm<SymbolForm>().sym
                            nsHeader = when (sym) {
                                REFERS -> {
                                    nsHeader.copy(refers = it.nested(RecordForm::forms, ::refersAnalyser))
                                }

                                ALIASES -> {
                                    nsHeader.copy(aliases = it.nested(RecordForm::forms) { aliasesAnalyser(it, ns) })
                                }

                                else -> TODO()
                            }
                        }
                    }
                }

                it.expectEnd()
                nsHeader
            }
    }
}

