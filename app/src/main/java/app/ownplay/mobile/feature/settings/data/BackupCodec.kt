package app.ownplay.mobile.feature.settings.data

import app.ownplay.mobile.feature.settings.domain.ProviderRefreshInterval
import app.ownplay.mobile.feature.settings.domain.SettingsSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal data class BackupSourceRecord(
    val sourceId: String,
    val displayName: String,
    val type: String,
    val safeBaseLocator: String?,
    val enabled: Boolean,
    val createdAt: Long,
)

internal data class BackupChannelPersonalizationRecord(
    val sourceId: String,
    val channelId: String,
    val favorite: Boolean,
    val hidden: Boolean,
    val localName: String?,
    val manualOrder: Int?,
)

internal data class BackupGroupRecord(
    val groupId: String,
    val sourceId: String,
    val name: String,
    val manualOrder: Int,
)

internal data class BackupMembershipRecord(
    val sourceId: String,
    val groupId: String,
    val channelId: String,
    val manualOrder: Int,
)

internal data class BackupFavoriteRecord(
    val sourceId: String,
    val mediaKind: String,
    val contentId: String,
    val addedAt: Long,
)

internal data class BackupProgressRecord(
    val sourceId: String,
    val mediaKind: String,
    val contentId: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

internal data class BackupDocument(
    val generatedAt: Long,
    val activeSourceId: String?,
    val settings: SettingsSnapshot,
    val sources: List<BackupSourceRecord>,
    val channelPersonalization: List<BackupChannelPersonalizationRecord>,
    val groups: List<BackupGroupRecord>,
    val memberships: List<BackupMembershipRecord>,
    val favorites: List<BackupFavoriteRecord>,
    val progress: List<BackupProgressRecord>,
)

internal data class PendingRestoreDocument(
    val channelPersonalization: List<BackupChannelPersonalizationRecord>,
    val memberships: List<BackupMembershipRecord>,
)

internal object BackupCodec {
    const val FORMAT = "ownplay-backup"
    const val VERSION = 1
    private const val PENDING_FORMAT = "ownplay-pending-restore"
    private const val MAX_INPUT_CHARS = 10_000_000
    private const val MAX_RECORDS_PER_SECTION = 50_000
    private const val MAX_STRING_CHARS = 16_384
    private val json = Json { ignoreUnknownKeys = false }

    fun encode(document: BackupDocument): String = buildJsonObject {
        put("format", JsonPrimitive(FORMAT))
        put("version", JsonPrimitive(VERSION))
        put("generatedAt", JsonPrimitive(document.generatedAt))
        put("activeSourceId", document.activeSourceId.asJson())
        put("settings", encodeSettings(document.settings))
        put("sources", buildJsonArray { document.sources.forEach { add(encodeSource(it)) } })
        put(
            "channelPersonalization",
            buildJsonArray { document.channelPersonalization.forEach { add(encodePersonalization(it)) } },
        )
        put("groups", buildJsonArray { document.groups.forEach { add(encodeGroup(it)) } })
        put("memberships", buildJsonArray { document.memberships.forEach { add(encodeMembership(it)) } })
        put("favorites", buildJsonArray { document.favorites.forEach { add(encodeFavorite(it)) } })
        put("progress", buildJsonArray { document.progress.forEach { add(encodeProgress(it)) } })
    }.toString()

    fun decode(raw: String): BackupDocument {
        require(raw.length <= MAX_INPUT_CHARS) { "Backup is too large." }
        val root = json.parseToJsonElement(raw).jsonObject
        require(root.requiredString("format") == FORMAT) { "Unsupported backup format." }
        require(root.requiredInt("version") == VERSION) { "Unsupported backup version." }

        return BackupDocument(
            generatedAt = root.requiredLong("generatedAt"),
            activeSourceId = root.optionalString("activeSourceId"),
            settings = decodeSettings(root.requiredObject("settings")),
            sources = root.requiredArray("sources").decodeSection(::decodeSource),
            channelPersonalization = root.requiredArray("channelPersonalization")
                .decodeSection(::decodePersonalization),
            groups = root.requiredArray("groups").decodeSection(::decodeGroup),
            memberships = root.requiredArray("memberships").decodeSection(::decodeMembership),
            favorites = root.requiredArray("favorites").decodeSection(::decodeFavorite),
            progress = root.requiredArray("progress").decodeSection(::decodeProgress),
        )
    }

    fun encodePending(document: PendingRestoreDocument): String = buildJsonObject {
        put("format", JsonPrimitive(PENDING_FORMAT))
        put("version", JsonPrimitive(VERSION))
        put(
            "channelPersonalization",
            buildJsonArray { document.channelPersonalization.forEach { add(encodePersonalization(it)) } },
        )
        put("memberships", buildJsonArray { document.memberships.forEach { add(encodeMembership(it)) } })
    }.toString()

    fun decodePending(raw: String): PendingRestoreDocument {
        if (raw.isBlank()) return PendingRestoreDocument(emptyList(), emptyList())
        require(raw.length <= MAX_INPUT_CHARS) { "Pending restore data is too large." }
        val root = json.parseToJsonElement(raw).jsonObject
        require(root.requiredString("format") == PENDING_FORMAT) { "Unsupported pending restore format." }
        require(root.requiredInt("version") == VERSION) { "Unsupported pending restore version." }
        return PendingRestoreDocument(
            channelPersonalization = root.requiredArray("channelPersonalization")
                .decodeSection(::decodePersonalization),
            memberships = root.requiredArray("memberships").decodeSection(::decodeMembership),
        )
    }

    private fun encodeSettings(settings: SettingsSnapshot) = buildJsonObject {
        put("pictureInPictureEnabled", JsonPrimitive(settings.pictureInPictureEnabled))
        put("resumePlaybackEnabled", JsonPrimitive(settings.resumePlaybackEnabled))
        put("autoRefreshProviders", JsonPrimitive(settings.autoRefreshProviders))
        put("providerRefreshInterval", JsonPrimitive(settings.providerRefreshInterval.name))
        put("showChannelLogos", JsonPrimitive(settings.showChannelLogos))
    }

    private fun decodeSettings(value: JsonObject): SettingsSnapshot = SettingsSnapshot(
        pictureInPictureEnabled = value.requiredBoolean("pictureInPictureEnabled"),
        resumePlaybackEnabled = value.requiredBoolean("resumePlaybackEnabled"),
        autoRefreshProviders = value.requiredBoolean("autoRefreshProviders"),
        providerRefreshInterval = ProviderRefreshInterval.valueOf(value.requiredString("providerRefreshInterval")),
        showChannelLogos = value.requiredBoolean("showChannelLogos"),
    )

    private fun encodeSource(value: BackupSourceRecord) = buildJsonObject {
        put("sourceId", JsonPrimitive(value.sourceId))
        put("displayName", JsonPrimitive(value.displayName))
        put("type", JsonPrimitive(value.type))
        put("safeBaseLocator", value.safeBaseLocator.asJson())
        put("enabled", JsonPrimitive(value.enabled))
        put("createdAt", JsonPrimitive(value.createdAt))
    }

    private fun decodeSource(value: JsonObject) = BackupSourceRecord(
        sourceId = value.requiredString("sourceId"),
        displayName = value.requiredString("displayName"),
        type = value.requiredString("type"),
        safeBaseLocator = value.optionalString("safeBaseLocator"),
        enabled = value.requiredBoolean("enabled"),
        createdAt = value.requiredLong("createdAt"),
    )

    private fun encodePersonalization(value: BackupChannelPersonalizationRecord) = buildJsonObject {
        put("sourceId", JsonPrimitive(value.sourceId))
        put("channelId", JsonPrimitive(value.channelId))
        put("favorite", JsonPrimitive(value.favorite))
        put("hidden", JsonPrimitive(value.hidden))
        put("localName", value.localName.asJson())
        put("manualOrder", value.manualOrder?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun decodePersonalization(value: JsonObject) = BackupChannelPersonalizationRecord(
        sourceId = value.requiredString("sourceId"),
        channelId = value.requiredString("channelId"),
        favorite = value.requiredBoolean("favorite"),
        hidden = value.requiredBoolean("hidden"),
        localName = value.optionalString("localName"),
        manualOrder = value.optionalInt("manualOrder"),
    )

    private fun encodeGroup(value: BackupGroupRecord) = buildJsonObject {
        put("groupId", JsonPrimitive(value.groupId))
        put("sourceId", JsonPrimitive(value.sourceId))
        put("name", JsonPrimitive(value.name))
        put("manualOrder", JsonPrimitive(value.manualOrder))
    }

    private fun decodeGroup(value: JsonObject) = BackupGroupRecord(
        groupId = value.requiredString("groupId"),
        sourceId = value.requiredString("sourceId"),
        name = value.requiredString("name"),
        manualOrder = value.requiredInt("manualOrder"),
    )

    private fun encodeMembership(value: BackupMembershipRecord) = buildJsonObject {
        put("sourceId", JsonPrimitive(value.sourceId))
        put("groupId", JsonPrimitive(value.groupId))
        put("channelId", JsonPrimitive(value.channelId))
        put("manualOrder", JsonPrimitive(value.manualOrder))
    }

    private fun decodeMembership(value: JsonObject) = BackupMembershipRecord(
        sourceId = value.requiredString("sourceId"),
        groupId = value.requiredString("groupId"),
        channelId = value.requiredString("channelId"),
        manualOrder = value.requiredInt("manualOrder"),
    )

    private fun encodeFavorite(value: BackupFavoriteRecord) = buildJsonObject {
        put("sourceId", JsonPrimitive(value.sourceId))
        put("mediaKind", JsonPrimitive(value.mediaKind))
        put("contentId", JsonPrimitive(value.contentId))
        put("addedAt", JsonPrimitive(value.addedAt))
    }

    private fun decodeFavorite(value: JsonObject) = BackupFavoriteRecord(
        sourceId = value.requiredString("sourceId"),
        mediaKind = value.requiredString("mediaKind"),
        contentId = value.requiredString("contentId"),
        addedAt = value.requiredLong("addedAt"),
    )

    private fun encodeProgress(value: BackupProgressRecord) = buildJsonObject {
        put("sourceId", JsonPrimitive(value.sourceId))
        put("mediaKind", JsonPrimitive(value.mediaKind))
        put("contentId", JsonPrimitive(value.contentId))
        put("positionMs", JsonPrimitive(value.positionMs))
        put("durationMs", JsonPrimitive(value.durationMs))
        put("completed", JsonPrimitive(value.completed))
        put("updatedAt", JsonPrimitive(value.updatedAt))
    }

    private fun decodeProgress(value: JsonObject) = BackupProgressRecord(
        sourceId = value.requiredString("sourceId"),
        mediaKind = value.requiredString("mediaKind"),
        contentId = value.requiredString("contentId"),
        positionMs = value.requiredLong("positionMs"),
        durationMs = value.requiredLong("durationMs"),
        completed = value.requiredBoolean("completed"),
        updatedAt = value.requiredLong("updatedAt"),
    )

    private fun String?.asJson() = this?.let(::JsonPrimitive) ?: JsonNull

    private fun JsonObject.requiredString(name: String): String = optionalString(name)
        ?.takeIf { it.isNotBlank() && it.length <= MAX_STRING_CHARS }
        ?: throw IllegalArgumentException("Invalid $name.")

    private fun JsonObject.optionalString(name: String): String? {
        val element = this[name] ?: return null
        if (element is JsonNull) return null
        val value = element.jsonPrimitive.contentOrNull ?: throw IllegalArgumentException("Invalid $name.")
        require(value.length <= MAX_STRING_CHARS) { "Invalid $name." }
        return value
    }

    private fun JsonObject.requiredLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Invalid $name.")

    private fun JsonObject.requiredInt(name: String): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("Invalid $name.")

    private fun JsonObject.optionalInt(name: String): Int? {
        val element = this[name] ?: return null
        if (element is JsonNull) return null
        return element.jsonPrimitive.intOrNull ?: throw IllegalArgumentException("Invalid $name.")
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: throw IllegalArgumentException("Invalid $name.")

    private fun JsonObject.requiredObject(name: String): JsonObject =
        this[name]?.jsonObject ?: throw IllegalArgumentException("Invalid $name.")

    private fun JsonObject.requiredArray(name: String): JsonArray =
        this[name]?.jsonArray ?: throw IllegalArgumentException("Invalid $name.")

    private fun <T> JsonArray.decodeSection(decode: (JsonObject) -> T): List<T> {
        require(size <= MAX_RECORDS_PER_SECTION) { "Backup section is too large." }
        return map { element -> decode(element.jsonObject) }
    }
}
