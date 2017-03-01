package bridje.analyser;

import bridje.reader.Form;
import bridje.reader.FormVisitor;
import bridje.runtime.Env;
import bridje.runtime.NSEnv;
import bridje.util.Pair;

import static bridje.util.Pair.pair;

public class KernelLanguage implements Language {

    @Override
    public Pair<Expr, NSAnalysisEnv> loadForm(Env env, NSAnalysisEnv nsAnalysisEnv, Form form) {
        Expr expr = form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr visit(Form.BoolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.StringForm form) {
                return new Expr.StringExpr(form.range, form.string);
            }

            @Override
            public Expr visit(Form.IntForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.VectorForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.SetForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.RecordForm form) {
                throw new UnsupportedOperationException();
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

        return pair(expr, nsAnalysisEnv);
    }
}
