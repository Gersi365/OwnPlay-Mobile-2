package app.ownplay.mobile.feature.settings.backup.data

import app.ownplay.mobile.feature.settings.backup.domain.BackupValidationCode
import app.ownplay.mobile.feature.settings.backup.domain.BackupValidationIssue
import app.ownplay.mobile.feature.settings.backup.domain.BackupValidator
import app.ownplay.mobile.feature.settings.backup.domain.OwnPlayBackupEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

sealed interface BackupDecodeResult {
    data class Success(val envelope: OwnPlayBackupEnvelope) : BackupDecodeResult
    data class Failure(val issues: List<BackupValidationIssue>) : BackupDecodeResult
}

object BackupJsonCodec {
    private val json = Json { prettyPrint = true }
    private val forbiddenSecretKeys = setOf(
        "credentialreference",
        "password",
        "username",
        "playlisturl",
        "epgurl",
        "token",
        "secret",
    )

    fun encode(envelope: OwnPlayBackupEnvelope): String {
        val issues = BackupValidator.validate(envelope)
        require(issues.isEmpty()) { "Backup envelope is invalid: ${issues.first().code}" }
        return json.encodeToString(JsonElement.serializer(), envelope.toJson())
    }

    fun decode(raw: String): BackupDecodeResult {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return failure(BackupValidationCode.MALFORMED_JSON, "$")
        }
        findForbiddenSecretKey(root)?.let { path ->
            return failure(BackupValidationCode.FORBIDDEN_SECRET_FIELD, path)
        }
        val envelope = try {
            root.toBackupEnvelope()
        } catch (_: Exception) {
            return failure(BackupValidationCode.INVALID_FIELD, "$")
        }
        val issues = BackupValidator.validate(envelope)
        return if (issues.isEmpty()) {
            BackupDecodeResult.Success(envelope)
        } else {
            BackupDecodeResult.Failure(issues)
        }
    }

    private fun findForbiddenSecretKey(
        element: JsonElement,
        path: String = "$",
    ): String? = when (element) {
        is JsonObject -> element.entries.firstNotNullOfOrNull { (key, value) ->
            if (key.lowercase() in forbiddenSecretKeys) "$path.$key"
            else findForbiddenSecretKey(value, "$path.$key")
        }
        is JsonArray -> element.withIndex().firstNotNullOfOrNull { (index, value) ->
            findForbiddenSecretKey(value, "$path[$index]")
        }
        else -> null
    }

    private fun failure(
        code: BackupValidationCode,
        path: String,
    ): BackupDecodeResult.Failure = BackupDecodeResult.Failure(
        listOf(BackupValidationIssue(code, path)),
    )
}
