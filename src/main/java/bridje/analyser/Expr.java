package bridje.analyser;

import bridje.reader.Range;
import bridje.runtime.*;
import bridje.types.Type;
import org.pcollections.PMap;
import org.pcollections.PVector;

import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.function.Function;

import static bridje.Util.toPVector;
import static java.util.stream.Collectors.joining;

public abstract class Expr<ET> {

    public final Range range;
    public final ET type;

    Expr(Range range, ET type) {
        this.range = range;
        this.type = type;
    }

    public abstract <V> V accept(ExprVisitor<? super ET, V> visitor);

    public abstract <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn);

    public static final class BoolExpr<ET> extends Expr<ET> {
        public final boolean value;

        public BoolExpr(Range range, ET type, boolean value) {
            super(range, type);
            this.value = value;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new BoolExpr<>(range, fn.apply(type), value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoolExpr boolExpr = (BoolExpr) o;
            return value == boolExpr.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return String.format("(BoolExpr %s)", value);
        }
    }

    public static final class StringExpr<ET> extends Expr<ET> {

        public final String string;

        public StringExpr(Range range, ET type, String string) {
            super(range, type);
            this.string = string;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new StringExpr<>(range, fn.apply(type), string);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringExpr that = (StringExpr) o;
            return Objects.equals(string, that.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(string);
        }

        @Override
        public String toString() {
            return String.format("(StringExpr \"%s\")", string);
        }
    }

    public static final class IntExpr<ET> extends Expr<ET> {

        public final long num;

        public IntExpr(Range range, ET type, long num) {
            super(range, type);
            this.num = num;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new IntExpr<>(range, fn.apply(type), num);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntExpr intExpr = (IntExpr) o;
            return num == intExpr.num;
        }

        @Override
        public int hashCode() {
            return Objects.hash(num);
        }

        @Override
        public String toString() {
            return String.format("(IntExpr %d)", num);
        }
    }

    public static final class VectorExpr<ET> extends Expr<ET> {
        public final PVector<Expr<ET>> exprs;

        public VectorExpr(Range range, ET type, PVector<Expr<ET>> exprs) {
            super(range, type);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new VectorExpr<>(range, fn.apply(type), exprs.stream().map(e -> e.fmapType(fn)).collect(toPVector()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VectorExpr that = (VectorExpr) o;
            return Objects.equals(exprs, that.exprs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exprs);
        }

        @Override
        public String toString() {
            return String.format("(VectorExpr %s)", exprs.stream().map(Object::toString).collect(joining(", ")));
        }
    }

    public static final class SetExpr<ET> extends Expr<ET> {
        public final PVector<Expr<ET>> exprs;

        public SetExpr(Range range, ET type, PVector<Expr<ET>> exprs) {
            super(range, type);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new SetExpr<>(range, fn.apply(type), exprs.stream().map(e -> e.fmapType(fn)).collect(toPVector()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SetExpr that = (SetExpr) o;
            return Objects.equals(exprs, that.exprs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exprs);
        }

        @Override
        public String toString() {
            return String.format("(SetExpr %s)", exprs.stream().map(Object::toString).collect(joining(", ")));
        }
    }

    public static final class MapExpr<ET> extends Expr<ET> {

        public static final class MapEntryExpr<ET> {
            public final Range range;
            public final Expr<ET> key, value;

            public MapEntryExpr(Range range, Expr<ET> key, Expr<ET> value) {
                this.range = range;
                this.key = key;
                this.value = value;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                MapEntryExpr<?> that = (MapEntryExpr<?>) o;
                return Objects.equals(key, that.key) &&
                    Objects.equals(value, that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(key, value);
            }

            public <ET_> MapEntryExpr<ET_> fmapType(Function<ET, ET_> fn) {
                return new MapEntryExpr<>(range, key.fmapType(fn), value.fmapType(fn));
            }
        }

        public final PVector<MapEntryExpr<ET>> entries;

        public MapExpr(Range range, ET type, PVector<MapEntryExpr<ET>> entries) {
            super(range, type);
            this.entries = entries;
        }

        @Override
        public <V> V accept(ExprVisitor<? super ET, V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new MapExpr<>(range, fn.apply(type), entries.stream().map(e -> e.fmapType(fn)).collect(toPVector()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MapExpr<?> mapExpr = (MapExpr<?>) o;
            return Objects.equals(entries, mapExpr.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries);
        }

        @Override
        public String toString() {
            return String.format("(MapExpr ^{%s})", entries.stream().map(e -> String.format("%s %s", e.key, e.value)).collect(joining(", ")));
        }
    }

    public static final class CallExpr<ET> extends Expr<ET> {

        public final PVector<Expr<ET>> exprs;

        public CallExpr(Range range, ET type, PVector<Expr<ET>> exprs) {
            super(range, type);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new CallExpr<>(range, fn.apply(type), exprs.stream().map(e -> e.fmapType(fn)).collect(toPVector()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CallExpr callExpr = (CallExpr) o;
            return Objects.equals(exprs, callExpr.exprs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exprs);
        }

        @Override
        public String toString() {
            return String.format("(CallExpr (%s))", exprs.stream().map(Object::toString).collect(joining(" ")));
        }
    }

    public static final class VarCallExpr<ET> extends Expr<ET> {

        public final Var var;
        public final PVector<Expr<ET>> params;

        public VarCallExpr(Range range, ET type, Var var, PVector<Expr<ET>> params) {
            super(range, type);
            this.var = var;
            this.params = params;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new VarCallExpr<>(range, fn.apply(type), var, params.stream().map(p -> p.fmapType(fn)).collect(toPVector()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VarCallExpr varCallExpr = (VarCallExpr) o;
            return Objects.equals(var, varCallExpr.var) &&
                Objects.equals(params, varCallExpr.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(var, params);
        }

        @Override
        public String toString() {
            return String.format("(VarCallExpr (%s %s))", var, params.stream().map(Object::toString).collect(joining(" ")));
        }
    }

    public static final class LetExpr<ET> extends Expr<ET> {

        public static final class LetBinding<ET> {
            public final LocalVar localVar;
            public final Expr<ET> expr;

            public LetBinding(LocalVar localVar, Expr<ET> expr) {
                this.localVar = localVar;
                this.expr = expr;
            }

            public <ET_> LetBinding<ET_> fmap(Function<ET, ET_> fn) {
                return new LetBinding<>(localVar, expr.fmapType(fn));
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                LetBinding that = (LetBinding) o;
                return Objects.equals(localVar, that.localVar) &&
                    Objects.equals(expr, that.expr);
            }

            @Override
            public int hashCode() {
                return Objects.hash(localVar, expr);
            }

            @Override
            public String toString() {
                return String.format("(LetBinding %s %s)", localVar, expr);
            }
        }

        public final PVector<LetBinding<ET>> bindings;
        public final Expr<ET> body;

        public LetExpr(Range range, ET type, PVector<LetBinding<ET>> bindings, Expr<ET> body) {
            super(range, type);
            this.bindings = bindings;
            this.body = body;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new LetExpr<>(range, fn.apply(type), bindings.stream().map(b -> b.fmap(fn)).collect(toPVector()), body.fmapType(fn));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LetExpr letExpr = (LetExpr) o;
            return Objects.equals(bindings, letExpr.bindings) &&
                Objects.equals(body, letExpr.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bindings, body);
        }

        @Override
        public String toString() {
            return String.format("(LetExpr [%s] %s)", bindings.stream().map(Object::toString).collect(joining(" ")), body);
        }
    }

    public static final class IfExpr<ET> extends Expr<ET> {

        public final Expr<ET> testExpr;
        public final Expr<ET> thenExpr;
        public final Expr<ET> elseExpr;

        public IfExpr(Range range, ET type, Expr<ET> testExpr, Expr<ET> thenExpr, Expr<ET> elseExpr) {
            super(range, type);
            this.testExpr = testExpr;
            this.thenExpr = thenExpr;
            this.elseExpr = elseExpr;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new IfExpr<>(range, fn.apply(type), testExpr.fmapType(fn), thenExpr.fmapType(fn), elseExpr.fmapType(fn));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IfExpr ifExpr = (IfExpr) o;
            return Objects.equals(testExpr, ifExpr.testExpr) &&
                Objects.equals(thenExpr, ifExpr.thenExpr) &&
                Objects.equals(elseExpr, ifExpr.elseExpr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(testExpr, thenExpr, elseExpr);
        }

        @Override
        public String toString() {
            return String.format("(IfExpr %s %s %s)", testExpr, thenExpr, elseExpr);
        }
    }

    public static final class LocalVarExpr<ET> extends Expr<ET> {

        public final LocalVar localVar;

        public LocalVarExpr(Range range, ET type, LocalVar localVar) {
            super(range, type);
            this.localVar = localVar;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new LocalVarExpr<>(range, fn.apply(type), localVar);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LocalVarExpr that = (LocalVarExpr) o;
            return Objects.equals(localVar, that.localVar);
        }

        @Override
        public int hashCode() {
            return Objects.hash(localVar);
        }

        @Override
        public String toString() {
            return String.format("(LocalVarExpr %s)", localVar);
        }
    }

    public static final class GlobalVarExpr<ET> extends Expr<ET> {

        public final Var var;

        public GlobalVarExpr(Range range, ET type, Var var) {
            super(range, type);
            this.var = var;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new GlobalVarExpr<>(range, fn.apply(type), var);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GlobalVarExpr that = (GlobalVarExpr) o;
            return Objects.equals(var, that.var);
        }

        @Override
        public int hashCode() {
            return Objects.hash(var);
        }

        @Override
        public String toString() {
            return String.format("(GlobalVarExpr %s)", var);
        }
    }

    public static final class FnExpr<ET> extends Expr<ET> {

        public final PVector<LocalVar> params;
        public final Expr<ET> body;

        public FnExpr(Range range, ET type, PVector<LocalVar> params, Expr<ET> body) {
            super(range, type);
            this.params = params;
            this.body = body;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FnExpr<?> fnExpr = (FnExpr<?>) o;
            return Objects.equals(params, fnExpr.params) &&
                Objects.equals(body, fnExpr.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(params, body);
        }

        @Override
        public String toString() {
            return String.format("(FnExpr (%s) %s)", params.stream().map(Object::toString).collect(joining(" ")), body);
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new FnExpr<>(range, fn.apply(type), params, body.fmapType(fn));
        }
    }

    public static final class NSExpr<ET> extends Expr<ET> {

        public final NS ns;
        public final PMap<Symbol, NS> aliases;
        public final PMap<Symbol, FQSymbol> refers;
        public final PMap<Symbol, Class<?>> imports;

        public NSExpr(Range range, ET type, NS ns, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers, PMap<Symbol, Class<?>> imports) {
            super(range, type);
            this.ns = ns;
            this.aliases = aliases;
            this.refers = refers;
            this.imports = imports;
        }

        @Override
        public <V> V accept(ExprVisitor<? super ET, V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new NSExpr<>(range, fn.apply(type), ns, aliases, refers, imports);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NSExpr<?> nsExpr = (NSExpr<?>) o;
            return Objects.equals(ns, nsExpr.ns) &&
                Objects.equals(aliases, nsExpr.aliases) &&
                Objects.equals(refers, nsExpr.refers) &&
                Objects.equals(imports, nsExpr.imports);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ns, aliases, refers, imports);
        }

        @Override
        public String toString() {
            return String.format("(NSExpr %s {aliases %s, refers %s, imports %s})", ns, aliases, refers, imports);
        }
    }

    public static class DefExpr<ET> extends Expr<ET> {

        public final FQSymbol sym;
        public final Expr<ET> body;

        public DefExpr(Range range, ET type, FQSymbol sym, Expr<ET> body) {
            super(range, type);
            this.sym = sym;
            this.body = body;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new DefExpr<>(range, fn.apply(type), sym, body.fmapType(fn));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefExpr defExpr = (DefExpr) o;
            return Objects.equals(sym, defExpr.sym) &&
                Objects.equals(body, defExpr.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym, body);
        }

        @Override
        public String toString() {
            return String.format("(DefExpr %s %s)", sym, body);
        }
    }

    public static class TypeDefExpr<ET> extends Expr<ET> {

        public final FQSymbol sym;
        public final Type typeDef;

        public TypeDefExpr(Range range, ET type, FQSymbol sym, Type typeDef) {
            super(range, type);
            this.sym = sym;
            this.typeDef = typeDef;
        }

        @Override
        public <T> T accept(ExprVisitor<? super ET, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new TypeDefExpr<>(range, fn.apply(type), sym, typeDef);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypeDefExpr<?> that = (TypeDefExpr<?>) o;
            return Objects.equals(sym, that.sym) &&
                Objects.equals(typeDef, that.typeDef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym, typeDef);
        }

        @Override
        public String toString() {
            return String.format("(TypeDefExpr %s %s)", sym, typeDef);
        }
    }

    public static class JavaTypeDefExpr<ET> extends Expr<ET> {
        public final MethodHandle handle;
        public final Type typeDef;

        public JavaTypeDefExpr(Range range, ET type, MethodHandle handle, Type typeDef) {
            super(range, type);
            this.handle = handle;
            this.typeDef = typeDef;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JavaTypeDefExpr<?> that = (JavaTypeDefExpr<?>) o;
            return Objects.equals(handle, that.handle) &&
                Objects.equals(typeDef, that.typeDef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(handle, typeDef);
        }

        @Override
        public <V> V accept(ExprVisitor<? super ET, V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new JavaTypeDefExpr<ET_>(range, fn.apply(type), handle, typeDef);
        }

        @Override
        public String toString() {
            return String.format("(JavaTypeDefExpr (:: %s %s))", handle, typeDef);
        }
    }

    public static class DefDataExpr<ET> extends Expr<ET> {

        public final DataType<ET> dataType;

        public DefDataExpr(Range range, ET type, DataType<ET> dataType) {
            super(range, type);
            this.dataType = dataType;
        }

        @Override
        public <V> V accept(ExprVisitor<? super ET, V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <ET_> Expr<ET_> fmapType(Function<ET, ET_> fn) {
            return new DefDataExpr<>(range, fn.apply(type), dataType.fmapType(fn));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefDataExpr<?> that = (DefDataExpr<?>) o;
            return Objects.equals(dataType, that.dataType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataType);
        }

        @Override
        public String toString() {
            return String.format("(DefDataExpr %s)", dataType);
        }
    }
}
