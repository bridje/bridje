package rho.analyser;

import rho.reader.Form;
import rho.reader.FormVisitor;

import static rho.Util.toPVector;

public class Analyser {

    public static ValueExpr analyseValueExpr(Form form) {
        return form.accept(new FormVisitor<ValueExpr>() {
            @Override
            public ValueExpr visit(Form.StringForm form) {
                return ValueExpr.StringExpr.fromForm(form);
            }

            @Override
            public ValueExpr visit(Form.IntForm form) {
                return ValueExpr.IntExpr.fromForm(form);
            }

            @Override
            public ValueExpr visit(Form.VectorForm form) {
                return ValueExpr.VectorExpr.vectorExpr(form.range, form.forms.stream().map(f -> f.accept(this)).collect(toPVector()));
            }

            @Override
            public ValueExpr visit(Form.SetForm form) {
                return ValueExpr.SetExpr.setExpr(form.range, form.forms.stream().map(f -> f.accept(this)).collect(toPVector()));
            }

            @Override
            public ValueExpr visit(Form.ListForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr visit(Form.SymbolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr analyse(Form form) {
        return form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr visit(Form.StringForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.IntForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.VectorForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.SetForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.ListForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.SymbolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }
}
