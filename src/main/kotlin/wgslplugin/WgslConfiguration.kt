package wgslplugin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Unwrapped wgsl-analyzer settings, shared by initialization and workspace/configuration. */
object WgslConfiguration {
    fun parse(json: String): JsonObject {
        val value = JsonParser.parseString(json.ifBlank { "{}" })
        require(value.isJsonObject) { "Server configuration must be a JSON object" }
        return value.asJsonObject
    }

    fun section(configuration: JsonObject, section: String?): JsonElement? {
        if (section.isNullOrBlank() || section == "wgsl-analyzer") return configuration
        if (!section.startsWith("wgsl-analyzer.")) return null
        val path = section.removePrefix("wgsl-analyzer.")
        configuration.get(path)?.let { return it }
        var result: JsonElement = configuration
        for (key in path.split('.')) {
            if (!result.isJsonObject) return null
            result = result.asJsonObject.get(key) ?: return null
        }
        return result
    }
}
