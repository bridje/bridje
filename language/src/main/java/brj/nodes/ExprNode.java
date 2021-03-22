package brj.nodes;

import brj.BridjeLanguage;
import brj.BridjeTypes;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.source.SourceSection;
import jdk.jshell.SourceCodeAnalysis;

@TypeSystemReference(BridjeTypes.class)
public abstract class ExprNode extends ExecutableNode {
    protected ExprNode(BridjeLanguage lang) {
        super(lang);
    }

    @Override
    public abstract SourceSection getSourceSection();
}
