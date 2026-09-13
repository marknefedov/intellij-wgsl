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
        WgslSettings.getInstance(project).state.enabled = false
    }

    fun testBundleIsPackagedAndBothLanguagesLoad() {
        val bundle = WgslTextMateBundleProvider().getBundles().single()
        assertTrue("Bundle must be outside the plugin JAR: ${bundle.path}", Files.exists(bundle.path.resolve("package.json")))
        val service = TextMateService.getInstance()
        assertNotNull(service.getLanguageDescriptorByFileName("shader.wgsl"))
        assertNotNull(service.getLanguageDescriptorByFileName("shader.wesl"))
        assertEquals(WgslFileType.WGSL, FileTypeManager.getInstance().getFileTypeByFileName("shader.wgsl"))
    }

    fun testWgslUsesTextMateAndHighlightsNestedComments() {
        val file = myFixture.configureByText("shader.wgsl", "/* outer /* inner */ outer */\nfn main() {}")
        assertEquals(TextMateFileType.INSTANCE, file.fileType)
        val tokens = scopes(file.text, file.virtualFile)
        assertTrue(tokens.any { it.first.contains("inner") && it.second.contains("comment.block") })
        assertTrue(tokens.any { it.first == "fn" && it.second.contains("keyword") })
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
        WgslSettings.getInstance(project).loadState(WgslSettings.Options(managed = false, executable = "C:/Tools With Spaces/wgsl-analyzer.exe"))
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
