package com.bond.mail.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushAccessConfigStoreTest {
    @Test
    fun `normalizes a hostname to an https origin`() {
        assertEquals(
            "https://push.example.com",
            PushAccessConfigStore.normalizeServiceOrigin("push.example.com"),
        )
    }

    @Test
    fun `preserves a valid https port and removes a trailing slash`() {
        assertEquals(
            "https://push.example.com:8443",
            PushAccessConfigStore.normalizeServiceOrigin(
                "https://PUSH.Example.com:8443/",
            ),
        )
    }

    @Test
    fun `rejects insecure or non-origin values`() {
        assertNull(PushAccessConfigStore.normalizeServiceOrigin(""))
        assertNull(PushAccessConfigStore.normalizeServiceOrigin("http://push.example.com"))
        assertNull(PushAccessConfigStore.normalizeServiceOrigin("https://user@push.example.com"))
        assertNull(PushAccessConfigStore.normalizeServiceOrigin("https://push.example.com/api"))
        assertNull(PushAccessConfigStore.normalizeServiceOrigin("https://push.example.com?key=value"))
    }

    @Test
    fun `moves missing and retired origins to the current service`() {
        assertEquals(
            PushAccessConfigStore.CURRENT_SERVICE_ORIGIN,
            PushAccessConfigStore.migrateServiceOrigin(null),
        )
        assertEquals(
            PushAccessConfigStore.CURRENT_SERVICE_ORIGIN,
            PushAccessConfigStore.migrateServiceOrigin(
                PushAccessConfigStore.LEGACY_SERVICE_ORIGIN,
            ),
        )
        assertEquals(
            "https://self-hosted.example.com",
            PushAccessConfigStore.migrateServiceOrigin(
                "https://self-hosted.example.com",
            ),
        )
    }
}
