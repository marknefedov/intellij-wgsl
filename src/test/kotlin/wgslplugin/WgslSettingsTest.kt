package wgslplugin

import org.junit.Assert.*
import org.junit.Test
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

class WgslSettingsTest {
    @Test fun testExistingSettingsXmlLoadsAndRoundTrips() {
        val xml = Element("state")
        for ((name, value) in mapOf("enabled" to "false", "managed" to "false", "executable" to "C:/Tools/server.exe", "configuration" to "{\"diagnostics\":{}}")) {
            xml.addContent(Element("option").setAttribute("name", name).setAttribute("value", value))
        }
        val state = XmlSerializer.deserialize(xml, WgslSettings.StoredState::class.java)
        val settings = WgslSettings()
        settings.loadState(state)
        val options = settings.options
        assertEquals(WgslSettings.Options(false, false, "C:/Tools/server.exe", "{\"diagnostics\":{}}"), options)
        settings.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(state), WgslSettings.StoredState::class.java))
        assertEquals(options, settings.options)
    }

    @Test fun testSettingsUpdatesTrackModificationWithoutChangingExistingSnapshot() {
        val settings = WgslSettings()
        val snapshot = settings.options
        val count = settings.stateModificationCount
        settings.update(snapshot.copy(enabled = false))
        assertTrue(snapshot.enabled)
        assertFalse(settings.options.enabled)
        assertTrue(settings.stateModificationCount > count)
    }

}
