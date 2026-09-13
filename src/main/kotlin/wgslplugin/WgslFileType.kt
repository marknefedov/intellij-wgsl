package wgslplugin

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.textmate.TextMateBackedFileType
import javax.swing.Icon

/** TextMate supplies the language and highlighting; these registrations retain file identity and icons. */
class WgslFileType private constructor(
    private val typeName: String,
    private val description: String,
    private val extension: String,
) : FileType, TextMateBackedFileType {
    override fun getName() = typeName
    override fun getDescription() = description
    override fun getDefaultExtension() = extension
    override fun getIcon(): Icon = ICON
    override fun isBinary() = false

    companion object {
        @JvmField val WGSL = WgslFileType("WGSL File", "WGSL", "wgsl")
        @JvmField val WESL = WgslFileType("WESL File", "WESL", "wesl")
        val ICON: Icon = IconLoader.getIcon("/icons/wgsl.svg", WgslFileType::class.java)
    }
}
