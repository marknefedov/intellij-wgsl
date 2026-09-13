package wgslplugin

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "WgslLanguageServerSettings", storages = [Storage("wgsl-lsp.xml")])
class WgslSettings : SimplePersistentStateComponent<WgslSettings.StoredState>(StoredState()) {
    class StoredState : BaseState() {
        var enabled by property(true)
        var managed by property(true)
        var executable by string("wgsl-analyzer")
        var configuration by string("{}")
    }

    data class Options(
        val enabled: Boolean = true,
        val managed: Boolean = true,
        val executable: String = "wgsl-analyzer",
        val configuration: String = "{}",
    )

    // Consumers receive a snapshot; edits cannot bypass BaseState modification tracking.
    val options: Options
        @Synchronized get() = Options(state.enabled, state.managed, state.executable.orEmpty(), state.configuration.orEmpty())

    @Synchronized
    fun update(options: Options) {
        state.enabled = options.enabled
        state.managed = options.managed
        state.executable = options.executable
        state.configuration = options.configuration
    }

    companion object {
        fun getInstance(project: Project): WgslSettings = project.service()
    }
}
