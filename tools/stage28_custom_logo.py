from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Daos.kt: expose the raw localLogo alongside the effective logoUrl.
path = Path("app/src/main/java/app/ownplay/mobile/data/db/Daos.kt")
text = path.read_text()
text = replace_once(
    text,
    '''    val favorite: Boolean,
    val localName: String?,
    val hidden: Boolean,
''',
    '''    val favorite: Boolean,
    val localName: String?,
    val localLogo: String?,
    val hidden: Boolean,
''',
    "manageable channel localLogo field",
)
text = replace_once(
    text,
    '''            COALESCE(p.favorite, 0) AS favorite,
            p.localName AS localName,
            COALESCE(p.hidden, 0) AS hidden,
''',
    '''            COALESCE(p.favorite, 0) AS favorite,
            p.localName AS localName,
            p.localLogo AS localLogo,
            COALESCE(p.hidden, 0) AS hidden,
''',
    "manageable channel localLogo projection",
)
path.write_text(text)

# LiveModels.kt: expose raw localLogo and repository mutation.
path = Path("app/src/main/java/app/ownplay/mobile/feature/live/domain/LiveModels.kt")
text = path.read_text()
text = replace_once(
    text,
    '''    val favorite: Boolean,
    val localName: String?,
    val hidden: Boolean,
''',
    '''    val favorite: Boolean,
    val localName: String?,
    val localLogo: String?,
    val hidden: Boolean,
''',
    "domain manageable channel localLogo",
)
text = replace_once(
    text,
    '''    suspend fun setChannelFavorite(channelId: String, favorite: Boolean)
    suspend fun setChannelLocalName(channelId: String, localName: String?)
    suspend fun setCategoryOrder(sourceId: String, orderedCategoryKeys: List<String>)
''',
    '''    suspend fun setChannelFavorite(channelId: String, favorite: Boolean)
    suspend fun setChannelLocalName(channelId: String, localName: String?)
    suspend fun setChannelLocalLogo(channelId: String, localLogo: String?)
    suspend fun setCategoryOrder(sourceId: String, orderedCategoryKeys: List<String>)
''',
    "repository local logo contract",
)
path.write_text(text)

# LivePersonalizationPolicy.kt: blank restores; invalid nonblank input is rejected by caller.
Path("app/src/main/java/app/ownplay/mobile/feature/live/domain/LivePersonalizationPolicy.kt").write_text('''package app.ownplay.mobile.feature.live.domain

import java.net.URI

object LivePersonalizationPolicy {
    const val MAX_LOCAL_NAME_LENGTH: Int = 120
    const val MAX_LOCAL_LOGO_URL_LENGTH: Int = 2_048

    fun normalizeLocalName(value: String?): String? = value
        ?.trim()
        ?.take(MAX_LOCAL_NAME_LENGTH)
        ?.takeIf { it.isNotEmpty() }

    fun normalizeLocalLogo(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalized.length > MAX_LOCAL_LOGO_URL_LENGTH) return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return normalized
    }

    fun isValidLocalLogoInput(value: String?): Boolean =
        value.isNullOrBlank() || normalizeLocalLogo(value) != null
}
''')

# LiveRepositoryImpl.kt: map raw state and mutate only valid nonblank URLs.
path = Path("app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt")
text = path.read_text()
text = replace_once(
    text,
    '''                                    favorite = row.favorite,
                                    localName = row.localName,
                                    hidden = row.hidden,
''',
    '''                                    favorite = row.favorite,
                                    localName = row.localName,
                                    localLogo = row.localLogo,
                                    hidden = row.hidden,
''',
    "repository manageable localLogo mapping",
)
text = replace_once(
    text,
    '''    override suspend fun setCategoryOrder(sourceId: String, orderedCategoryKeys: List<String>) {
''',
    '''    override suspend fun setChannelLocalLogo(channelId: String, localLogo: String?) {
        if (channelId.isBlank()) return
        val normalized = LivePersonalizationPolicy.normalizeLocalLogo(localLogo)
        if (!localLogo.isNullOrBlank() && normalized == null) return
        database.withTransaction {
            if (catalogDao.getLiveChannel(channelId) == null) return@withTransaction
            val current = catalogDao.getChannelPersonalization(channelId)
            catalogDao.upsertChannelPersonalization(
                (current ?: ChannelPersonalizationEntity(channelId = channelId)).copy(localLogo = normalized),
            )
        }
    }

    override suspend fun setCategoryOrder(sourceId: String, orderedCategoryKeys: List<String>) {
''',
    "repository local logo mutation",
)
path.write_text(text)

# LiveManagementScreen.kt: consolidate local name + logo into one personalization editor.
path = Path("app/src/main/java/app/ownplay/mobile/feature/settings/ui/LiveManagementScreen.kt")
text = path.read_text()
text = replace_once(
    text,
    'import app.ownplay.mobile.feature.live.domain.LiveManagementCatalog\n',
    'import app.ownplay.mobile.feature.live.domain.LiveManagementCatalog\nimport app.ownplay.mobile.feature.live.domain.LivePersonalizationPolicy\n',
    "management personalization policy import",
)
text = replace_once(
    text,
    '''                onRenameChannel = { channelId, localName ->
                    scope.launch { liveRepository.setChannelLocalName(channelId, localName) }
                },
''',
    '''                onSavePersonalization = { channelId, localName, localLogo ->
                    scope.launch {
                        liveRepository.setChannelLocalName(channelId, localName)
                        liveRepository.setChannelLocalLogo(channelId, localLogo)
                    }
                },
''',
    "management personalization callback",
)
text = replace_once(
    text,
    '''    onToggleChannel: (ManageableLiveChannel) -> Unit,
    onToggleFavorite: (ManageableLiveChannel) -> Unit,
    onRenameChannel: (String, String?) -> Unit,
    onMoveChannel: (String, Int) -> Boolean,
''',
    '''    onToggleChannel: (ManageableLiveChannel) -> Unit,
    onToggleFavorite: (ManageableLiveChannel) -> Unit,
    onSavePersonalization: (String, String?, String?) -> Unit,
    onMoveChannel: (String, Int) -> Boolean,
''',
    "channel management personalization signature",
)
text = replace_once(
    text,
    '''    var renameChannelId by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var renameValue by rememberSaveable(title) { mutableStateOf("") }
''',
    '''    var editChannelId by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var editNameValue by rememberSaveable(title) { mutableStateOf("") }
    var editLogoValue by rememberSaveable(title) { mutableStateOf("") }
    val editLogoValid = LivePersonalizationPolicy.isValidLocalLogoInput(editLogoValue)
''',
    "channel management edit state",
)
old_panel = '''        renameChannelId?.let { channelId ->
            val target = channels.firstOrNull { it.channelId == channelId }
            OwnPlayPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Xs),
            ) {
                Column(
                    modifier = Modifier.padding(OwnPlaySpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                ) {
                    Text(
                        text = "Rename channel",
                        style = MaterialTheme.typography.titleSmall,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = target?.name ?: "Channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnPlayColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Local name") },
                        supportingText = { Text("Leave blank to restore the provider name.") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { renameChannelId = null; renameValue = "" }) { Text("Cancel") }
                        TextButton(
                            onClick = {
                                onRenameChannel(channelId, renameValue)
                                renameChannelId = null
                                renameValue = ""
                            },
                        ) { Text("Save") }
                    }
                }
            }
        }
'''
new_panel = '''        editChannelId?.let { channelId ->
            val target = channels.firstOrNull { it.channelId == channelId }
            OwnPlayPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Xs),
            ) {
                Column(
                    modifier = Modifier.padding(OwnPlaySpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                ) {
                    Text(
                        text = "Channel personalization",
                        style = MaterialTheme.typography.titleSmall,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = target?.name ?: "Channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnPlayColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedTextField(
                        value = editNameValue,
                        onValueChange = { editNameValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Local name") },
                        supportingText = { Text("Leave blank to restore the provider name.") },
                    )
                    OutlinedTextField(
                        value = editLogoValue,
                        onValueChange = { editLogoValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !editLogoValid,
                        label = { Text("Custom logo URL") },
                        supportingText = {
                            Text(
                                if (editLogoValid) {
                                    "HTTP/HTTPS only. Leave blank to restore the provider logo."
                                } else {
                                    "Enter a valid HTTP/HTTPS URL without embedded credentials."
                                },
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                editChannelId = null
                                editNameValue = ""
                                editLogoValue = ""
                            },
                        ) { Text("Cancel") }
                        TextButton(
                            enabled = editLogoValid,
                            onClick = {
                                onSavePersonalization(channelId, editNameValue, editLogoValue)
                                editChannelId = null
                                editNameValue = ""
                                editLogoValue = ""
                            },
                        ) { Text("Save") }
                    }
                }
            }
        }
'''
text = replace_once(text, old_panel, new_panel, "channel personalization editor")
text = replace_once(
    text,
    '''                                    if (channel.favorite) add("Favorite")
                                    if (channel.localName != null) add("Custom name")
''',
    '''                                    if (channel.favorite) add("Favorite")
                                    if (channel.localName != null) add("Custom name")
                                    if (channel.localLogo != null) add("Custom logo")
''',
    "channel custom logo status",
)
text = replace_once(
    text,
    '''                        TextButton(
                            onClick = {
                                renameChannelId = channel.channelId
                                renameValue = channel.localName ?: channel.name
                            },
                            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Xs),
                        ) {
                            Text("Rename", style = MaterialTheme.typography.labelMedium)
                        }
''',
    '''                        TextButton(
                            onClick = {
                                editChannelId = channel.channelId
                                editNameValue = channel.localName ?: channel.name
                                editLogoValue = channel.localLogo.orEmpty()
                            },
                            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Xs),
                        ) {
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }
''',
    "channel edit button",
)
path.write_text(text)

# Focused JUnit4 tests for custom logo policy.
test = Path("app/src/test/java/app/ownplay/mobile/feature/live/domain/LiveLogoPersonalizationPolicyTest.kt")
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package app.ownplay.mobile.feature.live.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLogoPersonalizationPolicyTest {
    @Test
    fun `blank logo restores provider logo`() {
        assertNull(LivePersonalizationPolicy.normalizeLocalLogo(null))
        assertNull(LivePersonalizationPolicy.normalizeLocalLogo("   "))
        assertTrue(LivePersonalizationPolicy.isValidLocalLogoInput("   "))
    }

    @Test
    fun `http and https logo urls are normalized`() {
        assertEquals(
            "https://cdn.example.com/logo.png",
            LivePersonalizationPolicy.normalizeLocalLogo("  https://cdn.example.com/logo.png  "),
        )
        assertEquals(
            "http://192.168.1.20/channel.png",
            LivePersonalizationPolicy.normalizeLocalLogo("http://192.168.1.20/channel.png"),
        )
    }

    @Test
    fun `unsafe or malformed logo urls are rejected`() {
        assertFalse(LivePersonalizationPolicy.isValidLocalLogoInput("ftp://example.com/logo.png"))
        assertFalse(LivePersonalizationPolicy.isValidLocalLogoInput("/relative/logo.png"))
        assertFalse(LivePersonalizationPolicy.isValidLocalLogoInput("https://user:secret@example.com/logo.png"))
        assertFalse(
            LivePersonalizationPolicy.isValidLocalLogoInput(
                "https://example.com/" + "x".repeat(LivePersonalizationPolicy.MAX_LOCAL_LOGO_URL_LENGTH),
            ),
        )
    }
}
''')
