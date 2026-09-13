from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

path = Path("app/src/main/java/app/ownplay/mobile/sources/data/SourceRepositoryImpl.kt")
text = path.read_text()
text = replace_once(
    text,
    '''import java.util.UUID
import kotlinx.coroutines.flow.Flow
''',
    '''import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
''',
    "Cancellation import",
)
text = replace_once(
    text,
    '''        val credential = try {
            credentialStore.get(sourceId)
        } catch (_: Exception) {
            return failure("CREDENTIAL_READ_FAILED", "Secure source credentials could not be read.")
        } ?: return failure("CREDENTIAL_MISSING", "Source credentials are unavailable.")

        val attemptAt = nowMillis()
        val payload = try {
            catalogLoader.load(source, credential)
        } catch (_: Exception) {
            return recordFailedRefresh(sourceId, attemptAt, "REFRESH_UNEXPECTED")
        }
''',
    '''        val credential = try {
            credentialStore.get(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failure("CREDENTIAL_READ_FAILED", "Secure source credentials could not be read.")
        } ?: return failure("CREDENTIAL_MISSING", "Source credentials are unavailable.")

        val attemptAt = nowMillis()
        val payload = try {
            catalogLoader.load(source, credential)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return recordFailedRefresh(sourceId, attemptAt, "REFRESH_UNEXPECTED")
        }
''',
    "Refresh cancellation propagation",
)
text = replace_once(
    text,
    '''        } catch (_: Exception) {
            failure("REFRESH_PERSIST_FAILED", "Source refresh data could not be committed.")
        }
    }
''',
    '''        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure("REFRESH_PERSIST_FAILED", "Source refresh data could not be committed.")
        }
    }
''',
    "Persistence cancellation propagation",
)
path.write_text(text)
print("Stage 30 cancellation hardening applied")
