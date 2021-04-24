package brj

import brj.runtime.BridjeEnv
import com.oracle.truffle.api.TruffleLanguage

class BridjeContext internal constructor(val truffleEnv: TruffleLanguage.Env) {
    val bridjeEnv = BridjeEnv(truffleEnv)
}