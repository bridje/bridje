package bridje.types;

import bridje.analyser.Expr;
import bridje.analyser.ExprVisitor;
import org.pcollections.PCollection;
import org.pcollections.PVector;

import java.util.Optional;

import static bridje.Util.setOf;
import static bridje.Util.toPVector;
import static bridje.types.MonomorphicEnv.typeEquations;
import static bridje.types.MonomorphicEnv.union;
import static bridje.types.Type.SetType.setType;
import static bridje.types.Type.SimpleType.*;
import static bridje.types.Type.VectorType.vectorType;
import static bridje.types.TypeEquation.unify;

public class TypeChecker {

    static TypeResult typeExpr0(Expr<? extends Void> expr) {
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
                PVector<TypeResult> typeResults = expr.exprs.stream().map(el -> typeExpr0(el)).collect(toPVector());
                Type.TypeVar elemType = new Type.TypeVar();
                PCollection<MonomorphicEnv> envs = typeResults.stream().map(r -> r.monomorphicEnv).collect(toPVector());

                TypeMapping mapping = unify(typeEquations(envs)
                    .plusAll(typeResults.stream().map(r -> new TypeEquation(elemType, r.expr.type)).collect(toPVector())));

                return new TypeResult(union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    new Expr.VectorExpr<>(expr.range, vectorType(elemType.apply(mapping)),
                        typeResults.stream().map(r -> mapping.applyTo(r.expr)).collect(toPVector())));
            }

            @Override
            public TypeResult visit(Expr.SetExpr<? extends Void> expr) {
                PVector<TypeResult> typeResults = expr.exprs.stream().map(el -> typeExpr0(el)).collect(toPVector());
                Type.TypeVar elemType = new Type.TypeVar();
                PCollection<MonomorphicEnv> envs = typeResults.stream().map(r -> r.monomorphicEnv).collect(toPVector());

                TypeMapping mapping = unify(typeEquations(envs)
                    .plusAll(typeResults.stream().map(r -> new TypeEquation(elemType, r.expr.type)).collect(toPVector())));

                return new TypeResult(
                    union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    new Expr.SetExpr<>(expr.range, setType(elemType.apply(mapping)),
                        typeResults.stream().map(r -> mapping.applyTo(r.expr)).collect(toPVector())));
            }

            @Override
            public TypeResult visit(Expr.CallExpr<? extends Void> expr) {
                TypeVar resultType = new TypeVar();
                TypeResult callResult = typeExpr0(expr.exprs.get(0));
                PVector<TypeResult> paramTypeResults = expr.exprs.minus(0).stream().map(e -> typeExpr0(e)).collect(toPVector());

                PVector<MonomorphicEnv> envs = paramTypeResults.stream()
                    .map(tr -> tr.monomorphicEnv)
                    .collect(toPVector())
                    .plus(callResult.monomorphicEnv);

                TypeMapping mapping = unify(
                    typeEquations(envs)
                        .plus(new TypeEquation(callResult.expr.type, new FnType(paramTypeResults.stream().map(tr -> tr.expr.type).collect(toPVector()), resultType))));

                return new TypeResult(
                    union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    mapping.applyTo(
                        new Expr.CallExpr<>(expr.range, resultType,
                            paramTypeResults.stream()
                                .map(tr -> tr.expr)
                                .collect(toPVector())
                                .plus(0, callResult.expr))));
            }

            @Override
            public TypeResult visit(Expr.VarCallExpr<? extends Void> expr) {
                TypeVar resultType = new TypeVar();
                Type varType = expr.var.inferredType.instantiate();

                PVector<TypeResult> paramTypeResults = expr.params.stream().map(e -> typeExpr0(e)).collect(toPVector());

                PVector<MonomorphicEnv> envs = paramTypeResults.stream()
                    .map(tr -> tr.monomorphicEnv)
                    .collect(toPVector());

                TypeMapping mapping = unify(
                    typeEquations(envs)
                        .plus(new TypeEquation(varType, new FnType(paramTypeResults.stream().map(tr -> tr.expr.type).collect(toPVector()), resultType))));

                return new TypeResult(
                    union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    mapping.applyTo(
                        new Expr.VarCallExpr<>(expr.range, resultType,
                            expr.var,
                            paramTypeResults.stream()
                                .map(tr -> tr.expr)
                                .collect(toPVector()))));
            }

            @Override
            public TypeResult visit(Expr.LetExpr<? extends Void> expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeResult visit(Expr.IfExpr<? extends Void> expr) {
                TypeResult typedTest = typeExpr0(expr.testExpr);
                TypeResult typedThen = typeExpr0(expr.thenExpr);
                TypeResult typedElse = typeExpr0(expr.elseExpr);

                Type.TypeVar resultType = new Type.TypeVar();

                PCollection<MonomorphicEnv> envs = setOf(typedTest.monomorphicEnv, typedThen.monomorphicEnv, typedElse.monomorphicEnv);

                TypeMapping mapping = unify(
                    typeEquations(envs)
                        .plus(new TypeEquation(resultType, typedThen.expr.type))
                        .plus(new TypeEquation(resultType, typedElse.expr.type))
                        .plus(new TypeEquation(typedTest.expr.type, BOOL_TYPE)));

                return new TypeResult(union(envs.stream().map(e -> e.apply(mapping)).collect(toPVector())),
                    new Expr.IfExpr<>(expr.range, mapping.mapping.get(resultType),
                        mapping.applyTo(typedTest.expr),
                        mapping.applyTo(typedThen.expr),
                        mapping.applyTo(typedElse.expr)));
            }

            @Override
            public TypeResult visit(Expr.LocalVarExpr<? extends Void> expr) {
                Type.TypeVar typeVar = new Type.TypeVar();

                return new TypeResult(new MonomorphicEnv(expr.localVar, typeVar),
                    new Expr.LocalVarExpr<>(expr.range, typeVar, expr.localVar));
            }

            @Override
            public TypeResult visit(Expr.GlobalVarExpr<? extends Void> expr) {
                return new TypeResult(new Expr.GlobalVarExpr<>(expr.range, expr.var.inferredType.instantiate(), expr.var));
            }

            @Override
            public TypeResult visit(Expr.FnExpr<? extends Void> expr) {
                TypeResult typedBody = typeExpr0(expr.body);
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

    public static Expr<Type> typeExpr(Expr<Void> expr) {
        return typeExpr0(expr).expr;
    }

}
