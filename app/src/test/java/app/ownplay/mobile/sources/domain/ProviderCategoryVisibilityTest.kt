package app.ownplay.mobile.sources.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCategoryVisibilityTest {
    @Test
    fun `utility labels tolerate provider punctuation suffixes and unicode styling`() {
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("All"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("ALL CHANNELS"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("• Account Information •"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("ACCOUNT_INFO [expires soon]"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("Ａｃｃｏｕｎｔ Ｉｎｆｏｒｍａｔｉｏｎ"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("𝐀𝐜𝐜𝐨𝐮𝐧𝐭 𝐈𝐧𝐟𝐨𝐫𝐦𝐚𝐭𝐢𝐨𝐧"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("ᴀᴄᴄᴏᴜɴᴛ ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ"))
        assertTrue(ProviderCategoryVisibility.isUtilityLabel("Account​Information"))
        assertFalse(ProviderCategoryVisibility.isUtilityLabel("All Sports"))
        assertFalse(ProviderCategoryVisibility.isUtilityLabel("Accountants Information"))
        assertFalse(ProviderCategoryVisibility.isUtilityLabel("News"))
    }
}
