package wgslplugin

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Defaults
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateSyntaxHighlighterFactory
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

object WgslColors {
    val TYPE = TextAttributesKey.createTextAttributesKey("WGSL.TYPE", Defaults.CLASS_NAME)
    val FUNCTION = TextAttributesKey.createTextAttributesKey("WGSL.FUNCTION", Defaults.FUNCTION_DECLARATION)
    val CALL = TextAttributesKey.createTextAttributesKey("WGSL.FUNCTION_CALL", Defaults.FUNCTION_CALL)
    val FIELD = TextAttributesKey.createTextAttributesKey("WGSL.FIELD", Defaults.INSTANCE_FIELD)
    val PARAMETER = TextAttributesKey.createTextAttributesKey("WGSL.PARAMETER", Defaults.PARAMETER)
    val VARIABLE = TextAttributesKey.createTextAttributesKey("WGSL.VARIABLE", Defaults.LOCAL_VARIABLE)

    fun key(scope: TextMateScope): TextAttributesKey? {
        var current: TextMateScope? = scope
        while (current != null) {
            val name = current.scopeName?.toString().orEmpty()
            if (name.endsWith(".wgsl")) {
                when {
                    name.startsWith("entity.name.type.") || name.startsWith("storage.type.") -> return TYPE
                    name.startsWith("entity.name.function.") -> return FUNCTION
                    name.startsWith("support.function.") -> return CALL
                    name == "variable.other.member.wgsl" -> return FIELD
                    name == "variable.parameter.wgsl" -> return PARAMETER
                    name == "variable.other.wgsl" -> return VARIABLE
                }
            }
            current = current.parent
        }
        return null
    }
}

/** Keep TextMate's lexer, incremental storage, and all unrelated scopes intact. */
class WgslSyntaxHighlighter(private val delegate: SyntaxHighlighter) : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = delegate.highlightingLexer

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        val key = (tokenType as? TextMateElementType)?.let { WgslColors.key(it.scope) }
        return if (key != null) arrayOf(key) else delegate.getTokenHighlights(tokenType)
    }
}

/** TextMate-backed files share one language; delegate every other file unchanged. */
class WgslSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        val delegate = TextMateSyntaxHighlighterFactory().getSyntaxHighlighter(project, virtualFile)
        return if (virtualFile?.extension?.lowercase() in listOf("wgsl", "wesl")) WgslSyntaxHighlighter(delegate) else delegate
    }
}
