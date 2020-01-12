package brj.reader

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

internal interface Loc {
    val startLine: Int
    val endLine: Int
    val startCol: Int
    val endCol: Int

    data class SourceSectionLoc(val sourceSection: SourceSection): Loc {
        override val startLine get() = sourceSection.startLine
        override val endLine get() = sourceSection.endLine
        override val startCol get() = sourceSection.startColumn
        override val endCol get() = sourceSection.endColumn
    }

    interface Factory {
        fun makeLoc(line: Int, col: Int, length: Int): Loc

        class SourceSectionLocFactory(private val source: Source): Factory {
            override fun makeLoc(line: Int, col: Int, length: Int): Loc {
                return SourceSectionLoc(source.createSection(line, col, length))
            }
        }
    }
}