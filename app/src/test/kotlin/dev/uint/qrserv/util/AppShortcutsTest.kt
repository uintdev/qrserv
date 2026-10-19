package dev.uint.qrserv.util

import dev.uint.qrserv.net.HotspotUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppShortcutsTest {

    @Test
    fun bothShortcutsWhenTheHotspotCanWork() {
        assertEquals(listOf(AppShortcut.SHARE_FILE, AppShortcut.SHARE_HOTSPOT), availableShortcuts(null))
    }

    @Test
    fun noHotspotShortcutWhereTheHotspotCantWork() {
        for (reason in HotspotUnavailable.entries) {
            assertEquals(reason.name, listOf(AppShortcut.SHARE_FILE), availableShortcuts(reason))
        }
    }

    @Test
    fun actionsMapBackToTheirShortcuts() {
        for (shortcut in AppShortcut.entries) assertEquals(shortcut, AppShortcut.fromAction(shortcut.action))
        assertNull(AppShortcut.fromAction("android.intent.action.MAIN"))
        assertNull(AppShortcut.fromAction(null))
    }
}
