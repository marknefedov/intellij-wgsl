package wgslplugin

import com.intellij.textmate.joni.JoniRegexFactory
import junit.framework.TestCase
import org.jetbrains.plugins.textmate.language.TextMateInterner
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableBuilder
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateSyntaxMatcherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import org.jetbrains.plugins.textmate.regex.DefaultRegexProvider

/** Uses IntelliJ's real grammar engine without starting an IDE project or language server. */
class WgslTextMateGrammarTest : TestCase() {
    fun testTypeParameterAndFieldScopesInBothLanguages() {
        for (extension in listOf("wgsl", "wesl")) {
            val source = """
                struct material {
                    @align(16) roughness: f32,
                    layers: array<array<material, 2>, 4>,
                }
                alias surface = material;
                fn shade(input: material, scale: f32) -> material {
                    var result: material;
                    result.roughness = input.roughness * scale;
                    shade(result, 1.0);
                    return result;
                }
            """.trimIndent()
            val tokens = scopes(source, extension)
            fun assertScope(word: String, scope: String) {
                val matches = tokens.filter { it.first == word }
                assertTrue("$extension: missing $word in $tokens", matches.isNotEmpty())
                assertTrue("$extension: $word should be $scope: $matches", matches.all { it.second.contains(scope) })
            }
            assertScope("material", "entity.name.type")
            assertScope("surface", "entity.name.type")
            assertScope("roughness", "variable.other.member")
            assertScope("layers", "variable.other.member")
            assertTrue(tokens.any { it.first == "shade" && it.second.contains("entity.name.function") })
            assertTrue(tokens.any { it.first == "shade" && it.second.contains("support.function") })
            assertTrue(tokens.any { it.first == "input" && it.second.contains("variable.parameter") })
            assertTrue(tokens.any { it.first == "input" && it.second.contains("variable.other.wgsl") })
            assertScope("result", "variable.other.wgsl")
        }
    }

    fun testTypeContextsDoNotLeakIntoExpressionsOrComments() {
        val extension = "wgsl"
        val source = """
            // struct fake { field: imaginary }
            alias item = array<vec4f, 4>;
            fn main() {
                var value: item = make();
                let UPPERCASE = 1;
                var<storage, read_write> buffer: item;
                /* value.field: imaginary */
                value.x = UPPERCASE;
                switch UPPERCASE { case 1: consume(value); default: break; }
            }
        """.trimIndent()
        val tokens = scopes(source, extension)
        assertTrue(tokens.any { it.first == "make" && it.second.contains("support.function") })
        assertTrue(tokens.any { it.first == "consume" && it.second.contains("support.function") })
        assertTrue(tokens.filter { it.first == "UPPERCASE" }.all { it.second.contains("variable.other.wgsl") })
        assertTrue(tokens.any { it.first == "storage" && it.second.contains("storage.modifier.address_spaces") })
        assertTrue(tokens.any { it.first == "read_write" && it.second.contains("storage.modifier.memory_access_modes") })
        assertTrue(tokens.any { it.first.contains("imaginary") && it.second.contains("comment.line") })
        assertTrue(tokens.any { it.first.contains("imaginary") && it.second.contains("comment.block") })
    }

    private fun scopes(text: String, extension: String): List<Pair<String, String>> {
        val builder = TextMateSyntaxTableBuilder(object : TextMateInterner {
            override fun intern(name: String) = name
            override fun clear() = Unit
        })
        for (language in listOf("wgsl", "wesl")) {
            val bytes = requireNotNull(javaClass.getResourceAsStream("/textmate/wgsl/syntaxes/$language.tmLanguage.json")) {
                "Missing grammar test resource: $language"
            }.use { it.readBytes() }
            builder.addSyntax(JsonPlistReader().read(bytes))
        }
        val descriptor = requireNotNull(builder.build().getLanguageDescriptor("source.$extension"))
        val matcher = TextMateSyntaxMatcherImpl(DefaultRegexProvider(JoniRegexFactory()), TextMateSelectorWeigherImpl())
        val lexer = TextMateLexerCore(descriptor, matcher, -1, false)
        lexer.init(text, 0)
        val result = mutableListOf<Pair<String, String>>()
        while (lexer.getCurrentOffset() < text.length) {
            val before = lexer.getCurrentOffset()
            result += lexer.advanceLine(null).map { text.substring(it.startOffset, it.endOffset) to it.scope.toString() }
            check(lexer.getCurrentOffset() > before) { "Lexer stopped advancing" }
        }
        return result
    }
}
