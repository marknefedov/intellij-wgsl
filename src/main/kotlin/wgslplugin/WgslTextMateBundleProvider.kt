package wgslplugin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider

class WgslTextMateBundleProvider : TextMateBundleProvider {
    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val plugin = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("WGSL")))
        return listOf(TextMateBundleProvider.PluginBundle("WGSL and WESL", plugin.pluginPath.resolve("textmate/wgsl")))
    }
}
