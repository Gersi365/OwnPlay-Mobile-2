package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.feature.live.domain.LiveManagementCatalog
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.live.domain.ManageableLiveCategory
import app.ownplay.mobile.feature.live.domain.ManageableLiveChannel
import app.ownplay.mobile.feature.settings.domain.ManualOrderPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val UNCATEGORIZED_SCOPE = "__ownplay_uncategorized__"

@Composable
fun LiveManagementScreen(
    liveRepository: LiveRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogFlow = remember(liveRepository) { liveRepository.observeManagementCatalog() }
    val catalog by catalogFlow.collectAsState(initial = LiveManagementCatalog())
    val scope = rememberCoroutineScope()
    val orderMutex = remember { Mutex() }
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

    BackHandler(enabled = selectedScope != null) { selectedScope = null }

    if (selectedScope == null) {
        CategoryManagement(
            catalog = catalog,
            orderedCategories = orderedCategories,
            onBack = onBack,
            onOpenCategory = { selectedScope = it },
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
                }
            },
            onResetOrder = {
                val reset = orderedCategories.sortedWith(compareBy({ it.providerOrder }, { it.name.lowercase() }))
                orderedCategories = reset
                val sourceId = catalog.activeSourceId
                if (sourceId != null) scope.launch {
                    orderMutex.withLock { liveRepository.setCategoryOrder(sourceId, reset.map { it.categoryKey }) }
                }
            },
            modifier = modifier,
        )
    } else {
        val selectedCategory = catalog.categories.firstOrNull { it.categoryKey == selectedScope }
        ChannelManagement(
            title = selectedCategory?.name ?: "Uncategorized",
            channels = orderedChannels,
            onBack = { selectedScope = null },
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
                }
            },
            onResetOrder = {
                val reset = orderedChannels.sortedWith(compareBy({ it.providerOrder }, { it.name.lowercase() }))
                orderedChannels = reset
                scope.launch { orderMutex.withLock { liveRepository.setChannelOrder(reset.map { it.channelId }) } }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun CategoryManagement(
    catalog: LiveManagementCatalog,
    orderedCategories: List<ManageableLiveCategory>,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onToggleCategory: (ManageableLiveCategory) -> Unit,
    onMoveCategory: (String, Int) -> Unit,
    onResetOrder: () -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable(catalog.activeSourceId) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredCategories = remember(orderedCategories, normalizedQuery) {
        if (normalizedQuery.isBlank()) orderedCategories
        else orderedCategories.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }
    val uncategorizedVisible = catalog.channels.any { it.categoryKey == null } &&
        (normalizedQuery.isBlank() || "Uncategorized".contains(normalizedQuery, ignoreCase = true))
    val reorderEnabled = normalizedQuery.isBlank()

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(
            title = "Manage Live",
            subtitle = "Categories · hold the ⋮⋮ grip, then drag up or down",
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
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            if (uncategorizedVisible) {
                item(key = UNCATEGORIZED_SCOPE) {
                    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenCategory(UNCATEGORIZED_SCOPE) }
                                .padding(OwnPlaySpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Uncategorized", style = MaterialTheme.typography.titleMedium, color = OwnPlayColors.TextPrimary)
                                Text("Channels without a provider category", style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.Accent)
                        }
                    }
                }
            }

            if (filteredCategories.isEmpty() && !uncategorizedVisible) {
                item(key = "no-category-results") {
                    OwnPlayStatePanel(
                        title = "No matching categories",
                        message = "Try another search term.",
                    )
                }
            }

            itemsIndexed(filteredCategories, key = { _, item -> item.categoryKey }) { _, category ->
                OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).clickable { onOpenCategory(category.categoryKey) }) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium, color = OwnPlayColors.TextPrimary)
                            Text(
                                if (category.hidden) "Hidden · tap to manage channels" else "Visible · tap to manage channels",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OwnPlayColors.TextSecondary,
                            )
                        }
                        TextButton(onClick = { onToggleCategory(category) }) {
                            Text(if (category.hidden) "Show" else "Hide")
                        }
                        DragHandle(
                            contentDescription = "Reorder ${category.name}",
                            enabled = reorderEnabled,
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
    onMoveChannel: (String, Int) -> Unit,
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

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(title, "Channels · hold the ⋮⋮ grip, then drag up or down", onBack)
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

        LazyColumn(
            modifier = Modifier.weight(1f),
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
                OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (absoluteIndex + 1).toString().padStart(3, '0'),
                            modifier = Modifier.padding(end = OwnPlaySpacing.Md),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(channel.name, style = MaterialTheme.typography.titleMedium, color = OwnPlayColors.TextPrimary)
                            Text(
                                if (channel.hidden) "Hidden individually" else "Visible",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OwnPlayColors.TextSecondary,
                            )
                        }
                        TextButton(onClick = { onToggleChannel(channel) }) {
                            Text(if (channel.hidden) "Show" else "Hide")
                        }
                        DragHandle(
                            contentDescription = "Reorder ${channel.name}",
                            enabled = reorderEnabled,
                        ) { direction -> onMoveChannel(channel.channelId, direction) }
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
                    "Hide/Show is immediate. Hold the grip to start reordering."
                } else {
                    "Search is active. Clear it to reorder; Hide/Show still works."
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
        Text(title, style = MaterialTheme.typography.headlineMedium, color = OwnPlayColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
    }
}

@Composable
private fun DragHandle(
    contentDescription: String,
    enabled: Boolean,
    onMove: (Int) -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var dragging by remember(contentDescription) { mutableStateOf(false) }
    val dragModifier = if (enabled) {
        Modifier.pointerInput(contentDescription, thresholdPx) {
            var accumulated = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    accumulated = 0f
                    dragging = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragCancel = {
                    accumulated = 0f
                    dragging = false
                },
                onDragEnd = {
                    accumulated = 0f
                    dragging = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accumulated += dragAmount.y
                    when {
                        accumulated >= thresholdPx -> {
                            onMove(1)
                            accumulated = 0f
                        }
                        accumulated <= -thresholdPx -> {
                            onMove(-1)
                            accumulated = 0f
                        }
                    }
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
                color = if (dragging) OwnPlayColors.AccentSoft else OwnPlayColors.SurfaceElevated,
                shape = RoundedCornerShape(8.dp),
            )
            .semantics { this.contentDescription = contentDescription }
            .then(dragModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⋮⋮",
            style = MaterialTheme.typography.titleLarge,
            color = when {
                dragging -> OwnPlayColors.Accent
                enabled -> OwnPlayColors.TextPrimary
                else -> OwnPlayColors.TextSecondary
            },
            fontWeight = FontWeight.Bold,
        )
    }
}
