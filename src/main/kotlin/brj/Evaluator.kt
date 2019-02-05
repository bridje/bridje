package brj

import brj.QSymbol.Companion.mkQSym

internal interface Emitter {
    fun evalValueExpr(expr: ValueExpr): Any
    fun emitJavaImport(javaImport: JavaImport): Any
    fun emitRecordKey(recordKey: RecordKey): Any
    fun emitVariant(variantKey: VariantKey): Any
}

internal class Evaluator(var env: Env, private val emitter: Emitter) {

    inner class NSEvaluator(var nsEnv: NSEnv) {
        private fun evalJavaImports() {
            nsEnv.javaImports.values.forEach { import ->
                nsEnv += JavaImportVar(import, emitter.emitJavaImport(import))
            }

            env += nsEnv
        }

        private fun evalDef(expr: DefExpr) {
            nsEnv += DefVar(mkQSym(nsEnv.ns, expr.sym), expr.type, emitter.evalValueExpr(expr.expr))
        }

        private fun evalDecl(decl: DeclExpr) {
            when (decl) {
                is VarDeclExpr -> nsEnv += decl.defVar
                is PolyVarDeclExpr -> TODO()
                is TypeAliasDeclExpr -> nsEnv += decl.typeAlias
                is RecordKeyDeclExpr -> nsEnv += RecordKeyVar(decl.recordKey, emitter.emitRecordKey(decl.recordKey))
                is VariantKeyDeclExpr -> nsEnv += VariantKeyVar(decl.variantKey, emitter.emitVariant(decl.variantKey))
            }
        }

        private fun formEvaluator(it: AnalyserState) {
            val analyser = ActionExprAnalyser(env, nsEnv)

            it.nested(ListForm::forms) {
                when (it.expectForm<SymbolForm>().sym) {
                    DO -> it.varargs(::formEvaluator)
                    DECL -> evalDecl(analyser.declAnalyser(it))
                    DEF -> evalDef(analyser.defAnalyser(it))

                    else -> TODO()
                }
            }

            env += nsEnv
        }

        fun evalNS(forms: List<Form>) {
            evalJavaImports()
            AnalyserState(forms).varargs(::formEvaluator)
        }
    }

    fun evalNS(nsFile: NSFile) = NSEvaluator(nsFile.nsEnv).evalNS(nsFile.forms)
}