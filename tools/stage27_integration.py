from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# LiveRepositoryImpl.kt
path = Path("app/src/main/java/app/ownplay/mobile/feature/live/data/LiveRepositoryImpl.kt")
text = path.read_text()
text = replace_once(
    text,
    "import app.ownplay.mobile.data.db.CatalogDao\n",
    "import app.ownplay.mobile.data.db.BackupGroupMembershipView\nimport app.ownplay.mobile.data.db.CatalogDao\n",
    "repository backup membership import",
)
text = replace_once(
    text,
    "import app.ownplay.mobile.data.db.ChannelPersonalizationEntity\n",
    "import app.ownplay.mobile.data.db.ChannelPersonalizationEntity\nimport app.ownplay.mobile.data.db.CustomGroupEntity\nimport app.ownplay.mobile.data.db.CustomGroupMembershipEntity\n",
    "repository custom group entity imports",
)
text = replace_once(
    text,
    "import app.ownplay.mobile.feature.live.domain.LiveCatalog\n",
    "import app.ownplay.mobile.feature.live.domain.LiveCatalog\nimport app.ownplay.mobile.feature.live.domain.LiveCustomGroup\nimport app.ownplay.mobile.feature.live.domain.LiveCustomGroupPolicy\n",
    "repository custom group domain imports",
)
text = replace_once(
    text,
    "import java.util.Locale\n",
    "import java.util.Locale\nimport java.util.UUID\n",
    "repository UUID import",
)
text = replace_once(
    text,
    "    private val guideCache = ConcurrentHashMap<String, GuideCacheEntry>()\n",
    '''    private val guideCache = ConcurrentHashMap<String, GuideCacheEntry>()
    private val backupDao = database.backupDao()

    private fun mapCustomGroups(
        groups: List<CustomGroupEntity>,
        memberships: List<BackupGroupMembershipView>,
    ): List<LiveCustomGroup> {
        val channelIdsByGroup = memberships
            .groupBy { row -> row.groupId }
            .mapValues { (_, rows) -> rows.sortedBy { it.manualOrder }.map { it.channelId } }
        return groups.map { group ->
            LiveCustomGroup(
                groupId = group.groupId,
                sourceId = group.sourceId,
                name = group.name,
                manualOrder = group.manualOrder,
                channelIds = channelIdsByGroup[group.groupId].orEmpty(),
            )
        }
    }
''',
    "repository backup dao property",
)
text = replace_once(
    text,
    '''                combine(
                    catalogDao.observeAvailableCategories(source.sourceId, "LIVE"),
                    catalogDao.observeAvailableLiveChannels(source.sourceId),
                ) { categoryRows, channelRows ->
                    LiveCatalog(
''',
    '''                combine(
                    catalogDao.observeAvailableCategories(source.sourceId, "LIVE"),
                    catalogDao.observeAvailableLiveChannels(source.sourceId),
                    backupDao.observeCustomGroups(source.sourceId),
                    backupDao.observeCustomGroupMemberships(source.sourceId),
                ) { categoryRows, channelRows, groupRows, membershipRows ->
                    LiveCatalog(
''',
    "repository browse combine",
)
text = replace_once(
    text,
    '''                        channels = channelRows
                            .filterNot { row -> ProviderCategoryVisibility.isUtilityLabel(row.name) }
                            .map { row ->
                            LiveChannel(
                                channelId = row.channelId,
                                sourceId = row.sourceId,
                                categoryKey = row.categoryKey,
                                name = row.name,
                                logoUrl = row.logoUrl,
                                sortOrder = row.sortOrder,
                                favorite = row.favorite,
                            )
                        },
                    )
''',
    '''                        channels = channelRows
                            .filterNot { row -> ProviderCategoryVisibility.isUtilityLabel(row.name) }
                            .map { row ->
                                LiveChannel(
                                    channelId = row.channelId,
                                    sourceId = row.sourceId,
                                    categoryKey = row.categoryKey,
                                    name = row.name,
                                    logoUrl = row.logoUrl,
                                    sortOrder = row.sortOrder,
                                    favorite = row.favorite,
                                )
                            },
                        customGroups = mapCustomGroups(groupRows, membershipRows),
                    )
''',
    "repository browse custom groups",
)
text = replace_once(
    text,
    '''                combine(
                    catalogDao.observeManageableLiveCategories(source.sourceId),
                    catalogDao.observeManageableLiveChannels(source.sourceId),
                ) { categoryRows, channelRows ->
                    LiveManagementCatalog(
''',
    '''                combine(
                    catalogDao.observeManageableLiveCategories(source.sourceId),
                    catalogDao.observeManageableLiveChannels(source.sourceId),
                    backupDao.observeCustomGroups(source.sourceId),
                    backupDao.observeCustomGroupMemberships(source.sourceId),
                ) { categoryRows, channelRows, groupRows, membershipRows ->
                    LiveManagementCatalog(
''',
    "repository management combine",
)
text = replace_once(
    text,
    '''                        channels = channelRows
                            .filterNot { ProviderCategoryVisibility.isUtilityLabel(it.name) }
                            .map { row ->
                                ManageableLiveChannel(
                                    channelId = row.channelId,
                                    sourceId = row.sourceId,
                                    categoryKey = row.categoryKey,
                                    name = row.name,
                                    logoUrl = row.logoUrl,
                                    providerOrder = row.providerOrder,
                                    favorite = row.favorite,
                                    localName = row.localName,
                                    hidden = row.hidden,
                                    manualOrder = row.manualOrder,
                                )
                            },
                    )
''',
    '''                        channels = channelRows
                            .filterNot { ProviderCategoryVisibility.isUtilityLabel(it.name) }
                            .map { row ->
                                ManageableLiveChannel(
                                    channelId = row.channelId,
                                    sourceId = row.sourceId,
                                    categoryKey = row.categoryKey,
                                    name = row.name,
                                    logoUrl = row.logoUrl,
                                    providerOrder = row.providerOrder,
                                    favorite = row.favorite,
                                    localName = row.localName,
                                    hidden = row.hidden,
                                    manualOrder = row.manualOrder,
                                )
                            },
                        customGroups = mapCustomGroups(groupRows, membershipRows),
                    )
''',
    "repository management custom groups",
)
text = replace_once(
    text,
    "    override suspend fun loadNowNext(channelId: String): LiveNowNext {\n",
    '''    override suspend fun createCustomGroup(sourceId: String, name: String) {
        if (sourceId.isBlank()) return
        val normalizedName = LiveCustomGroupPolicy.normalizeName(name) ?: return
        database.withTransaction {
            if (sourceDao.get(sourceId) == null) return@withTransaction
            val nextOrder = backupDao.getCustomGroupsForSource(sourceId)
                .maxOfOrNull { group -> group.manualOrder }
                ?.plus(1)
                ?: 0
            backupDao.upsertCustomGroups(
                listOf(
                    CustomGroupEntity(
                        groupId = "local-group:${UUID.randomUUID()}",
                        sourceId = sourceId,
                        name = normalizedName,
                        manualOrder = nextOrder,
                    ),
                ),
            )
        }
    }

    override suspend fun renameCustomGroup(groupId: String, name: String) {
        if (groupId.isBlank()) return
        val normalizedName = LiveCustomGroupPolicy.normalizeName(name) ?: return
        database.withTransaction {
            val group = backupDao.getCustomGroup(groupId) ?: return@withTransaction
            backupDao.upsertCustomGroups(listOf(group.copy(name = normalizedName)))
        }
    }

    override suspend fun setCustomGroupMembership(groupId: String, channelId: String, included: Boolean) {
        if (groupId.isBlank() || channelId.isBlank()) return
        database.withTransaction {
            val group = backupDao.getCustomGroup(groupId) ?: return@withTransaction
            if (!backupDao.hasLiveChannel(group.sourceId, channelId)) return@withTransaction
            val memberships = backupDao.getCustomGroupMembershipRows(groupId)
            val existing = memberships.firstOrNull { row -> row.channelId == channelId }
            if (included) {
                if (existing != null) return@withTransaction
                val nextOrder = memberships.maxOfOrNull { row -> row.manualOrder }?.plus(1) ?: 0
                backupDao.upsertCustomGroupMembership(
                    CustomGroupMembershipEntity(
                        groupId = groupId,
                        channelId = channelId,
                        manualOrder = nextOrder,
                    ),
                )
            } else if (existing != null) {
                backupDao.deleteCustomGroupMembership(groupId, channelId)
            }
        }
    }

    override suspend fun loadNowNext(channelId: String): LiveNowNext {
''',
    "repository group mutations",
)
path.write_text(text)

# LiveShell.kt
path = Path("app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt")
text = path.read_text()
text = replace_once(
    text,
    "import app.ownplay.mobile.feature.live.domain.LiveChannel\n",
    "import app.ownplay.mobile.feature.live.domain.LiveChannel\nimport app.ownplay.mobile.feature.live.domain.LiveCustomGroup\n",
    "live shell custom group import",
)
text = replace_once(
    text,
    '''    val channels = catalog?.channels.orEmpty()
    val rawCategories = catalog?.categories.orEmpty()
    val categories = remember(rawCategories) { LiveBrowsePolicy.visibleCategories(rawCategories) }
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val favoriteChannels = remember(channels) { LiveBrowsePolicy.favoriteChannels(channels) }
    val visibleChannels = remember(
        channels,
        favoriteChannels,
        activeCategoryKey,
        favoritesOnly,
        searchActive,
        normalizedSearchQuery,
    ) {
        when {
            searchActive -> channels.filter { channel ->
                channel.name.contains(normalizedSearchQuery, ignoreCase = true)
            }
            favoritesOnly -> favoriteChannels
            else -> activeCategoryKey?.let { key ->
                channels.filter { channel -> channel.categoryKey == key }
            } ?: channels
        }
    }
    val showCategories = !searchActive && (categories.isNotEmpty() || favoriteChannels.isNotEmpty())
''',
    '''    val channels = catalog?.channels.orEmpty()
    val customGroups = catalog?.customGroups.orEmpty()
    val rawCategories = catalog?.categories.orEmpty()
    val categories = remember(rawCategories) { LiveBrowsePolicy.visibleCategories(rawCategories) }
    var selectedCategoryKey by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    var selectedCustomGroupId by remember(catalog?.activeSourceId) { mutableStateOf<String?>(null) }
    val activeCategoryKey = LiveBrowsePolicy.activeCategoryKey(categories, selectedCategoryKey)
    val selectedCustomGroup = remember(customGroups, selectedCustomGroupId) {
        customGroups.firstOrNull { group -> group.groupId == selectedCustomGroupId }
    }
    val normalizedSearchQuery = searchQuery.trim()
    val searchActive = normalizedSearchQuery.isNotEmpty()
    val favoriteChannels = remember(channels) { LiveBrowsePolicy.favoriteChannels(channels) }
    val customGroupChannels = remember(channels, selectedCustomGroup) {
        LiveBrowsePolicy.customGroupChannels(channels, selectedCustomGroup?.channelIds.orEmpty())
    }
    val visibleChannels = remember(
        channels,
        favoriteChannels,
        customGroupChannels,
        activeCategoryKey,
        favoritesOnly,
        selectedCustomGroup,
        searchActive,
        normalizedSearchQuery,
    ) {
        when {
            searchActive -> channels.filter { channel ->
                channel.name.contains(normalizedSearchQuery, ignoreCase = true)
            }
            selectedCustomGroup != null -> customGroupChannels
            favoritesOnly -> favoriteChannels
            else -> activeCategoryKey?.let { key ->
                channels.filter { channel -> channel.categoryKey == key }
            } ?: channels
        }
    }
    val showCategories = !searchActive && (
        categories.isNotEmpty() || favoriteChannels.isNotEmpty() || customGroups.isNotEmpty()
    )
''',
    "live shell browse state",
)
text = replace_once(
    text,
    '''    LaunchedEffect(favoriteChannels.isEmpty(), favoritesOnly) {
        if (favoritesOnly && favoriteChannels.isEmpty()) favoritesOnly = false
    }

''',
    '''    LaunchedEffect(favoriteChannels.isEmpty(), favoritesOnly) {
        if (favoritesOnly && favoriteChannels.isEmpty()) favoritesOnly = false
    }

    LaunchedEffect(customGroups, selectedCustomGroupId) {
        if (selectedCustomGroupId != null && selectedCustomGroup == null) {
            selectedCustomGroupId = null
        }
    }

''',
    "live shell missing group reset",
)
text = replace_once(
    text,
    '''            favoriteCount = favoriteChannels.size,
            favoritesOnly = favoritesOnly,
            liveRepository = liveRepository,
            selectedCategoryKey = activeCategoryKey,
''',
    '''            favoriteCount = favoriteChannels.size,
            favoritesOnly = favoritesOnly,
            customGroups = customGroups,
            selectedCustomGroupId = selectedCustomGroupId,
            liveRepository = liveRepository,
            selectedCategoryKey = activeCategoryKey,
''',
    "live shell group args",
)
text = replace_once(
    text,
    '''            onFavoriteFilterSelected = {
                favoritesOnly = true
                if (
''',
    '''            onFavoriteFilterSelected = {
                selectedCustomGroupId = null
                favoritesOnly = true
                if (
''',
    "live shell favorite clears group",
)
text = replace_once(
    text,
    '''            onCategorySelected = { categoryKey ->
                favoritesOnly = false
                selectedCategoryKey = categoryKey
''',
    '''            onCustomGroupSelected = { groupId ->
                favoritesOnly = false
                selectedCustomGroupId = groupId
                val group = customGroups.firstOrNull { it.groupId == groupId }
                if (
                    presentationState.presentation == LivePresentation.PREVIEW &&
                    selectedChannel?.channelId !in group?.channelIds.orEmpty()
                ) {
                    dispatch(LiveIntent.BackPressed)
                }
            },
            onCategorySelected = { categoryKey ->
                favoritesOnly = false
                selectedCustomGroupId = null
                selectedCategoryKey = categoryKey
''',
    "live shell group selection callback",
)
text = replace_once(
    text,
    '''    favoriteCount: Int,
    favoritesOnly: Boolean,
    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
''',
    '''    favoriteCount: Int,
    favoritesOnly: Boolean,
    customGroups: List<LiveCustomGroup>,
    selectedCustomGroupId: String?,
    liveRepository: LiveRepository,
    selectedCategoryKey: String?,
''',
    "browse preview group params",
)
text = replace_once(
    text,
    '''    onSearchQueryChange: (String) -> Unit,
    onFavoriteFilterSelected: () -> Unit,
    onCategorySelected: (String?) -> Unit,
''',
    '''    onSearchQueryChange: (String) -> Unit,
    onFavoriteFilterSelected: () -> Unit,
    onCustomGroupSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
''',
    "browse preview group callback param",
)
text = replace_once(
    text,
    "    LaunchedEffect(selectedChannel?.channelId, selectedCategoryKey, channels, categories) {\n",
    '''    LaunchedEffect(
        selectedChannel?.channelId,
        selectedCategoryKey,
        selectedCustomGroupId,
        channels,
        categories,
    ) {
''',
    "browse preview scrolling keys",
)
text = replace_once(
    text,
    '''                LiveCategoryStrip(
                    categories = categories,
                    favoriteCount = favoriteCount,
                    favoritesOnly = favoritesOnly,
                    selectedCategoryKey = selectedCategoryKey,
                    onFavoritesSelected = onFavoriteFilterSelected,
                    onSelected = onCategorySelected,
                )
''',
    '''                LiveCategoryStrip(
                    categories = categories,
                    favoriteCount = favoriteCount,
                    favoritesOnly = favoritesOnly,
                    customGroups = customGroups,
                    selectedCustomGroupId = selectedCustomGroupId,
                    selectedCategoryKey = selectedCategoryKey,
                    onFavoritesSelected = onFavoriteFilterSelected,
                    onCustomGroupSelected = onCustomGroupSelected,
                    onSelected = onCategorySelected,
                )
''',
    "category strip group args",
)
pattern = re.compile(
    r"@Composable\nprivate fun LiveCategoryStrip\(.*?\n}\n\n@Composable\nprivate fun PreviewSurface\(",
    re.S,
)
replacement = '''@Composable
private fun LiveCategoryStrip(
    categories: List<LiveCategory>,
    favoriteCount: Int,
    favoritesOnly: Boolean,
    customGroups: List<LiveCustomGroup>,
    selectedCustomGroupId: String?,
    selectedCategoryKey: String?,
    onFavoritesSelected: () -> Unit,
    onCustomGroupSelected: (String) -> Unit,
    onSelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
    ) {
        if (favoriteCount > 0) {
            item(key = "ownplay-favorites") {
                OwnPlayFilterChip(
                    label = "Favorites",
                    selected = favoritesOnly && selectedCustomGroupId == null,
                    onClick = onFavoritesSelected,
                )
            }
        }
        items(customGroups, key = { group -> group.groupId }) { group ->
            OwnPlayFilterChip(
                label = group.name,
                selected = !favoritesOnly && selectedCustomGroupId == group.groupId,
                onClick = { onCustomGroupSelected(group.groupId) },
            )
        }
        items(categories, key = { it.categoryKey }) { category ->
            OwnPlayFilterChip(
                label = category.name,
                selected = !favoritesOnly && selectedCustomGroupId == null && selectedCategoryKey == category.categoryKey,
                onClick = { onSelected(category.categoryKey) },
            )
        }
    }
}

@Composable
private fun PreviewSurface('''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"live shell category strip: expected one function, found {count}")
path.write_text(text)

# LiveManagementScreen.kt
path = Path("app/src/main/java/app/ownplay/mobile/feature/settings/ui/LiveManagementScreen.kt")
text = path.read_text()
text = replace_once(
    text,
    'private const val DESTINATION_CHANNELS = "channels"\n',
    'private const val DESTINATION_CHANNELS = "channels"\nprivate const val DESTINATION_CUSTOM_GROUPS = "custom_groups"\n',
    "management custom group destination",
)
text = replace_once(
    text,
    '''            onManageCategories = { destination = DESTINATION_CATEGORIES },
            onManageChannels = { destination = DESTINATION_CHANNEL_CATEGORIES },
            modifier = modifier,
''',
    '''            onManageCategories = { destination = DESTINATION_CATEGORIES },
            onManageChannels = { destination = DESTINATION_CHANNEL_CATEGORIES },
            onManageCustomGroups = { destination = DESTINATION_CUSTOM_GROUPS },
            modifier = modifier,
''',
    "management home custom group callback",
)
text = replace_once(
    text,
    '''        else -> {
            selectedScope = null
            destination = DESTINATION_HOME
        }
    }
}

@Composable
private fun LiveManagementHome(
''',
    '''        DESTINATION_CUSTOM_GROUPS -> CustomGroupManagementScreen(
            liveRepository = liveRepository,
            catalog = catalog,
            onBack = { destination = DESTINATION_HOME },
            modifier = modifier,
        )

        else -> {
            selectedScope = null
            destination = DESTINATION_HOME
        }
    }
}

@Composable
private fun LiveManagementHome(
''',
    "management custom group route",
)
pattern = re.compile(
    r"@Composable\nprivate fun LiveManagementHome\(.*?\n}\n\n@Composable\nprivate fun ChannelCategoryPicker\(",
    re.S,
)
replacement = '''@Composable
private fun LiveManagementHome(
    catalog: LiveManagementCatalog,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onManageChannels: () -> Unit,
    onManageCustomGroups: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(
            title = "Manage Live",
            subtitle = "Choose what you want to organize.",
            onBack = onBack,
        )
        if (catalog.activeSourceId == null) {
            OwnPlayStatePanel(
                title = "No active source",
                message = "Select a source first. Live management is stored per source.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }

        Column(
            modifier = Modifier.padding(OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            ManagementDestinationCard(
                title = "Manage categories",
                summary = "Show, hide, and reorder Live categories.",
                onClick = onManageCategories,
            )
            ManagementDestinationCard(
                title = "Manage channels",
                summary = "Choose a category, then show, hide, rename, favorite, or reorder its channels.",
                onClick = onManageChannels,
            )
            ManagementDestinationCard(
                title = "Custom groups",
                summary = "Create local channel groups and manage their memberships.",
                onClick = onManageCustomGroups,
            )
        }
    }
}

@Composable
private fun ManagementDestinationCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    OwnPlayPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OwnPlaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.Accent)
        }
    }
}

@Composable
private fun ChannelCategoryPicker('''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"management home function: expected one function, found {count}")
path.write_text(text)
