package wgslplugin

import org.junit.Assert.*
import org.junit.Test

class WgslConfigurationTest {
    @Test fun `configuration requests support root nested and dotted settings`() {
        val configuration = WgslConfiguration.parse("""{"diagnostics":{"enable":false},"inlayHints.enabled":true}""")
        assertSame(configuration, WgslConfiguration.section(configuration, "wgsl-analyzer"))
        assertSame(configuration, WgslConfiguration.section(configuration, null))
        assertFalse(WgslConfiguration.section(configuration, "wgsl-analyzer.diagnostics.enable")!!.asBoolean)
        assertTrue(WgslConfiguration.section(configuration, "wgsl-analyzer.inlayHints.enabled")!!.asBoolean)
        assertNull(WgslConfiguration.section(configuration, "other-server"))
        assertNull(WgslConfiguration.section(configuration, "wgsl-analyzer.missing.setting"))
    }

    @Test fun `configuration requires an object`() {
        assertEquals(0, WgslConfiguration.parse(" ").size())
        for (value in listOf("null", "[]", "true", "42")) {
            assertThrows(IllegalArgumentException::class.java) { WgslConfiguration.parse(value) }
        }
    }
}
