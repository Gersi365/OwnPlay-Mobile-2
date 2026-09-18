package app.ownplay.mobile.feature.settings.backup.data

import app.ownplay.mobile.downloads.domain.DownloadPreferences
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.playback.domain.PlaybackPreferences
import app.ownplay.mobile.feature.settings.backup.domain.BackupCatalogKind
import app.ownplay.mobile.feature.settings.backup.domain.BackupCategoryPersonalization
import app.ownplay.mobile.feature.settings.backup.domain.BackupChannelPersonalization
import app.ownplay.mobile.feature.settings.backup.domain.BackupCustomGroup
import app.ownplay.mobile.feature.settings.backup.domain.BackupCustomGroupMembership
import app.ownplay.mobile.feature.settings.backup.domain.BackupGlobalSettings
import app.ownplay.mobile.feature.settings.backup.domain.BackupLiveCategoryPersonalization
import app.ownplay.mobile.feature.settings.backup.domain.BackupLivePlacementOverride
import app.ownplay.mobile.feature.settings.backup.domain.BackupMediaFavorite
import app.ownplay.mobile.feature.settings.backup.domain.BackupMediaKind
import app.ownplay.mobile.feature.settings.backup.domain.BackupSourceDefinition
import app.ownplay.mobile.feature.settings.backup.domain.BackupSourceSettings
import app.ownplay.mobile.feature.settings.backup.domain.OwnPlayBackupEnvelope
import app.ownplay.mobile.feature.settings.backup.domain.OwnPlayBackupPayload
import app.ownplay.mobile.feature.settings.domain.DisplayPreferences
import app.ownplay.mobile.feature.settings.domain.SourceRefreshSchedule
import app.ownplay.mobile.sources.domain.SourceType
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
import kotlinx.serialization.json.put

internal fun OwnPlayBackupEnvelope.toJson(): JsonObject = buildJsonObject {
    put("format", format)
    put("version", version)
    put("createdAt", createdAt)
    put("payload", payload.toJson())
}

internal fun JsonObject.toBackupEnvelope() = OwnPlayBackupEnvelope(
    format = requiredString("format"),
    version = requiredInt("version"),
    createdAt = requiredString("createdAt"),
    payload = getValue("payload").jsonObject.toPayload(),
)

private fun OwnPlayBackupPayload.toJson(): JsonObject = buildJsonObject {
    put("sources", arrayOf(sources) { it.toJson() })
    put("activeSourceId", activeSourceId?.let(::JsonPrimitive) ?: JsonNull)
    put("globalSettings", globalSettings.toJson())
    put("sourceSettings", arrayOf(sourceSettings) { it.toJson() })
    put("categoryPersonalization", arrayOf(categoryPersonalization) { it.toJson() })
    put("channelPersonalization", arrayOf(channelPersonalization) { it.toJson() })
    put("mediaFavorites", arrayOf(mediaFavorites) { it.toJson() })
    put("liveCategoryPersonalization", arrayOf(liveCategoryPersonalization) { it.toJson() })
    put("livePlacementOverrides", arrayOf(livePlacementOverrides) { it.toJson() })
    put("customGroups", arrayOf(customGroups) { it.toJson() })
    put("customGroupMemberships", arrayOf(customGroupMemberships) { it.toJson() })
}

private fun JsonObject.toPayload() = OwnPlayBackupPayload(
    sources = arrayOrEmpty("sources").map { it.jsonObject.toSource() },
    activeSourceId = optionalString("activeSourceId"),
    globalSettings = getValue("globalSettings").jsonObject.toGlobalSettings(),
    sourceSettings = arrayOrEmpty("sourceSettings").map { it.jsonObject.toSourceSettings() },
    categoryPersonalization = arrayOrEmpty("categoryPersonalization").map {
        it.jsonObject.toCategoryPersonalization()
    },
    channelPersonalization = arrayOrEmpty("channelPersonalization").map {
        it.jsonObject.toChannelPersonalization()
    },
    mediaFavorites = arrayOrEmpty("mediaFavorites").map { it.jsonObject.toMediaFavorite() },
    liveCategoryPersonalization = arrayOrEmpty("liveCategoryPersonalization").map {
        it.jsonObject.toLiveCategoryPersonalization()
    },
    livePlacementOverrides = arrayOrEmpty("livePlacementOverrides").map {
        it.jsonObject.toLivePlacementOverride()
    },
    customGroups = arrayOrEmpty("customGroups").map { it.jsonObject.toCustomGroup() },
    customGroupMemberships = arrayOrEmpty("customGroupMemberships").map {
        it.jsonObject.toCustomGroupMembership()
    },
)
private fun BackupSourceDefinition.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("type", type.name)
    put("displayName", displayName)
    put("baseLocator", baseLocator)
    put("enabled", enabled)
}

private fun BackupGlobalSettings.toJson() = buildJsonObject {
    put("compactMediaRows", display.compactMediaRows)
    put("automaticPictureInPicture", playback.automaticPictureInPicture)
    put("unmeteredNetworkOnly", downloads.unmeteredNetworkOnly)
}

private fun BackupSourceSettings.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("refreshSchedule", refreshSchedule.name)
    put("liveOrganizationMode", liveOrganizationMode.name)
}

private fun BackupCategoryPersonalization.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("kind", kind.name)
    put("categoryKey", categoryKey)
    put("hidden", hidden)
    putNullableInt("manualOrder", manualOrder)
}
private fun BackupChannelPersonalization.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("channelId", channelId)
    put("favorite", favorite)
    put("hidden", hidden)
    putNullableString("localName", localName)
    putNullableString("localLogo", localLogo)
    putNullableInt("manualOrder", manualOrder)
}

private fun BackupMediaFavorite.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("mediaKind", mediaKind.name)
    put("contentId", contentId)
    put("addedAt", addedAt)
}

private fun BackupLiveCategoryPersonalization.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("organizationMode", organizationMode.name)
    put("categoryId", categoryId)
    put("hidden", hidden)
    putNullableInt("manualOrder", manualOrder)
}

private fun BackupLivePlacementOverride.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("categoryId", categoryId)
    put("channelId", channelId)
    put("hidden", hidden)
    putNullableInt("manualOrder", manualOrder)
}

private fun BackupCustomGroup.toJson() = buildJsonObject {
    put("sourceId", sourceId)
    put("groupId", groupId)
    put("name", name)
    put("manualOrder", manualOrder)
}

private fun BackupCustomGroupMembership.toJson() = buildJsonObject {
    put("groupId", groupId)
    put("channelId", channelId)
    put("manualOrder", manualOrder)
}

private fun JsonObject.toSource() = BackupSourceDefinition(
    sourceId = requiredString("sourceId"),
    type = enumValue(requiredString("type")),
    displayName = requiredString("displayName"),
    baseLocator = requiredString("baseLocator"),
    enabled = requiredBoolean("enabled"),
)

private fun JsonObject.toGlobalSettings() = BackupGlobalSettings(
    display = DisplayPreferences(compactMediaRows = optionalBoolean("compactMediaRows", false)),
    playback = PlaybackPreferences(
        automaticPictureInPicture = optionalBoolean("automaticPictureInPicture", true),
    ),
    downloads = DownloadPreferences(
        unmeteredNetworkOnly = optionalBoolean("unmeteredNetworkOnly", false),
    ),
)

private fun JsonObject.toSourceSettings() = BackupSourceSettings(
    sourceId = requiredString("sourceId"),
    refreshSchedule = enumValue(requiredString("refreshSchedule")),
    liveOrganizationMode = enumValue(requiredString("liveOrganizationMode")),
)

private fun JsonObject.toCategoryPersonalization() = BackupCategoryPersonalization(
    sourceId = requiredString("sourceId"),
    kind = enumValue(requiredString("kind")),
    categoryKey = requiredString("categoryKey"),
    hidden = optionalBoolean("hidden", false),
    manualOrder = optionalInt("manualOrder"),
)

private fun JsonObject.toChannelPersonalization() = BackupChannelPersonalization(
    sourceId = requiredString("sourceId"),
    channelId = requiredString("channelId"),
    favorite = optionalBoolean("favorite", false),
    hidden = optionalBoolean("hidden", false),
    localName = optionalString("localName"),
    localLogo = optionalString("localLogo"),
    manualOrder = optionalInt("manualOrder"),
)
private fun JsonObject.toMediaFavorite() = BackupMediaFavorite(
    sourceId = requiredString("sourceId"),
    mediaKind = enumValue(requiredString("mediaKind")),
    contentId = requiredString("contentId"),
    addedAt = requiredLong("addedAt"),
)

private fun JsonObject.toLiveCategoryPersonalization() = BackupLiveCategoryPersonalization(
    sourceId = requiredString("sourceId"),
    organizationMode = enumValue(requiredString("organizationMode")),
    categoryId = requiredString("categoryId"),
    hidden = optionalBoolean("hidden", false),
    manualOrder = optionalInt("manualOrder"),
)

private fun JsonObject.toLivePlacementOverride() = BackupLivePlacementOverride(
    sourceId = requiredString("sourceId"),
    categoryId = requiredString("categoryId"),
    channelId = requiredString("channelId"),
    hidden = optionalBoolean("hidden", false),
    manualOrder = optionalInt("manualOrder"),
)

private fun JsonObject.toCustomGroup() = BackupCustomGroup(
    sourceId = requiredString("sourceId"),
    groupId = requiredString("groupId"),
    name = requiredString("name"),
    manualOrder = requiredInt("manualOrder"),
)
private fun JsonObject.toCustomGroupMembership() = BackupCustomGroupMembership(
    groupId = requiredString("groupId"),
    channelId = requiredString("channelId"),
    manualOrder = requiredInt("manualOrder"),
)

private fun JsonObject.requiredString(key: String): String =
    getValue(key).jsonPrimitive.content

private fun JsonObject.requiredInt(key: String): Int =
    getValue(key).jsonPrimitive.intOrNull ?: error("Invalid Int: $key")

private fun JsonObject.requiredLong(key: String): Long =
    getValue(key).jsonPrimitive.longOrNull ?: error("Invalid Long: $key")

private fun JsonObject.requiredBoolean(key: String): Boolean =
    getValue(key).jsonPrimitive.booleanOrNull ?: error("Invalid Boolean: $key")

private fun JsonObject.optionalString(key: String): String? =
    get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.optionalInt(key: String): Int? =
    get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull

private fun JsonObject.optionalBoolean(key: String, default: Boolean): Boolean =
    get(key)?.jsonPrimitive?.booleanOrNull ?: default

private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
    get(key)?.jsonArray ?: JsonArray(emptyList())
private inline fun <T> arrayOf(
    values: List<T>,
    crossinline block: (T) -> JsonObject,
): JsonArray = buildJsonArray {
    values.forEach { add(block(it)) }
}

private inline fun <reified T : Enum<T>> enumValue(value: String): T =
    enumValues<T>().firstOrNull { it.name == value } ?: error("Invalid enum: $value")

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(
    key: String,
    value: String?,
) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(
    key: String,
    value: Int?,
) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}
