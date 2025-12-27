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
            val joined = (VectorType(IntType.notNull()) join VectorType(IntType.notNull())) as VectorType
            assertEquals(IntType, joined.el.base)
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
            val vecBase = result.base as VectorType
            assertEquals(IntType, vecBase.el.base)
        }
    }
}
