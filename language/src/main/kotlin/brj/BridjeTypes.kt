package brj

import brj.runtime.*
import com.oracle.truffle.api.dsl.TypeSystem

@TypeSystem(
    Int::class, Boolean::class, String::class,
    BridjeSet::class, BridjeVector::class,
    BridjeFunction::class,
    BridjeRecord::class, BridjeVariant::class,
    FxMap::class
)
abstract class BridjeTypes