package rho.analyser;

import org.junit.Test;

import static rho.analyser.Analyser.analyse;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.SymbolForm.symbolForm;

public class AnalyserTest {

    @Test
    public void resolvesPlus() throws Exception {
        Expr expr = analyse(listForm(symbolForm("+"), intForm(1), intForm(2)));
    }
}