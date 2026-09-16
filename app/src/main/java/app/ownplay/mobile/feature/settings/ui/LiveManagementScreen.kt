package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.feature.live.domain.LiveManagementCatalog
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.LivePersonalizationPolicy
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.live.domain.ManageableLiveCategory
import app.ownplay.mobile.feature.live.domain.ManageableLiveChannel
import app.ownplay.mobile.feature.settings.domain.ManualOrderPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val UNCATEGORIZED_SCOPE = "__ownplay_uncategorized__"
private const val DESTINATION_HOME = "home"
private const val DESTINATION_ORGANIZATION = "organization"
private const val DESTINATION_CATEGORIES = "categories"
private const val DESTINATION_CHANNEL_CATEGORIES = "channel_categories"
private const val DESTINATION_CHANNELS = "channels"
private const val DESTINATION_CUSTOM_GROUPS = "custom_groups"

private class ReorderVisualState {
    var dragging by mutableStateOf(false)
    var offsetY by mutableFloatStateOf(0f)
    var itemHeightPx by mutableFloatStateOf(0f)
    var autoScrollStepPx by mutableFloatStateOf(0f)
}

@Composable
private fun rememberReorderVisualState(key: String): ReorderVisualState =
    remember(key) { ReorderVisualState() }

private fun Modifier.reorderVisual(state: ReorderVisualState): Modifier =
    this
        .onSizeChanged { state.itemHeightPx = it.height.toFloat() }
        .zIndex(if (state.dragging) 1f else 0f)
        .graphicsLayer { translationY = state.offsetY }

private fun settleReorderOffset(
    state: ReorderVisualState,
    itemGapPx: Float,
    onMove: (Int) -> Boolean,
) {
    val moveDistance = (state.itemHeightPx + itemGapPx).coerceAtLeast(1f)
    while (state.offsetY >= moveDistance) {
        if (onMove(1)) {
            state.offsetY -= moveDistance
        } else {
            state.offsetY = moveDistance * 0.35f
            break
        }
    }
    while (state.offsetY <= -moveDistance) {
        if (onMove(-1)) {
            state.offsetY += moveDistance
        } else {
            state.offsetY = -moveDistance * 0.35f
            break
        }
    }
}

@Composable
fun LiveManagementScreen(
    liveRepository: LiveRepository,
    liveOrganizationRepository: LiveOrganizationRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(liveRepository) { liveRepository.observeManagementCatalog() }
    val catalog by catalogFlow.collectAsState(initial = LiveManagementCatalog())
    val scope = rememberCoroutineScope()
    val orderMutex = remember { Mutex() }
    var destination by rememberSaveable { mutableStateOf(DESTINATION_HOME) }
    var selectedScope by rememberSaveable { mutableStateOf<String?>(null) }
    var orderedCategories by remember { mutableStateOf<List<ManageableLiveCategory>>(emptyList()) }
    var orderedChannels by remember { mutableStateOf<List<ManageableLiveChannel>>(emptyList()) }

    LaunchedEffect(catalog.activeSourceId, catalog.categories) {
        orderedCategories = catalog.categories
    }
    LaunchedEffect(catalog.activeSourceId, catalog.channels, selectedScope) {
        orderedChannels = when (selectedScope) {
            null -> emptyList()
            UNCATEGORIZED_SCOPE -> catalog.channels.filter { it.categoryKey == null }
            else -> catalog.channels.filter { it.categoryKey == selectedScope }
        }
    }

    BackHandler(enabled = destination != DESTINATION_HOME) {
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
            onManageOrganization = { destination = DESTINATION_ORGANIZATION },
            onManageCategories = { destination = DESTINATION_CATEGORIES },
            onManageChannels = { destination = DESTINATION_CHANNEL_CATEGORIES },
            onManageCustomGroups = { destination = DESTINATION_CUSTOM_GROUPS },
            modifier = modifier,
        )

        DESTINATION_ORGANIZATION -> LiveOrganizationScreen(
            liveRepository = liveRepository,
            organizationRepository = liveOrganizationRepository,
            onBack = { destination = DESTINATION_HOME },
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
                onToggleFavorite = { channel ->
                    scope.launch { liveRepository.setChannelFavorite(channel.channelId, !channel.favorite) }
                },
                onSavePersonalization = { channelId, localName, localLogo ->
                    scope.launch {
                        liveRepository.setChannelLocalName(channelId, localName)
                        liveRepository.setChannelLocalLogo(channelId, localLogo)
                    }
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

        DESTINATION_CUSTOM_GROUPS -> CustomGroupManagementScreen(
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
    catalog: LiveManagementCatalog,
    onBack: () -> Unit,
    onManageOrganization: () -> Unit,
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
                title = "Live organization",
                summary = "Review discovered OwnPlay categories and choose Provider or OwnPlay per source.",
                onClick = onManageOrganization,
            )
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

@Composable
private fun CategoryManagement(
    catalog: LiveManagementCatalog,
    orderedCategories: List<ManageableLiveCategory>,
    onBack: () -> Unit,
    onToggleCategory: (ManageableLiveCategory) -> Unit,
    onMoveCategory: (String, Int) -> Boolean,
    onResetOrder: () -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable(catalog.activeSourceId) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredCategories = remember(orderedCategories, normalizedQuery) {
        if (normalizedQuery.isBlank()) orderedCategories
        else orderedCategories.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }
    val reorderEnabled = normalizedQuery.isBlank()
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(
            title = "Manage categories",
            subtitle = "Show, hide, or reorder categories. Drag to an edge to scroll.",
            onBack = onBack,
        )
        if (catalog.activeSourceId == null) {
            OwnPlayStatePanel(
                title = "No active source",
                message = "Select a source first. Category and channel visibility is stored per source.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }

        ManagementTools(
            query = query,
            placeholder = "Search categories",
            onQueryChange = { query = it },
            reorderEnabled = reorderEnabled,
            hasManualOrder = orderedCategories.any { it.manualOrder != null },
            onResetOrder = onResetOrder,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            if (filteredCategories.isEmpty()) {
                item(key = "no-category-results") {
                    OwnPlayStatePanel(
                        title = "No matching categories",
                        message = "Try another search term.",
                    )
                }
            }

            itemsIndexed(filteredCategories, key = { _, item -> item.categoryKey }) { _, category ->
                val dragState = rememberReorderVisualState(category.categoryKey)
                OwnPlayPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .reorderVisual(dragState),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = OwnPlaySpacing.Sm),
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = OwnPlayColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (category.hidden) "Hidden" else "Visible",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        VisibilityToggle(
                            visible = !category.hidden,
                            contentDescription = if (category.hidden) {
                                "Show ${category.name} in Live"
                            } else {
                                "Hide ${category.name} from Live"
                            },
                            onToggle = { onToggleCategory(category) },
                        )
                        DragHandle(
                            itemKey = category.categoryKey,
                            contentDescription = "Reorder ${category.name}",
                            enabled = reorderEnabled,
                            state = dragState,
                            listState = listState,
                        ) { direction -> onMoveCategory(category.categoryKey, direction) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelManagement(
    title: String,
    channels: List<ManageableLiveChannel>,
    onBack: () -> Unit,
    onToggleChannel: (ManageableLiveChannel) -> Unit,
    onToggleFavorite: (ManageableLiveChannel) -> Unit,
    onSavePersonalization: (String, String?, String?) -> Unit,
    onMoveChannel: (String, Int) -> Boolean,
    onResetOrder: () -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredChannels = remember(channels, normalizedQuery) {
        if (normalizedQuery.isBlank()) channels
        else channels.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }
    val reorderEnabled = normalizedQuery.isBlank()
    val listState = rememberLazyListState()
    var editChannelId by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var editNameValue by rememberSaveable(title) { mutableStateOf("") }
    var editLogoValue by rememberSaveable(title) { mutableStateOf("") }
    val editLogoValid = LivePersonalizationPolicy.isValidLocalLogoInput(editLogoValue)

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(title, "Channels · hold the grip and drag. Move to an edge to scroll.", onBack)
        if (channels.isEmpty()) {
            OwnPlayStatePanel(
                title = "No channels in this category",
                message = "Refresh the active provider if you expect channels here.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }

        ManagementTools(
            query = query,
            placeholder = "Search channels",
            onQueryChange = { query = it },
            reorderEnabled = reorderEnabled,
            hasManualOrder = channels.any { it.manualOrder != null },
            onResetOrder = onResetOrder,
        )

        editChannelId?.let { channelId ->
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            if (filteredChannels.isEmpty()) {
                item(key = "no-channel-results") {
                    OwnPlayStatePanel(
                        title = "No matching channels",
                        message = "Try another search term.",
                    )
                }
            }

            itemsIndexed(filteredChannels, key = { _, item -> item.channelId }) { _, channel ->
                val absoluteIndex = channels.indexOfFirst { it.channelId == channel.channelId }.coerceAtLeast(0)
                val dragState = rememberReorderVisualState(channel.channelId)
                OwnPlayPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .reorderVisual(dragState),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (absoluteIndex + 1).toString().padStart(3, '0'),
                                modifier = Modifier.padding(end = OwnPlaySpacing.Sm),
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                            )
                            Column(modifier = Modifier.weight(1f).padding(end = OwnPlaySpacing.Sm)) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OwnPlayColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildList {
                                        add(if (channel.hidden) "Hidden" else "Visible")
                                        if (channel.favorite) add("Favorite")
                                        if (channel.localName != null) add("Custom name")
                                        if (channel.localLogo != null) add("Custom logo")
                                    }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OwnPlayColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            VisibilityToggle(
                                visible = !channel.hidden,
                                contentDescription = if (channel.hidden) {
                                    "Show ${channel.name} in Live"
                                } else {
                                    "Hide ${channel.name} from Live"
                                },
                                onToggle = { onToggleChannel(channel) },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { onToggleFavorite(channel) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = if (channel.favorite) {
                                            "Remove ${channel.name} from favorites"
                                        } else {
                                            "Add ${channel.name} to favorites"
                                        }
                                    },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    if (channel.favorite) "★" else "☆",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (channel.favorite) OwnPlayColors.Accent else OwnPlayColors.TextSecondary,
                                )
                            }
                            TextButton(
                                onClick = {
                                    editChannelId = channel.channelId
                                    editNameValue = channel.localName.orEmpty()
                                    editLogoValue = channel.localLogo.orEmpty()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = "Edit ${channel.name}" },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    "✎",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OwnPlayColors.TextSecondary,
                                )
                            }
                            DragHandle(
                                itemKey = channel.channelId,
                                contentDescription = "Reorder ${channel.name}",
                                enabled = reorderEnabled,
                                state = dragState,
                                listState = listState,
                            ) { direction -> onMoveChannel(channel.channelId, direction) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementTools(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    reorderEnabled: Boolean,
    hasManualOrder: Boolean,
    onResetOrder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OwnPlaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(placeholder) },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (reorderEnabled) {
                    "Visibility changes are immediate. Hold the grip to reorder; drag to an edge to scroll."
                } else {
                    "Search is active. Clear it to reorder; visibility still works."
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextSecondary,
            )
            if (hasManualOrder && reorderEnabled) {
                TextButton(onClick = onResetOrder) { Text("Reset order") }
            }
        }
    }
}

@Composable
private fun ManagementHeader(title: String, subtitle: String, onBack: () -> Unit) {
    OwnPlayTopBar(showTagline = false)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
    ) {
        Text("‹ Settings", modifier = Modifier.clickable(onClick = onBack), style = MaterialTheme.typography.labelLarge, color = OwnPlayColors.Accent)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = OwnPlayColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OwnPlayColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VisibilityToggle(
    visible: Boolean,
    contentDescription: String,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .toggleable(
                value = visible,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(if (visible) OwnPlayColors.AccentStrong else OwnPlayColors.Divider)
                .padding(3.dp),
            contentAlignment = if (visible) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(OwnPlayColors.TextPrimary),
            )
        }
    }
}

@Composable
private fun DragHandle(
    itemKey: String,
    contentDescription: String,
    enabled: Boolean,
    state: ReorderVisualState,
    listState: LazyListState,
    onMove: (Int) -> Boolean,
) {
    val density = LocalDensity.current
    val itemGapPx = with(density) { OwnPlaySpacing.Sm.toPx() }
    val edgeZonePx = with(density) { 72.dp.toPx() }
    val maxAutoScrollStepPx = with(density) { 12.dp.toPx() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.dragging, state.autoScrollStepPx, listState) {
        while (state.dragging && state.autoScrollStepPx != 0f) {
            val consumed = listState.scrollBy(state.autoScrollStepPx)
            if (consumed == 0f) {
                state.autoScrollStepPx = 0f
                break
            }
            state.offsetY += consumed
            settleReorderOffset(state, itemGapPx, onMove)
            delay(16)
        }
    }

    fun updateEdgeAutoScroll() {
        if (!state.dragging) {
            state.autoScrollStepPx = 0f
            return
        }
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == itemKey }
        if (item == null) {
            state.autoScrollStepPx = 0f
            return
        }
        val centerY = item.offset + (item.size / 2f) + state.offsetY
        val topEdge = layout.viewportStartOffset + edgeZonePx
        val bottomEdge = layout.viewportEndOffset - edgeZonePx
        state.autoScrollStepPx = when {
            centerY < topEdge -> -maxAutoScrollStepPx * ((topEdge - centerY) / edgeZonePx).coerceIn(0.2f, 1f)
            centerY > bottomEdge -> maxAutoScrollStepPx * ((centerY - bottomEdge) / edgeZonePx).coerceIn(0.2f, 1f)
            else -> 0f
        }
    }

    val dragModifier = if (enabled) {
        Modifier.pointerInput(itemKey, enabled) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    state.offsetY = 0f
                    state.autoScrollStepPx = 0f
                    state.dragging = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragCancel = {
                    state.offsetY = 0f
                    state.autoScrollStepPx = 0f
                    state.dragging = false
                },
                onDragEnd = {
                    state.offsetY = 0f
                    state.autoScrollStepPx = 0f
                    state.dragging = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.offsetY += dragAmount.y
                    settleReorderOffset(state, itemGapPx, onMove)
                    updateEdgeAutoScroll()
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                color = if (state.dragging) OwnPlayColors.AccentSoft else OwnPlayColors.SurfaceElevated,
                shape = RoundedCornerShape(10.dp),
            )
            .semantics { this.contentDescription = contentDescription }
            .then(dragModifier),
        contentAlignment = Alignment.Center,
    ) {
        DragGripDots(active = state.dragging, enabled = enabled)
    }
}

@Composable
private fun DragGripDots(active: Boolean, enabled: Boolean) {
    val dotColor = when {
        active -> OwnPlayColors.Accent
        enabled -> OwnPlayColors.TextPrimary
        else -> OwnPlayColors.TextSecondary
    }
    Canvas(modifier = Modifier.size(22.dp)) {
        val radius = 1.7.dp.toPx()
        val xs = listOf(size.width * 0.36f, size.width * 0.64f)
        val ys = listOf(size.height * 0.28f, size.height * 0.50f, size.height * 0.72f)
        ys.forEach { y ->
            xs.forEach { x ->
                drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
            }
        }
    }
}