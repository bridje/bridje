package bridje.analyser;

import bridje.reader.Range;
import bridje.runtime.*;
import org.pcollections.PMap;
import org.pcollections.PVector;

import java.util.Objects;

import static java.util.stream.Collectors.joining;

public abstract class Expr {

    public final Range range;

    Expr(Range range) {
        this.range = range;
    }

    public abstract <V> V accept(ExprVisitor<V> visitor);

    public static final class BoolExpr extends Expr {
        public final boolean value;

        public BoolExpr(Range range, boolean value) {
            super(range);
            this.value = value;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class StringExpr extends Expr {

        public final String string;

        public StringExpr(Range range, String string) {
            super(range);
            this.string = string;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class IntExpr extends Expr {

        public final long num;

        public IntExpr(Range range, long num) {
            super(range);
            this.num = num;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class VectorExpr extends Expr {
        public final PVector<Expr> exprs;

        public VectorExpr(Range range, PVector<Expr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class SetExpr extends Expr {
        public final PVector<Expr> exprs;

        public SetExpr(Range range, PVector<Expr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class CallExpr extends Expr {

        public final PVector<Expr> exprs;

        public CallExpr(Range range, PVector<Expr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class VarCallExpr extends Expr {

        public final Var var;
        public final PVector<Expr> params;

        public VarCallExpr(Range range, Var var, PVector<Expr> params) {
            super(range);
            this.var = var;
            this.params = params;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class LetExpr extends Expr {

        public static final class LetBinding {
            public final LocalVar localVar;
            public final Expr expr;

            public LetBinding(LocalVar localVar, Expr expr) {
                this.localVar = localVar;
                this.expr = expr;
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

        public final PVector<LetBinding> bindings;
        public final Expr body;

        public LetExpr(Range range, PVector<LetBinding> bindings, Expr body) {
            super(range);
            this.bindings = bindings;
            this.body = body;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class IfExpr extends Expr {

        public final Expr testExpr;
        public final Expr thenExpr;
        public final Expr elseExpr;

        public IfExpr(Range range, Expr testExpr, Expr thenExpr, Expr elseExpr) {
            super(range);
            this.testExpr = testExpr;
            this.thenExpr = thenExpr;
            this.elseExpr = elseExpr;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class LocalVarExpr extends Expr {

        public final LocalVar localVar;

        public LocalVarExpr(Range range, LocalVar localVar) {
            super(range);
            this.localVar = localVar;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class GlobalVarExpr extends Expr {

        public final Var var;

        public GlobalVarExpr(Range range, Var var) {
            super(range);
            this.var = var;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static final class FnExpr extends Expr {

        public final PVector<LocalVar> params;
        public final Expr body;

        public FnExpr(Range range, PVector<LocalVar> params, Expr body) {
            super(range);
            this.params = params;
            this.body = body;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FnExpr fnExpr = (FnExpr) o;
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
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static final class NSExpr extends Expr {

        public final NS ns;
        public final PMap<Symbol, NS> aliases;
        public final PMap<Symbol, FQSymbol> refers;
        public final PMap<Symbol, Class<?>> imports;

        public NSExpr(Range range, NS ns, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers, PMap<Symbol, Class<?>> imports) {
            super(range);
            this.ns = ns;
            this.aliases = aliases;
            this.refers = refers;
            this.imports = imports;
        }

        @Override
        public <V> V accept(ExprVisitor<V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NSExpr nsExpr = (NSExpr) o;
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

    public static class DefExpr extends Expr {

        public final FQSymbol sym;
        public final Expr body;

        public DefExpr(Range range, FQSymbol sym, Expr body) {
            super(range);
            this.sym = sym;
            this.body = body;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visit(this);
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

    public static class DefJExpr extends Expr {
        public final FQSymbol sym;
        public final JCall jCall;

        public DefJExpr(Range range, FQSymbol sym, JCall jCall) {
            super(range);
            this.sym = sym;
            this.jCall = jCall;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefJExpr that = (DefJExpr) o;
            return Objects.equals(sym, that.sym) &&
                Objects.equals(jCall, that.jCall);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym, jCall);
        }

        @Override
        public <V> V accept(ExprVisitor<V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toString() {
            return String.format("(DefJExpr %s (:: %s %s))", sym, jCall);
        }
    }

    public static class DefDataExpr extends Expr {

        public final DataType dataType;

        public DefDataExpr(Range range, DataType dataType) {
            super(range);
            this.dataType = dataType;
        }

        @Override
        public <V> V accept(ExprVisitor<V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefDataExpr that = (DefDataExpr) o;
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
