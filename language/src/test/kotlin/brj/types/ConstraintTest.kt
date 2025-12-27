package brj.types

import brj.types.Nullability.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConstraintTest {

    @Nested
    inner class BasicResolution {

        @Test
        fun `empty constraints returns empty subst`() {
            val subst = emptyList<Constraint>().resolve()
            assertTrue(subst.isEmpty())
        }

        @Test
        fun `same types produces no bindings`() {
            val subst = listOf(IntType.notNull() subOf IntType.notNull()).resolve()
            assertTrue(subst.isEmpty())
        }

        @Test
        fun `concrete to fresh binds type`() {
            val tv = TypeVar()
            val subst = listOf(IntType.notNull() subOf freshType(tv)).resolve()
            assertEquals(IntType, subst[tv]?.base)
            assertEquals(NOT_NULL, subst[tv]?.nullability)
        }

        @Test
        fun `fresh to concrete binds type`() {
            val tv = TypeVar()
            val subst = listOf(freshType(tv) subOf IntType.notNull()).resolve()
            assertEquals(IntType, subst[tv]?.base)
        }

        @Test
        fun `nullable to non-null throws`() {
            assertThrows(TypeErrorException::class.java) {
                listOf(IntType.nullable() subOf IntType.notNull()).resolve()
            }
        }

        @Test
        fun `nullable propagates to fresh`() {
            val tv = TypeVar()
            val subst = listOf(IntType.nullable() subOf freshType(tv)).resolve()
            assertEquals(NULLABLE, subst[tv]?.nullability)
        }
    }

    @Nested
    inner class VectorConstraints {

        @Test
        fun `VectorType subOf VectorType decomposes to element constraint`() {
            val elemTv = TypeVar()
            val subst = listOf(
                VectorType(IntType.notNull()).notNull() subOf VectorType(freshType(elemTv)).notNull()
            ).resolve()
            assertEquals(IntType, subst[elemTv]?.base)
        }
    }

    @Nested
    inner class ApplySubstAfterResolve {

        @Test
        fun `resolving and applying gives correct base type`() {
            val fresh = freshType(TypeVar())
            val subst = listOf(IntType.notNull() subOf fresh).resolve()
            assertEquals(IntType, fresh.applySubst(subst).base)
        }

        @Test
        fun `resolving vector constraint and applying`() {
            val elemType = freshType(TypeVar())
            val vecType = VectorType(elemType).notNull()

            val subst = listOf(
                VectorType(IntType.notNull()).notNull() subOf vecType
            ).resolve()

            val result = vecType.applySubst(subst)
            val vecBase = result.base as VectorType
            assertEquals(IntType, vecBase.el.base)
        }
    }
}
