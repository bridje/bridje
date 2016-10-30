package rho.runtime;

import java.lang.invoke.MethodHandle;

public interface IndyBootstrap {

    void setHandles(MethodHandle valueHandle, MethodHandle fnHandle);

}
