package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import java.io.PrintWriter;

import static brj.BridjeTypesGen.expectString;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

@NodeField(name = "writer", type = PrintWriter.class)
public abstract class PrintlnNode extends ExprNode {

    public PrintlnNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract PrintWriter getWriter();

    @TruffleBoundary
    private String print(String str) {
        PrintWriter writer = getWriter();
        writer.println(str);
        writer.flush();
        return str;
    }

    @Specialization
    public Object doExecute(VirtualFrame frame) {
        try {
            return print(expectString(frame.getArguments()[1]));
        } catch (UnexpectedResultException e) {
            throw shouldNotReachHere(e);
        }
    }
}
