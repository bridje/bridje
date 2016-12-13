package bridje.analyser;

import bridje.reader.Form;
import bridje.reader.FormVisitor;
import bridje.runtime.Env;
import bridje.runtime.NS;
import bridje.runtime.Symbol;
import bridje.types.Type;
import org.pcollections.PVector;

import java.util.HashMap;
import java.util.Map;

import static bridje.Util.or;
import static bridje.Util.toPVector;
import static bridje.types.Type.FnType.fnType;
import static bridje.types.Type.SetType.setType;
import static bridje.types.Type.SimpleType.*;

public class TypeAnalyser {

    private final Env env;
    private final LocalTypeEnv localTypeEnv;
    private final NS currentNS;
    final Map<Symbol, TypeVar> typeVarMapping;

    public TypeAnalyser(Env env, LocalTypeEnv localTypeEnv, NS currentNS) {
        this.env = env;
        this.localTypeEnv = localTypeEnv;
        this.typeVarMapping = new HashMap<>(localTypeEnv.typeVarMapping);
        this.currentNS = currentNS;
    }

    private Type analyzeType0(Form form) {
        return form.accept(new FormVisitor<Type>() {
            @Override
            public Type visit(Form.BoolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.StringForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.IntForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.VectorForm form) {
                if (form.forms.size() == 1) {
                    return setType(analyzeType0(form.forms.get(0)));
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.SetForm form) {
                if (form.forms.size() == 1) {
                    return setType(analyzeType0(form.forms.get(0)));
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.MapForm mapForm) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.RecordForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.ListForm form) {
                PVector<Form> forms = form.forms;
                Form firstForm = forms.get(0);
                if (firstForm instanceof Form.SymbolForm) {
                    Symbol sym = ((Form.SymbolForm) firstForm).sym;

                    switch (sym.sym) {
                        case "Fn":
                            int formCount = forms.size();
                            if (formCount >= 3) {
                                PVector<Type> types = forms.minus(0).stream().map(f -> analyzeType0(f)).collect(toPVector());

                                return fnType(
                                    types.subList(0, types.size() - 1),
                                    types.get(types.size() - 1));
                            }
                            break;

                        default:
                            return new AppliedType(analyzeType0(firstForm),
                                forms.minus(0).stream().map(f -> analyzeType0(f)).collect(toPVector()));
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.SymbolForm form) {
                Symbol sym = form.sym;

                if (Character.isLowerCase(sym.sym.charAt(0))) {
                    return typeVarMapping.computeIfAbsent(sym, s -> new TypeVar());
                } else {
                    switch (sym.sym) {
                        case "Str":
                            return STRING_TYPE;
                        case "Int":
                            return INT_TYPE;
                        case "Bool":
                            return BOOL_TYPE;
                        default:
                            return or(
                                () -> localTypeEnv.resolve(sym),
                                () -> env.resolveDataType(currentNS, sym).map(dt -> dt.type),
                                () -> env.resolveImport(currentNS, sym).map(JavaType::new))

                                .orElseThrow(UnsupportedOperationException::new);
                    }
                }
            }

            @Override
            public Type visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Type analyzeType(Env env, LocalTypeEnv localTypeEnv, NS currentNS, Form form) {
        return new TypeAnalyser(env, localTypeEnv, currentNS).analyzeType0(form);
    }

}
