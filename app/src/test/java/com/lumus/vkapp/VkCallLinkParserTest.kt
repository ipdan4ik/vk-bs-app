package com.lumus.vkapp

import com.lumus.vkapp.transport.CallProvider
import com.lumus.vkapp.transport.VkCallLinkParser
import org.junit.Assert.assertEquals
import org.junit.Test

class VkCallLinkParserTest {
    @Test
    fun `parses vk links`() {
        val parsed = VkCallLinkParser.parse("https://vk.com/call/join/abcdef")
        assertEquals(CallProvider.VK, parsed.provider)
        assertEquals("https://vk.com/call/join/abcdef", parsed.normalizedLink)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported hosts`() {
        VkCallLinkParser.parse("https://example.com/room")
    }
}

