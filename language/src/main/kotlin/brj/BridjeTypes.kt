package brj

import brj.runtime.BridjeFunction
import brj.runtime.BridjeSet
import brj.runtime.BridjeVector
import com.oracle.truffle.api.dsl.TypeSystem

@TypeSystem(
    Int::class, Boolean::class, String::class,
    BridjeSet::class, BridjeVector::class,
    BridjeFunction::class)
abstract class BridjeTypes