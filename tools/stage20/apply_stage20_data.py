from pathlib import Path
import shutil
import sys

staging = Path(sys.argv[1])


def replace_once(path_str: str, old: str, new: str) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path_str}: expected 1 occurrence, found {count}")
    path.write_text(text.replace(old, new, 1))


def remove_once(path_str: str, block: str) -> None:
    replace_once(path_str, block, "")


daos = "app/src/main/java/app/ownplay/mobile/data/db/Daos.kt"
downloads = "app/src/main/java/app/ownplay/mobile/downloads/data/DownloadRepositoryImpl.kt"
services = "app/src/main/java/app/ownplay/mobile/core/OwnPlayServices.kt"

replace_once(
    daos,
    """    fun observeIncompleteProgress(sourceId: String): Flow<List<PlaybackProgressEntity>>

    @Query(
""",
    """    fun observeIncompleteProgress(sourceId: String): Flow<List<PlaybackProgressEntity>>

    @Query(
        \"\"\"
        SELECT * FROM playback_progress
        WHERE completed = 0
          AND positionMs > 0
        ORDER BY updatedAt DESC, sourceId ASC, mediaKind ASC, contentId ASC
        \"\"\",
    )
    fun observeAllIncompleteProgress(): Flow<List<PlaybackProgressEntity>>

    @Query(
""",
)
replace_once(
    daos,
    """@Dao
interface DownloadDao {
    @Query(
""",
    """@Dao
interface DownloadDao {
    @Query(
        \"\"\"
        SELECT * FROM downloads
        ORDER BY createdAt DESC, downloadId ASC
        \"\"\",
    )
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query(
""",
)

for import_line in (
    "import app.ownplay.mobile.sources.domain.SourceRepository\n",
    "import kotlinx.coroutines.ExperimentalCoroutinesApi\n",
    "import kotlinx.coroutines.flow.flatMapLatest\n",
    "import kotlinx.coroutines.flow.flowOf\n",
):
    remove_once(downloads, import_line)

replace_once(
    downloads,
    """    private val sourceRepository: SourceRepository,
    private val downloadDao: DownloadDao,
""",
    """    private val downloadDao: DownloadDao,
""",
)
replace_once(
    downloads,
    """    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDownloads(): Flow<List<DownloadItem>> =
        sourceRepository.observeActiveSource().flatMapLatest { source ->
            if (source == null) {
                flowOf(emptyList())
            } else {
                combine(
                    downloadDao.observeForSource(source.sourceId),
                    libraryDao.observeIncompleteProgress(source.sourceId),
                ) { downloads, progress ->
                    val progressByKey = progress.associateBy { row ->
                        ProgressKey(row.mediaKind.uppercase(Locale.US), row.contentId)
                    }
                    DownloadOrderingPolicy.ordered(
                        downloads.mapNotNull { row ->
                            row.toDomainOrNull(
                                progress = progressByKey[
                                    ProgressKey(row.mediaKind.uppercase(Locale.US), row.contentId)
                                ],
                            )
                        },
                    )
                }
            }
        }
""",
    """    override fun observeDownloads(): Flow<List<DownloadItem>> =
        combine(
            downloadDao.observeAll(),
            libraryDao.observeAllIncompleteProgress(),
        ) { downloads, progress ->
            val progressByKey = progress.associateBy { row ->
                ProgressKey(
                    sourceId = row.sourceId,
                    mediaKind = row.mediaKind.uppercase(Locale.US),
                    contentId = row.contentId,
                )
            }
            DownloadOrderingPolicy.ordered(
                downloads.mapNotNull { row ->
                    row.toDomainOrNull(
                        progress = progressByKey[
                            ProgressKey(
                                sourceId = row.sourceId,
                                mediaKind = row.mediaKind.uppercase(Locale.US),
                                contentId = row.contentId,
                            )
                        ],
                    )
                },
            )
        }
""",
)
replace_once(
    downloads,
    """                DownloadDestinationPolicy.movie(
                    title = movie.name,
                    extension = movie.extension.takeUnless { it.isNullOrBlank() }
""",
    """                DownloadDestinationPolicy.movie(
                    title = movie.name,
                    identityKey = "${row.sourceId}:${row.contentId}",
                    extension = movie.extension.takeUnless { it.isNullOrBlank() }
""",
)
replace_once(
    downloads,
    """                DownloadDestinationPolicy.episode(
                    seriesTitle = episode.seriesName,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.title,
                    extension = episode.extension.takeUnless { it.isNullOrBlank() }
""",
    """                DownloadDestinationPolicy.episode(
                    seriesTitle = episode.seriesName,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.title,
                    identityKey = "${row.sourceId}:${row.contentId}",
                    extension = episode.extension.takeUnless { it.isNullOrBlank() }
""",
)
replace_once(
    downloads,
    "    private data class ProgressKey(val mediaKind: String, val contentId: String)\n",
    """    private data class ProgressKey(
        val sourceId: String,
        val mediaKind: String,
        val contentId: String,
    )
""",
)

replace_once(
    services,
    """        DownloadRepositoryImpl(
            context = applicationContext,
            sourceRepository = sourceRepository,
            downloadDao = database.downloadDao(),
""",
    """        DownloadRepositoryImpl(
            context = applicationContext,
            downloadDao = database.downloadDao(),
""",
)

shutil.copyfile(
    staging / "DownloadDestinationPolicy.kt",
    "app/src/main/java/app/ownplay/mobile/downloads/data/DownloadDestinationPolicy.kt",
)
shutil.copyfile(
    staging / "PublicDownloadFileStore.kt",
    "app/src/main/java/app/ownplay/mobile/downloads/data/PublicDownloadFileStore.kt",
)
shutil.copyfile(
    staging / "DownloadDestinationPolicyTest.kt",
    "app/src/test/java/app/ownplay/mobile/downloads/data/DownloadDestinationPolicyTest.kt",
)
shutil.copyfile(
    staging / "STAGE20_PERFORMANCE_DOWNLOAD_ROBUSTNESS_SOURCE_AUDIT.md",
    "docs/audit/STAGE20_PERFORMANCE_DOWNLOAD_ROBUSTNESS_SOURCE_AUDIT.md",
)

print("Stage 20 download/data edits applied.")
