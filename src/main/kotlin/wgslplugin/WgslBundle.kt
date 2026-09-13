package wgslplugin

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object WgslBundle {
    private val bundle = DynamicBundle(WgslBundle::class.java, "messages.WgslBundle")

    @Nls
    fun message(@PropertyKey(resourceBundle = "messages.WgslBundle") key: String, vararg params: Any): String =
        bundle.getMessage(key, *params)
}
