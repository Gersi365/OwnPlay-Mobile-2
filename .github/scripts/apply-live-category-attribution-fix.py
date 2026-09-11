from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one guarded match, found {count}")
    path.write_text(text.replace(old, new, 1))


# 1) Let the Xtream client request a category-scoped live-stream list.
client_api = Path("app/src/main/java/app/ownplay/mobile/sources/data/xtream/XtreamClient.kt")
replace_once(
    client_api,
    "    suspend fun liveStreams(baseUrl: String, credential: SourceCredential.Xtream): XtreamResult<List<XtreamLiveStream>>\n",
    "    suspend fun liveStreams(\n"
    "        baseUrl: String,\n"
    "        credential: SourceCredential.Xtream,\n"
    "        categoryId: String? = null,\n"
    "    ): XtreamResult<List<XtreamLiveStream>>\n",
)

# 2) Build get_live_streams&category_id=... when requested and normalize provider ids.
client_impl = Path("app/src/main/java/app/ownplay/mobile/sources/data/xtream/OkHttpXtreamClient.kt")
replace_once(
    client_impl,
    "    override suspend fun liveStreams(\n"
    "        baseUrl: String,\n"
    "        credential: SourceCredential.Xtream,\n"
    "    ): XtreamResult<List<XtreamLiveStream>> {\n"
    "        return mapArray(XtreamUrlBuilder.apiUrl(baseUrl, credential, \"get_live_streams\")) { obj, index ->\n",
    "    override suspend fun liveStreams(\n"
    "        baseUrl: String,\n"
    "        credential: SourceCredential.Xtream,\n"
    "        categoryId: String?,\n"
    "    ): XtreamResult<List<XtreamLiveStream>> {\n"
    "        val extra = categoryId\n"
    "            ?.trim()\n"
    "            ?.takeIf(String::isNotEmpty)\n"
    "            ?.let { mapOf(\"category_id\" to it) }\n"
    "            .orEmpty()\n"
    "        return mapArray(\n"
    "            XtreamUrlBuilder.apiUrl(baseUrl, credential, \"get_live_streams\", extra = extra),\n"
    "        ) { obj, index ->\n",
)
text = client_impl.read_text()
old_category_parse = '                categoryId = obj["category_id"]?.text(),\n'
if text.count(old_category_parse) != 3:
    raise SystemExit(f"{client_impl}: expected three category_id stream parses")
text = text.replace(
    old_category_parse,
    '                categoryId = obj["category_id"]?.text()?.trim()?.takeIf(String::isNotEmpty),\n',
)
client_impl.write_text(text)
replace_once(
    client_impl,
    '        val key = obj["category_id"]?.text()?.takeIf(String::isNotBlank) ?: return@mapArray null\n',
    '        val key = obj["category_id"]?.text()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapArray null\n',
)

# 3) Add a small pure attribution policy, covered by unit tests.
policy = Path("app/src/main/java/app/ownplay/mobile/sources/data/xtream/XtreamLiveCategoryAttribution.kt")
if policy.exists():
    raise SystemExit(f"{policy}: file unexpectedly already exists")
policy.write_text(
    '''package app.ownplay.mobile.sources.data.xtream

object XtreamLiveCategoryAttribution {
    fun normalizeProviderCategoryId(raw: String?): String? =
        raw?.trim()?.takeIf(String::isNotEmpty)

    fun needsRecovery(
        knownCategoryIds: Collection<String>,
        streamCategoryIds: Collection<String?>,
    ): Boolean {
        val known = knownCategoryIds
            .mapNotNull(::normalizeProviderCategoryId)
            .toSet()
        if (known.isEmpty() || streamCategoryIds.isEmpty()) return false
        return streamCategoryIds.none { raw ->
            normalizeProviderCategoryId(raw)?.let(known::contains) == true
        }
    }
}
'''
)

policy_test = Path("app/src/test/java/app/ownplay/mobile/sources/data/xtream/XtreamLiveCategoryAttributionTest.kt")
if policy_test.exists():
    raise SystemExit(f"{policy_test}: file unexpectedly already exists")
policy_test.write_text(
    '''package app.ownplay.mobile.sources.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamLiveCategoryAttributionTest {
    @Test
    fun normalizesProviderCategoryIds() {
        assertEquals("42", XtreamLiveCategoryAttribution.normalizeProviderCategoryId(" 42 "))
        assertEquals(null, XtreamLiveCategoryAttribution.normalizeProviderCategoryId("   "))
    }

    @Test
    fun requestsRecoveryWhenGlobalStreamsHaveNoUsableCategoryMapping() {
        assertTrue(
            XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = listOf("10", "20"),
                streamCategoryIds = listOf(null, "", "999"),
            ),
        )
    }

    @Test
    fun skipsRecoveryWhenAnyGlobalStreamResolvesToProviderCategory() {
        assertFalse(
            XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = listOf("10", "20"),
                streamCategoryIds = listOf(null, " 20 ", "999"),
            ),
        )
    }
}
'''
)

# 4) Recover category attribution only when the global response has zero usable mappings.
loader = Path("app/src/main/java/app/ownplay/mobile/sources/data/SourceCatalogLoader.kt")
replace_once(
    loader,
    "import app.ownplay.mobile.sources.data.xtream.XtreamClient\n",
    "import app.ownplay.mobile.sources.data.xtream.XtreamClient\n"
    "import app.ownplay.mobile.sources.data.xtream.XtreamLiveCategoryAttribution\n"
    "import app.ownplay.mobile.sources.data.xtream.XtreamLiveStream\n",
)
replace_once(
    loader,
    "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n",
    "import kotlinx.coroutines.Dispatchers\n"
    "import kotlinx.coroutines.async\n"
    "import kotlinx.coroutines.awaitAll\n"
    "import kotlinx.coroutines.coroutineScope\n"
    "import kotlinx.coroutines.sync.Semaphore\n"
    "import kotlinx.coroutines.sync.withPermit\n"
    "import kotlinx.coroutines.withContext\n",
)
replace_once(
    loader,
    "        val liveStreamsResult = xtreamClient.liveStreams(source.baseLocator, xtreamCredential)\n",
    "        val globalLiveStreamsResult = xtreamClient.liveStreams(source.baseLocator, xtreamCredential)\n",
)
replace_once(
    loader,
    "        val liveCategoryMap = categoryIdMap(source.sourceId, \"LIVE\", liveCategoriesResult)\n",
    "        val liveStreamsResult = recoverLiveCategoryAttribution(\n"
    "            source = source,\n"
    "            credential = xtreamCredential,\n"
    "            categoriesResult = liveCategoriesResult,\n"
    "            streamsResult = globalLiveStreamsResult,\n"
    "        )\n"
    "        val liveCategoryMap = categoryIdMap(source.sourceId, \"LIVE\", liveCategoriesResult)\n",
)
replace_once(
    loader,
    "                        categoryKey = stream.categoryId?.let(liveCategoryMap::get),\n",
    "                        categoryKey = XtreamLiveCategoryAttribution\n"
    "                            .normalizeProviderCategoryId(stream.categoryId)\n"
    "                            ?.let(liveCategoryMap::get),\n",
)
replace_once(
    loader,
    "                        categoryKey = movie.categoryId?.let(vodCategoryMap::get),\n",
    "                        categoryKey = XtreamLiveCategoryAttribution\n"
    "                            .normalizeProviderCategoryId(movie.categoryId)\n"
    "                            ?.let(vodCategoryMap::get),\n",
)
replace_once(
    loader,
    "                        categoryKey = item.categoryId?.let(seriesCategoryMap::get),\n",
    "                        categoryKey = XtreamLiveCategoryAttribution\n"
    "                            .normalizeProviderCategoryId(item.categoryId)\n"
    "                            ?.let(seriesCategoryMap::get),\n",
)

recovery_method = '''    private suspend fun recoverLiveCategoryAttribution(
        source: Source,
        credential: SourceCredential.Xtream,
        categoriesResult: XtreamResult<List<app.ownplay.mobile.sources.data.xtream.XtreamCategory>>,
        streamsResult: XtreamResult<List<XtreamLiveStream>>,
    ): XtreamResult<List<XtreamLiveStream>> {
        val categoryRows = (categoriesResult as? XtreamResult.Success)?.value
            ?.filterNot { category -> ProviderCategoryVisibility.isUtilityLabel(category.name) }
            ?: return streamsResult
        val globalRows = (streamsResult as? XtreamResult.Success)?.value ?: return streamsResult
        val categoryIds = categoryRows
            .mapNotNull { category ->
                XtreamLiveCategoryAttribution.normalizeProviderCategoryId(category.providerKey)
            }
            .distinct()
        val normalizedGlobalRows = globalRows.map { stream ->
            stream.copy(
                categoryId = XtreamLiveCategoryAttribution.normalizeProviderCategoryId(stream.categoryId),
            )
        }

        if (!XtreamLiveCategoryAttribution.needsRecovery(
                knownCategoryIds = categoryIds,
                streamCategoryIds = normalizedGlobalRows.map(XtreamLiveStream::categoryId),
            )
        ) {
            return XtreamResult.Success(normalizedGlobalRows)
        }

        val categoryResults = coroutineScope {
            val requestGate = Semaphore(XTREAM_CATEGORY_RECOVERY_CONCURRENCY)
            categoryIds.map { categoryId ->
                async {
                    requestGate.withPermit {
                        val rows = when (
                            val result = xtreamClient.liveStreams(
                                baseUrl = source.baseLocator,
                                credential = credential,
                                categoryId = categoryId,
                            )
                        ) {
                            is XtreamResult.Success -> result.value
                            is XtreamResult.Failure -> emptyList()
                        }
                        categoryId to rows
                    }
                }
            }.awaitAll()
        }

        // Some Xtream servers ignore category_id and return the complete catalog for every request.
        // Only attribute a stream when category-scoped responses place that stream in exactly one category.
        val membershipByStreamId = linkedMapOf<String, MutableSet<String>>()
        val sampleByStreamId = linkedMapOf<String, XtreamLiveStream>()
        categoryResults.forEach { (categoryId, rows) ->
            rows.forEach { stream ->
                membershipByStreamId.getOrPut(stream.streamId) { linkedSetOf() }.add(categoryId)
                sampleByStreamId.putIfAbsent(stream.streamId, stream)
            }
        }
        val recoveredCategoryByStreamId = membershipByStreamId.mapNotNull { (streamId, memberships) ->
            memberships.singleOrNull()?.let { categoryId -> streamId to categoryId }
        }.toMap()
        if (recoveredCategoryByStreamId.isEmpty()) {
            return XtreamResult.Success(normalizedGlobalRows)
        }

        val knownCategoryIds = categoryIds.toSet()
        val mergedIds = linkedSetOf<String>()
        val merged = normalizedGlobalRows.map { stream ->
            mergedIds += stream.streamId
            val existingCategory = stream.categoryId?.takeIf(knownCategoryIds::contains)
            stream.copy(
                categoryId = existingCategory ?: recoveredCategoryByStreamId[stream.streamId],
            )
        }.toMutableList()

        recoveredCategoryByStreamId.forEach { (streamId, categoryId) ->
            if (mergedIds.add(streamId)) {
                sampleByStreamId[streamId]?.let { sample ->
                    merged += sample.copy(categoryId = categoryId)
                }
            }
        }
        return XtreamResult.Success(merged)
    }

'''
replace_once(
    loader,
    "    private fun categoryIdMap(\n",
    recovery_method + "    private fun categoryIdMap(\n",
)
replace_once(
    loader,
    "            .associate { category ->\n"
    "                category.providerKey to StableIdentity.categoryId(sourceId, kind, category.providerKey)\n"
    "            }\n",
    "            .mapNotNull { category ->\n"
    "                val providerKey = XtreamLiveCategoryAttribution\n"
    "                    .normalizeProviderCategoryId(category.providerKey)\n"
    "                    ?: return@mapNotNull null\n"
    "                providerKey to StableIdentity.categoryId(sourceId, kind, providerKey)\n"
    "            }\n"
    "            .toMap()\n",
)
replace_once(
    loader,
    "    private fun failedPayload(code: String): ProviderRefreshPayload = ProviderRefreshPayload(\n",
    "    private companion object {\n"
    "        const val XTREAM_CATEGORY_RECOVERY_CONCURRENCY = 4\n"
    "    }\n\n"
    "    private fun failedPayload(code: String): ProviderRefreshPayload = ProviderRefreshPayload(\n",
)

# Final guard: source-only files expected, no build/version changes.
expected = {
    "app/src/main/java/app/ownplay/mobile/sources/data/SourceCatalogLoader.kt",
    "app/src/main/java/app/ownplay/mobile/sources/data/xtream/OkHttpXtreamClient.kt",
    "app/src/main/java/app/ownplay/mobile/sources/data/xtream/XtreamClient.kt",
    "app/src/main/java/app/ownplay/mobile/sources/data/xtream/XtreamLiveCategoryAttribution.kt",
    "app/src/test/java/app/ownplay/mobile/sources/data/xtream/XtreamLiveCategoryAttributionTest.kt",
}
for file_name in expected:
    if not Path(file_name).exists():
        raise SystemExit(f"missing expected patched file: {file_name}")
print("Patched files:")
for file_name in sorted(expected):
    print(file_name)
