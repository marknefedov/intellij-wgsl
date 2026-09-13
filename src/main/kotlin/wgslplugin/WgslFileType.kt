package wgslplugin

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.textmate.TextMateBackedFileType
import javax.swing.Icon

object WgslIcons {
    val FILE: Icon = IconLoader.getIcon("/icons/wgsl.svg", WgslIcons::class.java)
}

/** TextMate supplies the language and highlighting; these registrations retain file identity and icons. */
class WgslFileType : FileType, TextMateBackedFileType {
    override fun getName() = "WGSL File"
    override fun getDescription() = "WGSL"
    override fun getDefaultExtension() = "wgsl"
    override fun getIcon(): Icon = WgslIcons.FILE
    override fun isBinary() = false
}

class WeslFileType : FileType, TextMateBackedFileType {
    override fun getName() = "WESL File"
    override fun getDescription() = "WESL"
    override fun getDefaultExtension() = "wesl"
    override fun getIcon(): Icon = WgslIcons.FILE
    override fun isBinary() = false
}
