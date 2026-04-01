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
    inner class FnTypeConstraints {

        @Test
        fun `FnType subOf FnType decomposes - covariant return`() {
            val retTv = TypeVar()
            val subst = listOf(
                FnType(listOf(IntType.notNull()), StringType.notNull()).notNull() subOf
                    FnType(listOf(IntType.notNull()), freshType(retTv)).notNull()
            ).resolve()
            assertEquals(StringType, subst[retTv]?.base)
        }

        @Test
        fun `FnType subOf FnType decomposes - contravariant params`() {
            val paramTv = TypeVar()
            val subst = listOf(
                FnType(listOf(freshType(paramTv)), IntType.notNull()).notNull() subOf
                    FnType(listOf(StringType.notNull()), IntType.notNull()).notNull()
            ).resolve()
            assertEquals(StringType, subst[paramTv]?.base)
        }

        @Test
        fun `FnType arity mismatch throws`() {
            assertThrows(TypeErrorException::class.java) {
                listOf(
                    FnType(listOf(IntType.notNull()), IntType.notNull()).notNull() subOf
                        FnType(listOf(IntType.notNull(), IntType.notNull()), IntType.notNull()).notNull()
                ).resolve()
            }
        }

        @Test
        fun `lower has extra trailing Record param - resolves`() {
            val retTv = TypeVar()
            val subst = listOf(
                FnType(listOf(StringType.notNull(), RecordType.notNull()), IntType.notNull()).notNull() subOf
                    FnType(listOf(StringType.notNull()), freshType(retTv)).notNull()
            ).resolve()
            assertEquals(IntType, subst[retTv]?.base)
        }

        @Test
        fun `upper has extra trailing Record param - resolves`() {
            val retTv = TypeVar()
            val subst = listOf(
                FnType(listOf(StringType.notNull()), IntType.notNull()).notNull() subOf
                    FnType(listOf(StringType.notNull(), RecordType.notNull()), freshType(retTv)).notNull()
            ).resolve()
            assertEquals(IntType, subst[retTv]?.base)
        }

        @Test
        fun `single Record param can be elided`() {
            val retTv = TypeVar()
            val subst = listOf(
                FnType(listOf(RecordType.notNull()), IntType.notNull()).notNull() subOf
                    FnType(emptyList(), freshType(retTv)).notNull()
            ).resolve()
            assertEquals(IntType, subst[retTv]?.base)
        }

        @Test
        fun `extra trailing non-Record param still throws`() {
            assertThrows(TypeErrorException::class.java) {
                listOf(
                    FnType(listOf(StringType.notNull(), IntType.notNull()), IntType.notNull()).notNull() subOf
                        FnType(listOf(StringType.notNull()), IntType.notNull()).notNull()
                ).resolve()
            }
            assertThrows(TypeErrorException::class.java) {
                listOf(
                    FnType(listOf(StringType.notNull()), IntType.notNull()).notNull() subOf
                        FnType(listOf(StringType.notNull(), IntType.notNull()), IntType.notNull()).notNull()
                ).resolve()
            }
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
            val vecBase = result.base as AppliedType
            assertEquals(IntType, vecBase.args[0].base)
        }
    }
}
