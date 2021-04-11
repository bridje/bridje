package brj.nodes.builtins;

import brj.BridjeLanguage;
import brj.BridjeTypesGen;
import brj.nodes.ExprNode;
import brj.runtime.BridjeVector;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import static brj.BridjeTypesGen.expectBridjeVector;
import static brj.BridjeTypesGen.expectInteger;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

public class ConjNode extends ExprNode {
    public ConjNode(BridjeLanguage lang) {
        super(lang);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            var args = frame.getArguments();
            return expectBridjeVector(args[1]).conj(expectInteger(args[2]));
        } catch (UnexpectedResultException e) {
            throw shouldNotReachHere();
        }
    }
}
