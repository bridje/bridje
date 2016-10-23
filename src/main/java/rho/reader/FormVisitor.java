package rho.reader;

public interface FormVisitor<T> {

    T accept(Form.StringForm form);

    T accept(Form.IntForm form);

    T accept(Form.VectorForm form);

    T accept(Form.SetForm form);

    T accept(Form.ListForm form);

    T accept(Form.SymbolForm form);

    T accept(Form.QSymbolForm form);
}
