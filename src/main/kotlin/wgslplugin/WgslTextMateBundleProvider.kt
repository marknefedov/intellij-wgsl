package wgslplugin

import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider

class WgslTextMateBundleProvider : TextMateBundleProvider, PluginAware {
    private lateinit var plugin: PluginDescriptor

    override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
        plugin = pluginDescriptor
    }

    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        return listOf(TextMateBundleProvider.PluginBundle("WGSL and WESL", plugin.pluginPath.resolve("textmate/wgsl")))
    }
}
