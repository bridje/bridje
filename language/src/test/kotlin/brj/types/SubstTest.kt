package brj.types

import brj.types.Nullability.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SubstTest {

    @Nested
    inner class NullabilityJoinTests {

        @Test
        fun `join NOT_NULL with NOT_NULL`() {
            assertEquals(NOT_NULL, NOT_NULL join NOT_NULL)
        }

        @Test
        fun `join NOT_NULL with MAYBE_NULL`() {
            assertEquals(MAYBE_NULL, NOT_NULL join MAYBE_NULL)
            assertEquals(MAYBE_NULL, MAYBE_NULL join NOT_NULL)
        }

        @Test
        fun `join with NULLABLE always gives NULLABLE`() {
            assertEquals(NULLABLE, NOT_NULL join NULLABLE)
            assertEquals(NULLABLE, NULLABLE join NOT_NULL)
            assertEquals(NULLABLE, MAYBE_NULL join NULLABLE)
            assertEquals(NULLABLE, NULLABLE join MAYBE_NULL)
            assertEquals(NULLABLE, NULLABLE join NULLABLE)
        }
    }

    @Nested
    inner class NullabilityMeetTests {

        @Test
        fun `meet NULLABLE with NULLABLE`() {
            assertEquals(NULLABLE, NULLABLE meet NULLABLE)
        }

        @Test
        fun `meet NULLABLE with MAYBE_NULL`() {
            assertEquals(MAYBE_NULL, NULLABLE meet MAYBE_NULL)
            assertEquals(MAYBE_NULL, MAYBE_NULL meet NULLABLE)
        }

        @Test
        fun `meet with NOT_NULL always gives NOT_NULL`() {
            assertEquals(NOT_NULL, NOT_NULL meet NULLABLE)
            assertEquals(NOT_NULL, NULLABLE meet NOT_NULL)
            assertEquals(NOT_NULL, NOT_NULL meet MAYBE_NULL)
            assertEquals(NOT_NULL, MAYBE_NULL meet NOT_NULL)
            assertEquals(NOT_NULL, NOT_NULL meet NOT_NULL)
        }
    }

    @Nested
    inner class BaseTypeJoinTests {

        @Test
        fun `join identical base types`() {
            assertEquals(IntType, IntType join IntType)
            assertEquals(StringType, StringType join StringType)
        }

        @Test
        fun `join different base types throws`() {
            assertThrows(TypeErrorException::class.java) {
                IntType join StringType
            }
        }

        @Test
        fun `join VectorTypes joins elements`() {
            val joined = (VectorType(IntType.notNull()) join VectorType(IntType.notNull())) as AppliedType
            assertEquals(IntType, joined.args[0].base)
        }
    }

    @Nested
    inner class TypeJoinTests {

        @Test
        fun `join types with same base`() {
            val joined = IntType.notNull() join IntType.notNull()
            assertEquals(IntType, joined.base)
            assertEquals(NOT_NULL, joined.nullability)
        }

        @Test
        fun `join non-null with nullable promotes to nullable`() {
            val joined = IntType.notNull() join IntType.nullable()
            assertEquals(IntType, joined.base)
            assertEquals(NULLABLE, joined.nullability)
        }

        @Test
        fun `join with unknown base type`() {
            val joined = IntType.notNull() join freshType()
            assertEquals(IntType, joined.base)
        }

        @Test
        fun `join two unknown base types`() {
            val joined = freshType() join freshType()
            assertNull(joined.base)
        }
    }

    @Nested
    inner class FnTypeJoinTests {

        @Test
        fun `join identical FnTypes`() {
            val fn1 = FnType(listOf(IntType.notNull()), StringType.notNull())
            val fn2 = FnType(listOf(IntType.notNull()), StringType.notNull())
            val joined = fn1 join fn2
            assertTrue(joined is FnType)
        }

        @Test
        fun `join FnTypes with different arities throws`() {
            val fn1 = FnType(listOf(IntType.notNull()), StringType.notNull())
            val fn2 = FnType(listOf(IntType.notNull(), IntType.notNull()), StringType.notNull())
            assertThrows(TypeErrorException::class.java) { fn1 join fn2 }
        }

        @Test
        fun `join FnTypes with trailing Record produces shorter arity`() {
            val fn1 = FnType(listOf(StringType.notNull(), RecordType.notNull()), IntType.notNull())
            val fn2 = FnType(listOf(StringType.notNull()), IntType.notNull())
            val joined = fn1 join fn2
            assertTrue(joined is FnType)
            assertEquals(1, (joined as FnType).paramTypes.size)
        }

        @Test
        fun `join FnTypes with trailing Record is symmetric`() {
            val fn1 = FnType(listOf(StringType.notNull()), IntType.notNull())
            val fn2 = FnType(listOf(StringType.notNull(), RecordType.notNull()), IntType.notNull())
            val joined = fn1 join fn2
            assertTrue(joined is FnType)
            assertEquals(1, (joined as FnType).paramTypes.size)
        }
    }

    @Nested
    inner class FnTypeMeetTests {

        @Test
        fun `meet FnTypes with trailing Record produces longer arity`() {
            val fn1 = FnType(listOf(StringType.notNull(), RecordType.notNull()), IntType.notNull())
            val fn2 = FnType(listOf(StringType.notNull()), IntType.notNull())
            val met = fn1 meet fn2
            assertTrue(met is FnType)
            assertEquals(2, (met as FnType).paramTypes.size)
            assertEquals(RecordType, met.paramTypes[1].base)
        }

        @Test
        fun `meet FnTypes with trailing Record is symmetric`() {
            val fn1 = FnType(listOf(StringType.notNull()), IntType.notNull())
            val fn2 = FnType(listOf(StringType.notNull(), RecordType.notNull()), IntType.notNull())
            val met = fn1 meet fn2
            assertTrue(met is FnType)
            assertEquals(2, (met as FnType).paramTypes.size)
        }
    }

    @Nested
    inner class ApplySubstFnTypeTests {

        @Test
        fun `applySubst recurses into FnType`() {
            val tv = TypeVar()
            val fnType = FnType(listOf(freshType(tv)), IntType.notNull()).notNull()
            val subst = mapOf(tv to StringType.notNull())
            val result = fnType.applySubst(subst)
            val fn = result.base as FnType
            assertEquals(StringType, fn.paramTypes[0].base)
        }
    }

    @Nested
    inner class ApplySubstTests {

        @Test
        fun `applySubst on type with no bindings`() {
            val result = IntType.notNull().applySubst(emptyMap())
            assertEquals(IntType, result.base)
            assertEquals(NOT_NULL, result.nullability)
        }

        @Test
        fun `applySubst binds base type`() {
            val tv = TypeVar()
            val type = freshType(tv)
            val subst = mapOf(tv to IntType.notNull())
            val result = type.applySubst(subst)
            assertEquals(IntType, result.base)
        }

        @Test
        fun `applySubst binds nullability via join`() {
            val tv = TypeVar()
            val type = freshType(tv)
            val subst = mapOf(tv to nullType())
            val result = type.applySubst(subst)
            assertEquals(NULLABLE, result.nullability)
        }

        @Test
        fun `applySubst on vector applies to element`() {
            val elemTv = TypeVar()
            val elemType = freshType(elemTv)
            val vecType = VectorType(elemType).notNull()
            val subst = mapOf(elemTv to IntType.notNull())
            val result = vecType.applySubst(subst)
            val vecBase = result.base as AppliedType
            assertEquals(IntType, vecBase.args[0].base)
        }
    }
}
