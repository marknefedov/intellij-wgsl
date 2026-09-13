package wgslplugin

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.EditorHighlightingProvidingColorSettingsPage
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerDataStorage
import com.intellij.testFramework.LightVirtualFile

class WgslColorSettingsPage : EditorHighlightingProvidingColorSettingsPage {
    override fun getDisplayName() = "WGSL / WESL"
    override fun getIcon() = WgslIcons.FILE
    override fun getHighlighter() = WgslSyntaxHighlighterFactory().getSyntaxHighlighter(null, LightVirtualFile("preview.wgsl"))
    override fun createEditorHighlighter(scheme: EditorColorsScheme) = object : LexerEditorHighlighter(highlighter, scheme) {
        // TextMate token types carry scopes and must not be reduced to numeric IDs.
        override fun createStorage() = TextMateLexerDataStorage()
    }
    override fun getAdditionalHighlightingTagToDescriptorMap() = null
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Types and aliases", WgslColors.TYPE),
        AttributesDescriptor("Function declarations", WgslColors.FUNCTION),
        AttributesDescriptor("Function calls", WgslColors.CALL),
        AttributesDescriptor("Fields and swizzles", WgslColors.FIELD),
        AttributesDescriptor("Parameter declarations", WgslColors.PARAMETER),
        AttributesDescriptor("Variables", WgslColors.VARIABLE),
    )

    override fun getDemoText() = """
        // Colors use TextMate syntax classification, without a server.
        struct material {
            roughness: f32,
            tint: vec4f,
        }
        alias surface = material;

        fn shade(input: material, scale: f32) -> vec4f {
            var result: surface = input;
            result.roughness = input.roughness * scale;
            return result.tint;
        }

        fn main() {
            var value: material;
            let color = shade(value, 1.0);
        }
    """.trimIndent()
}
