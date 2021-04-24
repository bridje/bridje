package brj.nodes.builtins;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.nodes.ExprNode;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import static brj.BridjeTypesGen.expectString;

public abstract class PrStrNode extends ExprNode {

    private final InteropLibrary objLib = InteropLibrary.getUncached();

    public PrStrNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization
    public String doExecute(VirtualFrame frame,
                            @CachedLanguage BridjeLanguage lang,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        try {
            Object obj = frame.getArguments()[1];
            if (!objLib.hasLanguage(obj) || objLib.getLanguage(obj) != BridjeLanguage.class) {
                obj = lang.getLanguageView(ctx, obj);
            }
            return expectString(objLib.toDisplayString(obj));
        } catch (UnexpectedResultException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

}
