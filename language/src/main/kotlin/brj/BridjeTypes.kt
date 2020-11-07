package brj

import com.oracle.truffle.api.dsl.TypeSystem

@TypeSystem(Int::class, Boolean::class, String::class)
abstract class BridjeTypes