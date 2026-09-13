package wgslplugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "WgslLanguageServerSettings", storages = [Storage("wgsl-lsp.xml")])
class WgslSettings : PersistentStateComponent<WgslSettings.Options> {
    data class Options(
        var enabled: Boolean = true,
        var managed: Boolean = true,
        var executable: String = "wgsl-analyzer",
        var configuration: String = "{}",
    )

    private var options = Options()
    override fun getState() = options
    override fun loadState(state: Options) { options = state }

    companion object {
        fun getInstance(project: Project): WgslSettings = project.service()
    }
}
