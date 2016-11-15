package rho.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import static rho.runtime.Var.FN_METHOD_NAME;
import static rho.runtime.Var.VALUE_METHOD_NAME;

public interface IndyBootstrap {

    void setHandles(MethodHandle valueHandle, MethodHandle fnHandle);

    class Delegate implements IndyBootstrap {
        private final MutableCallSite valueCallSite;
        private final MutableCallSite fnCallSite;

        public Delegate(Class<?> valueClass, MethodType fnMethodType) {
            this.valueCallSite = new MutableCallSite(MethodType.methodType(valueClass));
            this.fnCallSite = fnMethodType != null
                ? new MutableCallSite(fnMethodType)
                : null;
        }

        public CallSite bootstrap(String name) {
            switch (name) {
                case VALUE_METHOD_NAME:
                    return valueCallSite;
                case FN_METHOD_NAME:
                    if (fnCallSite != null) {
                        return fnCallSite;
                    }
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public void setHandles(MethodHandle valueHandle, MethodHandle fnHandle) {
            valueCallSite.setTarget(valueHandle);

            if (fnHandle != null) {
                fnCallSite.setTarget(fnHandle);
            }
        }
    }

}
