package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader("Manage Live", "Categories", onBack)
        if (catalog.activeSourceId == null) {
            OwnPlayStatePanel(
                title = "No active source",
                message = "Select a source first. Category and channel visibility is stored per source.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            if (catalog.channels.any { it.categoryKey == null }) {
                item(key = UNCATEGORIZED_SCOPE) {
                    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).clickable { onOpenCategory(UNCATEGORIZED_SCOPE) }) {
                                Text("Uncategorized", style = MaterialTheme.typography.titleMedium, color = OwnPlayColors.TextPrimary)
                                Text("Manage channels without a provider category", style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge, color = OwnPlayColors.Accent)
                        }
                    }
                }
            }
            itemsIndexed(orderedCategories, key = { _, item -> item.categoryKey }) { _, category ->
                OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).clickable { onOpenCategory(category.categoryKey) }) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium, color = OwnPlayColors.TextPrimary)
                            Text(
                                if (category.hidden) "Hidden category · tap to manage its channels" else "Visible category · tap to manage channels",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OwnPlayColors.TextSecondary,
                            )
                        }
                        TextButton(onClick = { onToggleCategory(category) }) {
                            Text(if (category.hidden) "Show" else "Hide")
                        }
                        DragHandle("Reorder ${category.name}") { direction -> onMoveCategory(category.categoryKey, direction) }
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
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(title, "Channels · long-press and drag ≡ to reorder", onBack)
        if (channels.isEmpty()) {
            OwnPlayStatePanel(
                title = "No channels in this category",
                message = "Refresh the active provider if you expect channels here.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            itemsIndexed(channels, key = { _, item -> item.channelId }) { index, channel ->
                OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (index + 1).toString().padStart(3, '0'),
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
                        DragHandle("Reorder ${channel.name}") { direction -> onMoveChannel(channel.channelId, direction) }
                    }
                }
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
private fun DragHandle(contentDescription: String, onMove: (Int) -> Unit) {
    val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
    Text(
        text = "≡",
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(contentDescription, thresholdPx) {
                var accumulated = 0f
                detectDragGesturesAfterLongPress(
                    onDragCancel = { accumulated = 0f },
                    onDragEnd = { accumulated = 0f },
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
            },
        style = MaterialTheme.typography.headlineMedium,
        color = OwnPlayColors.Accent,
        fontWeight = FontWeight.Bold,
    )
}
