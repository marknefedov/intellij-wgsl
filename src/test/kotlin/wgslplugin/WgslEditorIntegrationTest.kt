package wgslplugin

import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateFileType
import org.jetbrains.plugins.textmate.TextMateService
import java.nio.file.Files

class WgslEditorIntegrationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // These editor tests never launch or download a server.
        WgslSettings.getInstance(project).update(WgslSettings.Options(enabled = false))
    }

    fun testBundleIsPackagedAndBothLanguagesLoad() {
        val bundle = org.jetbrains.plugins.textmate.api.TextMateBundleProvider.EP_NAME.extensionList
            .filterIsInstance<WgslTextMateBundleProvider>().single().getBundles().single()
        assertTrue("Bundle must be outside the plugin JAR: ${bundle.path}", Files.exists(bundle.path.resolve("package.json")))
        val service = TextMateService.getInstance()
        assertNotNull(service.getLanguageDescriptorByFileName("shader.wgsl"))
        assertNotNull(service.getLanguageDescriptorByFileName("shader.wesl"))
        assertTrue(FileTypeManager.getInstance().getFileTypeByFileName("shader.wgsl") is WgslFileType)
        assertTrue(FileTypeManager.getInstance().getFileTypeByFileName("shader.wesl") is WeslFileType)
    }

    fun testWgslUsesTextMateAndHighlightsNestedComments() {
        val file = myFixture.configureByText("shader.wgsl", "/* outer /* inner */ outer */\nfn main() {}")
        assertEquals(TextMateFileType.INSTANCE, file.fileType)
        val tokens = scopes(file.text, file.virtualFile)
        assertTrue(tokens.any { it.first.contains("inner") && it.second.contains("comment.block") })
        assertTrue(tokens.any { it.first == "fn" && it.second.contains("keyword") })
    }

    fun testShaderColorsUseTextMateScopesAndRespectEditorScheme() {
        for (extension in listOf("wgsl", "wesl")) {
            val file = myFixture.configureByText("colors.$extension", """
                struct material { roughness: f32, }
                fn shade(input: material) { var value: material; value.roughness = 1.0; shade(value); }
            """.trimIndent())
            val syntax = com.intellij.openapi.fileTypes.SyntaxHighlighterFactory.getSyntaxHighlighter(file.fileType, project, file.virtualFile)
            assertTrue("Shader must select our scope mapper", syntax is WgslSyntaxHighlighter)
            val scheme = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme.clone() as com.intellij.openapi.editor.colors.EditorColorsScheme
            val custom = com.intellij.openapi.editor.markup.TextAttributes(java.awt.Color.MAGENTA, null, null, null, 0)
            scheme.setAttributes(WgslColors.FIELD, custom)
            val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file.virtualFile, scheme, project)
            highlighter.setText(file.text)
            val iterator = highlighter.createIterator(0)
            var fields = 0
            var types = 0
            while (!iterator.atEnd()) {
                val word = file.text.substring(iterator.start, iterator.end)
                if (word == "roughness") {
                    assertEquals(java.awt.Color.MAGENTA, iterator.textAttributes.foregroundColor)
                    fields++
                }
                if (word == "material") {
                    assertEquals(WgslColors.TYPE, syntax!!.getTokenHighlights(iterator.tokenType).single())
                    types++
                }
                iterator.advance()
            }
            assertEquals(2, fields)
            assertEquals(3, types)
        }
    }

    fun testOtherTextMateLanguagesKeepTheirHighlighter() {
        val syntax = WgslSyntaxHighlighterFactory().getSyntaxHighlighter(project, com.intellij.testFramework.LightVirtualFile("other.txt"))
        assertFalse(syntax is WgslSyntaxHighlighter)
    }

    fun testColorSettingsPreviewAndThemeDefaults() {
        val page = WgslColorSettingsPage()
        assertEquals(6, page.attributeDescriptors.size)
        assertTrue(page.highlighter is WgslSyntaxHighlighter)
        val manager = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance()
        for (name in listOf("Default", "Darcula")) {
            val scheme = requireNotNull(manager.getScheme(name))
            assertNotNull(scheme.getAttributes(WgslColors.TYPE).foregroundColor)
            assertFalse(scheme.getAttributes(WgslColors.TYPE).foregroundColor == scheme.getAttributes(WgslColors.FIELD).foregroundColor)
            val preview = page.createEditorHighlighter(scheme)
            preview.setText(page.demoText)
            val token = preview.createIterator(page.demoText.indexOf("roughness"))
            assertEquals(scheme.getAttributes(WgslColors.FIELD).foregroundColor, token.textAttributes.foregroundColor)
        }
    }

    fun testWeslImportsAndWgslSyntaxShareHighlighting() {
        val file = myFixture.configureByText("shader.wesl", "import package::math::value;\nfn main() {}")
        assertEquals(TextMateFileType.INSTANCE, file.fileType)
        val tokens = scopes(file.text, file.virtualFile)
        assertTrue(tokens.any { it.first == "import" && it.second.contains("keyword.control.import.wesl") })
        assertTrue(tokens.any { it.first == "fn" && it.second.contains("keyword") })
        val descriptor = WgslLspClientDescriptor(project)
        assertEquals("wesl", descriptor.getLanguageId(file.virtualFile))
    }

    fun testCustomExecutableIsPassedAsOneArgument() {
        WgslSettings.getInstance(project).update(WgslSettings.Options(managed = false, executable = "C:/Tools With Spaces/wgsl-analyzer.exe"))
        val command = WgslLspClientDescriptor(project).createCommandLine()
        assertEquals("C:/Tools With Spaces/wgsl-analyzer.exe", command.exePath)
        assertTrue(command.parametersList.list.isEmpty())
    }

    fun testTextMateCommentAction() {
        myFixture.configureByText("shader.wgsl", "<caret>let value = 1;")
        myFixture.performEditorAction("CommentByLineComment")
        assertTrue(myFixture.editor.document.text.trimStart().startsWith("//"))
        myFixture.performEditorAction("CommentByLineComment")
        assertEquals("let value = 1;", myFixture.editor.document.text)
    }

    fun testSettingsFormLifecycleDoesNotModifySavedState() {
        WgslSettings.getInstance(project).update(WgslSettings.Options(enabled = false, managed = false, executable = ""))
        val configurable = WgslSettingsConfigurable(project)
        try {
            configurable.createComponent()
            assertFalse(configurable.isModified)
            configurable.apply()
            configurable.reset()
            assertFalse(configurable.isModified)
            configurable.createComponent()
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
        assertFalse(configurable.isModified)
    }

    private fun scopes(text: String, file: com.intellij.openapi.vfs.VirtualFile): List<Pair<String, String>> {
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
        highlighter.setText(text)
        val iterator = highlighter.createIterator(0)
        val tokens = mutableListOf<Pair<String, String>>()
        while (!iterator.atEnd()) {
            tokens += text.substring(iterator.start, iterator.end) to iterator.tokenType.toString()
            iterator.advance()
        }
        return tokens
    }
}
