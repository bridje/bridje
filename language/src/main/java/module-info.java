module bridje.language {
    requires org.graalvm.truffle;
    requires kotlin.stdlib;
    exports brj;

    provides com.oracle.truffle.api.TruffleLanguage
            with brj.BridjeLanguage;
}