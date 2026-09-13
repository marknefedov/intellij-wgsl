package wgslplugin

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object WgslBundle : DynamicBundle("messages.WgslBundle") {
    @Nls
    fun message(@PropertyKey(resourceBundle = "messages.WgslBundle") key: String, vararg params: Any): String =
        getMessage(key, *params)
}
