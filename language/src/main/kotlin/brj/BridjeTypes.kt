package brj

import com.oracle.truffle.api.dsl.TypeSystem

@TypeSystem(Boolean::class, Long::class, Double::class)
abstract class BridjeTypes
