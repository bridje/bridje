package brj.intellij

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class BridjeFileType : LanguageFileType(BridjeLanguage) {
    override fun getName() = "Bridje"
    override fun getDescription() = "Bridje source file"
    override fun getDefaultExtension() = "bridje"
    override fun getIcon(): Icon? =
        IconLoader.getIcon("META-INF/pluginIcon.svg", BridjeFileType::class.java.classLoader)
}