from pathlib import Path

path = Path("app/src/main/java/app/ownplay/mobile/feature/settings/ui/LiveManagementScreen.kt")
text = path.read_text()

text = text.replace(
    'private const val UNCATEGORIZED_SCOPE = "__ownplay_uncategorized__"\n',
    'private const val UNCATEGORIZED_SCOPE = "__ownplay_uncategorized__"\n'
    'private const val DESTINATION_HOME = "home"\n'
    'private const val DESTINATION_CATEGORIES = "categories"\n'
    'private const val DESTINATION_CHANNEL_CATEGORIES = "channel_categories"\n'
    'private const val DESTINATION_CHANNELS = "channels"\n',
    1,
)

text = text.replace(
    '    var selectedScope by rememberSaveable { mutableStateOf<String?>(null) }\n'
    '    var orderedCategories by remember { mutableStateOf<List<ManageableLiveCategory>>(emptyList()) }\n',
    '    var destination by rememberSaveable { mutableStateOf(DESTINATION_HOME) }\n'
    '    var selectedScope by rememberSaveable { mutableStateOf<String?>(null) }\n'
    '    var orderedCategories by remember { mutableStateOf<List<ManageableLiveCategory>>(emptyList()) }\n',
    1,
)

start = text.index('    BackHandler(enabled = selectedScope != null)')
end = text.index('\n@Composable\nprivate fun CategoryManagement(', start)
replacement = '''    BackHandler(enabled = destination != DESTINATION_HOME) {
        when (destination) {
            DESTINATION_CHANNELS -> {
                selectedScope = null
                destination = DESTINATION_CHANNEL_CATEGORIES
            }
            else -> {
                selectedScope = null
                destination = DESTINATION_HOME
            }
        }
    }

    when (destination) {
        DESTINATION_HOME -> LiveManagementHome(
            catalog = catalog,
            onBack = onBack,
            onManageCategories = { destination = DESTINATION_CATEGORIES },
            onManageChannels = { destination = DESTINATION_CHANNEL_CATEGORIES },
            modifier = modifier,
        )

        DESTINATION_CATEGORIES -> CategoryManagement(
            catalog = catalog,
            orderedCategories = orderedCategories,
            onBack = { destination = DESTINATION_HOME },
            onToggleCategory = { category ->
                scope.launch { liveRepository.setCategoryHidden(category.sourceId, category.categoryKey, !category.hidden) }
            },
            onMoveCategory = { categoryKey, direction ->
                val currentIds = orderedCategories.map { it.categoryKey }
                val movedIds = ManualOrderPolicy.move(currentIds, categoryKey, direction)
                if (movedIds != currentIds) {
                    val byId = orderedCategories.associateBy { it.categoryKey }
                    orderedCategories = movedIds.mapNotNull(byId::get)
                    val sourceId = catalog.activeSourceId
                    if (sourceId != null) scope.launch {
                        orderMutex.withLock { liveRepository.setCategoryOrder(sourceId, movedIds) }
                    }
                    true
                } else {
                    false
                }
            },
            onResetOrder = {
                val sourceId = catalog.activeSourceId
                val categoryKeys = orderedCategories.map { it.categoryKey }
                orderedCategories = orderedCategories.sortedWith(compareBy({ it.providerOrder }, { it.name.lowercase() }))
                if (sourceId != null) scope.launch {
                    orderMutex.withLock { liveRepository.resetCategoryOrder(sourceId, categoryKeys) }
                }
            },
            modifier = modifier,
        )

        DESTINATION_CHANNEL_CATEGORIES -> ChannelCategoryPicker(
            catalog = catalog,
            onBack = { destination = DESTINATION_HOME },
            onOpenCategory = { categoryKey ->
                selectedScope = categoryKey
                destination = DESTINATION_CHANNELS
            },
            modifier = modifier,
        )

        DESTINATION_CHANNELS -> {
            val selectedCategory = catalog.categories.firstOrNull { it.categoryKey == selectedScope }
            ChannelManagement(
                title = selectedCategory?.name ?: "Uncategorized",
                channels = orderedChannels,
                onBack = {
                    selectedScope = null
                    destination = DESTINATION_CHANNEL_CATEGORIES
                },
                onToggleChannel = { channel ->
                    scope.launch { liveRepository.setChannelHidden(channel.channelId, !channel.hidden) }
                },
                onMoveChannel = { channelId, direction ->
                    val currentIds = orderedChannels.map { it.channelId }
                    val movedIds = ManualOrderPolicy.move(currentIds, channelId, direction)
                    if (movedIds != currentIds) {
                        val byId = orderedChannels.associateBy { it.channelId }
                        orderedChannels = movedIds.mapNotNull(byId::get)
                        scope.launch { orderMutex.withLock { liveRepository.setChannelOrder(movedIds) } }
                        true
                    } else {
                        false
                    }
                },
                onResetOrder = {
                    val channelIds = orderedChannels.map { it.channelId }
                    orderedChannels = orderedChannels.sortedWith(compareBy({ it.providerOrder }, { it.name.lowercase() }))
                    scope.launch { orderMutex.withLock { liveRepository.resetChannelOrder(channelIds) } }
                },
                modifier = modifier,
            )
        }

        else -> {
            selectedScope = null
            destination = DESTINATION_HOME
        }
    }
}

@Composable
private fun LiveManagementHome(
    catalog: LiveManagementCatalog,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onManageChannels: () -> Unit,
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
            OwnPlayPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageCategories),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OwnPlaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = OwnPlayColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Show, hide, and reorder Live categories.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.Accent)
                }
            }

            OwnPlayPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageChannels),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OwnPlaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage channels",
                            style = MaterialTheme.typography.titleMedium,
                            color = OwnPlayColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Choose a category, then show, hide, or reorder its channels.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun ChannelCategoryPicker(
    catalog: LiveManagementCatalog,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable(catalog.activeSourceId) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredCategories = remember(catalog.categories, normalizedQuery) {
        if (normalizedQuery.isBlank()) catalog.categories
        else catalog.categories.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }
    val channelCounts = remember(catalog.channels) {
        catalog.channels.groupingBy { it.categoryKey }.eachCount()
    }
    val uncategorizedCount = channelCounts[null] ?: 0
    val showUncategorized = uncategorizedCount > 0 &&
        (normalizedQuery.isBlank() || "Uncategorized".contains(normalizedQuery, ignoreCase = true))

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(
            title = "Manage channels",
            subtitle = "Choose a category to manage its channels.",
            onBack = onBack,
        )
        if (catalog.activeSourceId == null) {
            OwnPlayStatePanel(
                title = "No active source",
                message = "Select a source first. Channel visibility and order are stored per source.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            singleLine = true,
            label = { Text("Search categories") },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            itemsIndexed(filteredCategories, key = { _, item -> item.categoryKey }) { _, category ->
                val channelCount = channelCounts[category.categoryKey] ?: 0
                OwnPlayPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCategory(category.categoryKey) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OwnPlaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = OwnPlayColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "$channelCount channels",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.Accent)
                    }
                }
            }

            if (showUncategorized) {
                item(key = UNCATEGORIZED_SCOPE) {
                    OwnPlayPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCategory(UNCATEGORIZED_SCOPE) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(OwnPlaySpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Uncategorized",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OwnPlayColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "$uncategorizedCount channels without a provider category",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OwnPlayColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.Accent)
                        }
                    }
                }
            }

            if (filteredCategories.isEmpty() && !showUncategorized) {
                item(key = "no-channel-category-results") {
                    OwnPlayStatePanel(
                        title = "No matching categories",
                        message = "Try another search term.",
                    )
                }
            }
        }
    }
}
'''
text = text[:start] + replacement + text[end:]

text = text.replace(
    '    onBack: () -> Unit,\n    onOpenCategory: (String) -> Unit,\n    onToggleCategory: (ManageableLiveCategory) -> Unit,\n',
    '    onBack: () -> Unit,\n    onToggleCategory: (ManageableLiveCategory) -> Unit,\n',
    1,
)

uncategorized_decl = '''    val uncategorizedVisible = catalog.channels.any { it.categoryKey == null } &&
        (normalizedQuery.isBlank() || "Uncategorized".contains(normalizedQuery, ignoreCase = true))
'''
text = text.replace(uncategorized_decl, '', 1)

text = text.replace(
    '            title = "Manage Live",\n            subtitle = "Categories · hold the grip and drag. Move to an edge to scroll.",\n',
    '            title = "Manage categories",\n            subtitle = "Show, hide, or reorder categories. Drag to an edge to scroll.",\n',
    1,
)

uncategorized_block_start = text.index('            if (uncategorizedVisible) {')
uncategorized_block_end = text.index('            if (filteredCategories.isEmpty() && !uncategorizedVisible) {', uncategorized_block_start)
text = text[:uncategorized_block_start] + text[uncategorized_block_end:]
text = text.replace(
    '            if (filteredCategories.isEmpty() && !uncategorizedVisible) {',
    '            if (filteredCategories.isEmpty()) {',
    1,
)

text = text.replace(
    '''                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenCategory(category.categoryKey) }
                                .padding(end = OwnPlaySpacing.Sm),
                        ) {''',
    '''                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = OwnPlaySpacing.Sm),
                        ) {''',
    1,
)
text = text.replace(
    '                                text = if (category.hidden) "Hidden · Tap for channels" else "Visible · Tap for channels",',
    '                                text = if (category.hidden) "Hidden" else "Visible",',
    1,
)

required = [
    'text = "Manage categories"',
    'text = "Manage channels"',
    'title = "Manage channels"',
    'Choose a category to manage its channels.',
    'text = if (category.hidden) "Hidden" else "Visible"',
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"Missing expected marker after patch: {marker}")

for forbidden in [
    'Hidden · Tap for channels',
    'Visible · Tap for channels',
    '.clickable { onOpenCategory(category.categoryKey) }\n                                .padding(end = OwnPlaySpacing.Sm)',
]:
    if forbidden in text:
        raise SystemExit(f"Forbidden legacy marker remains: {forbidden}")

path.write_text(text)
print(f"Patched {path}")
