package bridje.types;

import bridje.analyser.Expr;
import bridje.analyser.ExprVisitor;
import bridje.analyser.LocalVar;
import bridje.runtime.Env;
import org.pcollections.*;

import java.util.Optional;

import static bridje.Util.*;
import static bridje.types.CTypeChecker.MonomorphicEnv.typeEquations;
import static bridje.types.CTypeChecker.MonomorphicEnv.union;
import static bridje.types.Type.SetType.setType;
import static bridje.types.Type.SimpleType.*;
import static bridje.types.Type.VectorType.vectorType;

public class CTypeChecker {

    static class MonomorphicEnv {
        final PMap<LocalVar, Type> typings;

        static final MonomorphicEnv EMPTY = new MonomorphicEnv(Empty.map());

        MonomorphicEnv(PMap<LocalVar, Type> typings) {
            this.typings = typings;
        }

        MonomorphicEnv minusAll(PCollection<LocalVar> localVars) {
            return new MonomorphicEnv(typings.minusAll(localVars));
        }

        MonomorphicEnv apply(TypeMapping mapping) {
            throw new UnsupportedOperationException();
        }

        static MonomorphicEnv union(PCollection<MonomorphicEnv> envs) {
            throw new UnsupportedOperationException();
        }

        static PSet<TypeEquation> typeEquations(PCollection<MonomorphicEnv> envs) {
            PMap<LocalVar, Type.TypeVar> localVarTypeVars = envs.stream()
                .flatMap(env -> env.typings.keySet().stream())
                .collect(toPMap(lv -> lv, lv -> new Type.TypeVar()));

            return envs.stream()
                .flatMap(env -> env.typings.entrySet().stream())
                .map(e -> new TypeEquation(localVarTypeVars.get(e.getKey()), e.getValue()))
                .collect(toPSet());
        }
    }

    static class TypeMapping {
        final PMap<Type.TypeVar, Type> mapping;

        TypeMapping(PMap<Type.TypeVar, Type> mapping) {
            this.mapping = mapping;
        }
    }

    static class TypeEquation {
        final Type left, right;

        TypeEquation(Type left, Type right) {
            this.left = left;
            this.right = right;
        }
    }

    static TypeMapping unify(PCollection<TypeEquation> typeEquations) {
        throw new UnsupportedOperationException();
    }


    static class TypeResult {
        final MonomorphicEnv monomorphicEnv;
        final Expr<Type> expr;

        public TypeResult(Expr<Type> typedExpr) {
            this.monomorphicEnv = MonomorphicEnv.EMPTY;
            this.expr = typedExpr;
        }

        TypeResult(MonomorphicEnv monomorphicEnv, Expr<Type> typedExpr) {
            this.monomorphicEnv = monomorphicEnv;
            this.expr = typedExpr;
        }
    }

    static TypeResult typeExpr0(Env env, Expr<? extends Void> expr) {
        return expr.accept(new ExprVisitor<Void, TypeResult>() {
            @Override
            public TypeResult visit(Expr.BoolExpr<? extends Void> expr) {
                return new TypeResult(new Expr.BoolExpr<>(expr.range, BOOL_TYPE, expr.value));
            }

            @Override
            public TypeResult visit(Expr.StringExpr<? extends Void> expr) {
                return new TypeResult(new Expr.StringExpr<>(expr.range, STRING_TYPE, expr.string));
            }

            @Override
            public TypeResult visit(Expr.IntExpr<? extends Void> expr) {
                return new TypeResult(new Expr.IntExpr<>(expr.range, INT_TYPE, expr.num));
            }

            @Override
            public TypeResult visit(Expr.VectorExpr<? extends Void> expr) {
                PVector<TypeResult> typeResults = expr.exprs.stream().map(el -> typeExpr0(env, expr)).collect(toPVector());
                Type.TypeVar elemType = new Type.TypeVar();
                PCollection<MonomorphicEnv> envs = typeResults.stream().map(r -> r.monomorphicEnv).collect(toPVector());

                TypeMapping mapping = unify(typeEquations(envs)
                    .plusAll(typeResults.stream().map(r -> new TypeEquation(elemType, r.expr.type)).collect(toPVector())));

                return new TypeResult(union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    new Expr.VectorExpr<>(expr.range, vectorType(elemType),
                        typeResults.stream().map(r -> r.expr).collect(toPVector())));
            }

            @Override
            public TypeResult visit(Expr.SetExpr<? extends Void> expr) {
                PVector<TypeResult> typeResults = expr.exprs.stream().map(el -> typeExpr0(env, expr)).collect(toPVector());
                Type.TypeVar elemType = new Type.TypeVar();
                PCollection<MonomorphicEnv> envs = typeResults.stream().map(r -> r.monomorphicEnv).collect(toPVector());

                TypeMapping mapping = unify(typeEquations(envs)
                    .plusAll(typeResults.stream().map(r -> new TypeEquation(elemType, r.expr.type)).collect(toPVector())));

                return new TypeResult(union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    new Expr.SetExpr<>(expr.range, setType(elemType),
                        typeResults.stream().map(r -> r.expr).collect(toPVector())));
            }

            @Override
            public TypeResult visit(Expr.CallExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeResult visit(Expr.VarCallExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeResult visit(Expr.LetExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeResult visit(Expr.IfExpr<? extends Void> expr) {
                TypeResult typedTest = typeExpr0(env, expr.testExpr);
                TypeResult typedThen = typeExpr0(env, expr.thenExpr);
                TypeResult typedElse = typeExpr0(env, expr.elseExpr);

                Type.TypeVar resultType = new Type.TypeVar();

                PCollection<MonomorphicEnv> envs = setOf(typedTest.monomorphicEnv, typedThen.monomorphicEnv, typedElse.monomorphicEnv);

                TypeMapping mapping = unify(
                    typeEquations(envs)
                        .plus(new TypeEquation(resultType, typedThen.expr.type))
                        .plus(new TypeEquation(resultType, typedElse.expr.type))
                        .plus(new TypeEquation(typedTest.expr.type, BOOL_TYPE)));

                return new TypeResult(union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    new Expr.IfExpr<Type>(expr.range, mapping.mapping.get(resultType), typedTest.expr, typedThen.expr, typedElse.expr));
            }

            @Override
            public TypeResult visit(Expr.LocalVarExpr<? extends Void> expr) {
                Type.TypeVar typeVar = new Type.TypeVar();

                return new TypeResult(new MonomorphicEnv(HashTreePMap.singleton(expr.localVar, typeVar)), new Expr.LocalVarExpr<Type>(expr.range, typeVar, expr.localVar));
            }

            @Override
            public TypeResult visit(Expr.GlobalVarExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeResult visit(Expr.FnExpr<? extends Void> expr) {
                TypeResult typedBody = typeExpr0(env, expr.body);
                return new TypeResult(typedBody.monomorphicEnv.minusAll(expr.params),
                    new Expr.FnExpr<>(expr.range,
                        new Type.FnType(
                            expr.params.stream().map(p -> Optional.ofNullable(typedBody.monomorphicEnv.typings.get(p)).orElseGet(Type.TypeVar::new)).collect(toPVector()),
                            typedBody.expr.type
                        ),
                        expr.params,
                        typedBody.expr));
            }

            @Override
            public TypeResult visit(Expr.DefExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeResult visit(Expr.TypeDefExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeResult visit(Expr.DefDataExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr<Type> typeExpr(Env env, Expr<Void> expr) {
        return typeExpr0(env, expr).expr;
    }

}
