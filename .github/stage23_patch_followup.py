from pathlib import Path

path = Path("app/src/main/java/app/ownplay/mobile/feature/settings/ui/DownloadManagementScreen.kt")
text = path.read_text()
old = '''                    errorMessage = when (val result = downloadRepository.remove(item.downloadId)) {
                        is DownloadOperationResult.Failure -> result.safeMessage
                        is DownloadOperationResult.Success -> null
                    }
'''
new = '''                    errorMessage = when (val result = downloadRepository.remove(item.downloadId)) {
                        is DownloadOperationResult.Failure -> result.safeMessage
                        is DownloadOperationResult.Success -> {
                            libraryVisibilityPreferences.showDownload(item.downloadId)
                            null
                        }
                    }
'''
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one delete-result block, found {text.count(old)}")
path.write_text(text.replace(old, new, 1))
