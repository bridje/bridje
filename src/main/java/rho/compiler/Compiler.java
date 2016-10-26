package rho.compiler;

import org.pcollections.HashTreePSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.Panic;
import rho.analyser.ValueExpr;
import rho.analyser.ValueExprVisitor;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.types.Type;
import rho.types.TypeChecker;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static rho.Panic.panic;
import static rho.Util.setOf;
import static rho.Util.vectorOf;
import static rho.compiler.AccessFlag.*;
import static rho.compiler.ClassDefiner.defineClass;
import static rho.compiler.Instruction.MethodInvoke.INVOKE_STATIC;
import static rho.compiler.Instruction.SimpleInstruction.ARETURN;
import static rho.compiler.Instruction.*;
import static rho.compiler.NewClass.newClass;
import static rho.compiler.NewMethod.newMethod;

public class Compiler {

    static CompileResult compileValue(ValueExpr expr) {
        return expr.accept(new ValueExprVisitor<CompileResult>() {
            @Override
            public CompileResult visit(ValueExpr.BoolExpr expr) {
                return new CompileResult(vectorOf(loadBool(expr.value)), setOf());
            }

            @Override
            public CompileResult visit(ValueExpr.StringExpr expr) {
                return new CompileResult(vectorOf(loadObject(expr.string)), setOf());
            }

            @Override
            public CompileResult visit(ValueExpr.IntExpr expr) {
                return new CompileResult(
                    vectorOf(
                        loadObject(expr.num),
                        methodCall(Long.class, INVOKE_STATIC, "valueOf", Long.class, vectorOf(Long.TYPE))),
                    setOf());
            }

            @Override
            public CompileResult visit(ValueExpr.VectorExpr expr) {
                List<PVector<Instruction>> instructions = new LinkedList<>();
                Set<NewClass> newClasses = new HashSet<>();

                for (ValueExpr innerExpr : expr.exprs) {
                    CompileResult compileResult = innerExpr.accept(this);
                    instructions.add(compileResult.instructions);
                    newClasses.addAll(compileResult.newClasses);
                }

                return new CompileResult(vectorOf(Instruction.vectorOf(Object.class, TreePVector.from(instructions))), HashTreePSet.from(newClasses));
            }

            @Override
            public CompileResult visit(ValueExpr.SetExpr expr) {
                List<PVector<Instruction>> instructions = new LinkedList<>();
                Set<NewClass> newClasses = new HashSet<>();

                for (ValueExpr innerExpr : expr.exprs) {
                    CompileResult compileResult = innerExpr.accept(this);
                    instructions.add(compileResult.instructions);
                    newClasses.addAll(compileResult.newClasses);
                }

                return new CompileResult(vectorOf(Instruction.setOf(Object.class, TreePVector.from(instructions))), HashTreePSet.from(newClasses));
            }

            @Override
            public CompileResult visit(ValueExpr.CallExpr expr) {
                List<PVector<Instruction>> paramInstructions = new LinkedList<>();
                Set<NewClass> newClasses = new HashSet<>();

                for (ValueExpr param : expr.params) {
                    CompileResult compileResult = param.accept(this);
                    paramInstructions.add(compileResult.instructions);

                }

                return new CompileResult(vectorOf(varCall(expr.var, TreePVector.from(paramInstructions))), HashTreePSet.from(newClasses));
            }
        });
    }

    private static Object evalInstructions(Env env, PVector<Instruction> instructions) {
        try {
            return
                defineClass(env,
                    newClass("user$$eval")
                        .withMethod(
                            newMethod("$$eval", Object.class, vectorOf(), instructions)
                                .withFlags(setOf(PUBLIC, FINAL, STATIC))))
                    .getDeclaredMethod("$$eval")
                    .invoke(null);
        } catch (Panic e) {
            throw e;
        } catch (InvocationTargetException e) {
            try {
                throw e.getCause();
            } catch (Panic p) {
                throw p;
            } catch (Throwable t) {
                throw panic(t, "Error evaluating $$eval method.");
            }
        } catch (Exception e) {
            throw panic(e, "Can't execute $$eval method.");
        }
    }

    static EvalResult evalValue(Env env, ValueExpr expr) {
        Type type = TypeChecker.type(env, expr);

        CompileResult compileResult = compileValue(expr);

        for (NewClass newClass : compileResult.newClasses) {
            defineClass(env, newClass);
        }

        return new EvalResult(env, type, evalInstructions(env, compileResult.instructions.plus(ARETURN)));
    }
}
