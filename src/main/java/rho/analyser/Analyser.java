package rho.analyser;

import rho.reader.Form;
import rho.reader.FormVisitor;

import static rho.Util.toPVector;

public class Analyser {

    public static Expr analyse(Form form) {
        return form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr accept(Form.StringForm form) {
                return ValueExpr.StringExpr.fromForm(form);
            }

            @Override
            public Expr accept(Form.IntForm form) {
                return ValueExpr.IntExpr.fromForm(form);
            }

            @Override
            public Expr accept(Form.VectorForm form) {
                return ValueExpr.VectorExpr.vectorExpr(form.range, form.forms.stream().map(Analyser::analyse).collect(toPVector()));
            }

            @Override
            public Expr accept(Form.SetForm form) {
                return ValueExpr.SetExpr.setExpr(form.range, form.forms.stream().map(Analyser::analyse).collect(toPVector()));
            }

            @Override
            public Expr accept(Form.ListForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr accept(Form.SymbolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr accept(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }
}
