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
            nsEnv += DefVar(QSymbol.intern(nsEnv.ns, expr.sym), expr.type, emitter.evalValueExpr(expr.expr))
        }

        private fun evalTypeDef(typeDef: TypeDefExpr) {
            if (nsEnv.vars[typeDef.sym] != null) {
                TODO("sym already exists in NS")
            }

            nsEnv += DefVar(QSymbol.intern(nsEnv.ns, typeDef.sym), typeDef.type, null)
        }

        private fun evalDefData(defDataExpr: DefDataExpr) {
            if (defDataExpr.sym != null && defDataExpr.constructors.isNotEmpty()) {
                val dataType = DataType(QSymbol.intern(nsEnv.ns, defDataExpr.sym), defDataExpr.typeParams, defDataExpr.constructors.map(DefDataConstructorExpr::sym))

                nsEnv += dataType

                defDataExpr.constructors.forEach { constructor ->
                    val sym = QSymbol.intern(nsEnv.ns, constructor.sym)
                    val dataTypeConstructor = DataTypeConstructor(sym, dataType, constructor.params)
                    nsEnv += ConstructorVar(dataTypeConstructor, emitter.emitConstructor(dataTypeConstructor))
                }
            }

            defDataExpr.attributes.forEach {
                // TODO emit attribute fn
                nsEnv += AttributeVar(it, null)
            }
        }

        private fun evalDefx(expr: DefxExpr) {
            TODO()
        }

        private fun formEvaluator(it: AnalyserState) {
            val analyser = ActionExprAnalyser(env, nsEnv)

            it.nested(ListForm::forms) {
                when (it.expectForm<SymbolForm>().sym) {
                    DO -> it.varargs(::formEvaluator)

                    DEF_DATA -> evalDefData(analyser.defDataAnalyser(it))
                    TYPE_DEF -> evalTypeDef(analyser.typeDefAnalyser(it))
                    DEF -> evalDef(analyser.defAnalyser(it))
                    DEFX -> evalDefx(analyser.defxAnalyser(it))

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