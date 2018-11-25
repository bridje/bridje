package brj

internal interface Emitter {
    fun evalValueExpr(expr: ValueExpr): Any
    fun emitJavaImport(javaImport: JavaImport): Any
    fun emitConstructor(dataTypeConstructor: DataTypeConstructor): Any
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
            val expectedType = nsEnv.vars[expr.sym]?.type

            if (expectedType != null && !(expr.type.matches(expectedType))) {
                TODO()
            }

            nsEnv += GlobalVar(expr.sym, expectedType ?: expr.type, emitter.evalValueExpr(expr.expr))
        }

        private fun evalTypeDef(typeDef: TypeDefExpr) {
            if (nsEnv.vars[typeDef.sym] != null) {
                TODO("sym already exists in NS")
            }

            nsEnv += GlobalVar(typeDef.sym, typeDef.type, null)
        }

        private fun evalDefData(defDataExpr: DefDataExpr) {
            val dataType = DataType(QSymbol.intern(nsEnv.ns, defDataExpr.sym), defDataExpr.typeParams, defDataExpr.constructors.map(DefDataConstructorExpr::sym))

            nsEnv += dataType

            defDataExpr.constructors.forEach { constructor ->
                val sym = QSymbol.intern(nsEnv.ns, constructor.sym)
                val dataTypeConstructor = DataTypeConstructor(sym, dataType, constructor.params)
                nsEnv += ConstructorVar(dataTypeConstructor, emitter.emitConstructor(dataTypeConstructor))
            }
        }

        private fun formEvaluator(it: AnalyserState) {
            val analyser = ActionExprAnalyser(env, nsEnv)

            it.nested(ListForm::forms) {
                when (it.expectForm<SymbolForm>().sym) {
                    DO -> it.varargs(::formEvaluator)

                    DEF_DATA -> evalDefData(analyser.defDataAnalyser(it))
                    TYPE_DEF -> evalTypeDef(analyser.typeDefAnalyser(it))
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