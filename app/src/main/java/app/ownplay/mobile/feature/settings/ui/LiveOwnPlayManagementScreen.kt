package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlayPrimaryButton
import app.ownplay.mobile.design.OwnPlaySecondaryButton
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.feature.live.domain.LiveCategoryPersonalizationKey
import app.ownplay.mobile.feature.live.domain.LiveCategoryScope
import app.ownplay.mobile.feature.live.domain.LiveChannelMembership
import app.ownplay.mobile.feature.live.domain.LiveChannelMembershipPersonalizationKey
import app.ownplay.mobile.feature.live.domain.LiveChannelMembershipScope
import app.ownplay.mobile.feature.live.domain.LiveOrganizationCategory
import app.ownplay.mobile.feature.live.domain.LiveOrganizationManagementCategory
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.live.domain.LiveOrganizationPresentationPolicy
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.LiveOrganizationSnapshot
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayChannelTreatment
import app.ownplay.mobile.feature.live.domain.LiveOwnPlayMembershipEditMode
import app.ownplay.mobile.feature.live.domain.ManageableLiveChannel
import app.ownplay.mobile.feature.settings.domain.ManualOrderPolicy
import kotlinx.coroutines.launch

@Composable
internal fun LiveOwnPlayManagementScreen(
    sourceId: String?,
    sourceName: String?,
    liveRepository: LiveRepository,
    organization: LiveOrganizationSnapshot,
    organizationRepository: LiveOrganizationRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = remember(organization) {
        LiveOrganizationPresentationPolicy.managementCategories(organization)
    }
    var selectedCategoryId by rememberSaveable(sourceId) { mutableStateOf<String?>(null) }
    val selectedCategory = organization.categories.firstOrNull { category ->
        category.mode == LiveOrganizationMode.OWNPLAY && category.categoryId == selectedCategoryId
    }

    BackHandler(enabled = selectedCategoryId != null) { selectedCategoryId = null }

    if (sourceId == null) {
        Column(modifier = modifier.fillMaxSize()) {
            OwnPlayManagementHeader("OwnPlay management", "Manual category corrections", onBack)
            OwnPlayStatePanel(
                title = "No active source",
                message = "Select an active source before editing OwnPlay Categories.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
        }
        return
    }

    if (selectedCategory != null) {
        OwnPlayCategoryMembershipScreen(
            sourceId = sourceId,
            sourceName = sourceName,
            liveRepository = liveRepository,
            organization = organization,
            category = selectedCategory,
            organizationRepository = organizationRepository,
            onBack = { selectedCategoryId = null },
            modifier = modifier,
        )
    } else {
        OwnPlayCategoryManagementList(
            sourceId = sourceId,
            categories = categories,
            organization = organization,
            organizationRepository = organizationRepository,
            onOpenCategory = { selectedCategoryId = it },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun OwnPlayCategoryManagementList(
    sourceId: String,
    categories: List<LiveOrganizationManagementCategory>,
    organization: LiveOrganizationSnapshot,
    organizationRepository: LiveOrganizationRepository,
    onOpenCategory: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val siblingIdsByParent = remember(organization.categories) {
        organization.categories
            .filter { it.mode == LiveOrganizationMode.OWNPLAY }
            .groupBy { it.parentCategoryId }
            .mapValues { (_, siblings) -> siblings.map { it.categoryId } }
    }
    val siblingIndexById = remember(siblingIdsByParent) {
        buildMap {
            siblingIdsByParent.values.forEach { siblingIds ->
                siblingIds.forEachIndexed { index, categoryId -> put(categoryId, index) }
            }
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        OwnPlayManagementHeader(
            title = "OwnPlay Categories",
            subtitle = "Hide or reorder siblings, then open a category to edit channel memberships.",
            onBack = onBack,
        )
        if (categories.isEmpty()) {
            OwnPlayStatePanel(
                title = "No OwnPlay categories",
                message = "Refresh the source to run discovery before editing classifications.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            items(categories, key = { it.category.categoryId }) { row ->
                val category = row.category
                val siblingIds = siblingIdsByParent[category.parentCategoryId].orEmpty()
                val siblingIndex = siblingIndexById[category.categoryId] ?: -1
                OwnPlayCategoryManagementCard(
                    row = row,
                    canMoveUp = siblingIndex > 0,
                    canMoveDown = siblingIndex in 0 until siblingIds.lastIndex,
                    onOpen = { onOpenCategory(category.categoryId) },
                    onToggleHidden = {
                        scope.launch {
                            organizationRepository.setCategoryHidden(
                                LiveCategoryPersonalizationKey(
                                    sourceId = sourceId,
                                    mode = LiveOrganizationMode.OWNPLAY,
                                    categoryId = category.categoryId,
                                ),
                                !category.hidden,
                            )
                        }
                    },
                    onMove = { direction ->
                        val moved = ManualOrderPolicy.move(siblingIds, category.categoryId, direction)
                        if (moved != siblingIds) scope.launch {
                            organizationRepository.setCategoryOrder(
                                LiveCategoryScope(
                                    sourceId = sourceId,
                                    mode = LiveOrganizationMode.OWNPLAY,
                                    parentCategoryId = category.parentCategoryId,
                                ),
                                moved,
                            )
                        }
                    },
                    onResetOrder = {
                        scope.launch {
                            organizationRepository.resetCategoryOrder(
                                LiveCategoryScope(
                                    sourceId = sourceId,
                                    mode = LiveOrganizationMode.OWNPLAY,
                                    parentCategoryId = category.parentCategoryId,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OwnPlayCategoryManagementCard(
    row: LiveOrganizationManagementCategory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onToggleHidden: () -> Unit,
    onMove: (Int) -> Unit,
    onResetOrder: () -> Unit,
) {
    val category = row.category
    OwnPlayPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 12).dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = OwnPlayColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${row.includedChannelCount} memberships · ${if (category.hidden) "Hidden" else "Visible"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
                Text("Open", modifier = Modifier.clickable(onClick = onOpen), color = OwnPlayColors.Accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md)) {
                Text(
                    if (category.hidden) "Show" else "Hide",
                    modifier = Modifier.clickable(onClick = onToggleHidden),
                    color = OwnPlayColors.Accent,
                )
                Text(
                    "↑",
                    modifier = Modifier.clickable(enabled = canMoveUp) { onMove(-1) },
                    color = if (canMoveUp) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                )
                Text(
                    "↓",
                    modifier = Modifier.clickable(enabled = canMoveDown) { onMove(1) },
                    color = if (canMoveDown) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                )
                if (category.manualOrder != null) {
                    Text("Reset order", modifier = Modifier.clickable(onClick = onResetOrder), color = OwnPlayColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun OwnPlayCategoryMembershipScreen(
    sourceId: String,
    sourceName: String?,
    liveRepository: LiveRepository,
    organization: LiveOrganizationSnapshot,
    category: LiveOrganizationCategory,
    organizationRepository: LiveOrganizationRepository,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable(sourceId, category.categoryId) { mutableStateOf("") }
    var selectedIds by remember(sourceId, category.categoryId) { mutableStateOf<Set<String>>(emptySet()) }
    val memberships = remember(organization.memberships, category.categoryId) {
        organization.memberships.filter { membership ->
            membership.mode == LiveOrganizationMode.OWNPLAY &&
                membership.categoryId == category.categoryId &&
                membership.included
        }
    }
    val membershipById = remember(memberships) { memberships.associateBy { it.channelId } }
    val currentMemberIds = remember(memberships) { memberships.map { it.channelId } }
    val memberIndexById = remember(currentMemberIds) {
        currentMemberIds.withIndex().associate { indexed -> indexed.value to indexed.index }
    }
    val memberChannelFlow = remember(liveRepository, sourceId, category.categoryId) {
        liveRepository.observeOwnPlayManageableChannels(sourceId, category.categoryId)
    }
    val memberChannelRows by memberChannelFlow.collectAsState(initial = emptyList())
    val channelById = remember(memberChannelRows) { memberChannelRows.associateBy { it.channelId } }
    val memberChannels = remember(currentMemberIds, channelById) {
        currentMemberIds.mapNotNull(channelById::get)
    }
    val normalizedQuery = query.trim()
    val searchFlow = remember(liveRepository, sourceId, normalizedQuery) {
        liveRepository.searchManageableChannels(sourceId, normalizedQuery)
    }
    val searchChannels by searchFlow.collectAsState(initial = emptyList())
    val visibleChannels = if (normalizedQuery.isBlank()) memberChannels else searchChannels

    Column(modifier = modifier.fillMaxSize()) {
        OwnPlayManagementHeader(
            title = category.displayName,
            subtitle = buildString {
                if (!sourceName.isNullOrBlank()) append(sourceName).append(" · ")
                append("Scoped OwnPlay memberships · ").append(memberships.size).append(" channels")
            },
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(OwnPlaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search channels to add or correct") },
                )
            }
            if (selectedIds.isNotEmpty()) {
                item(key = "bulk-actions") {
                    OwnPlayBulkMembershipActions(
                        selectionCount = selectedIds.size,
                        markerEligible = !category.semanticKey.isNullOrBlank() && category.semanticKey != "COUNTRY",
                        onAction = { action ->
                            val ids = selectedIds.toList()
                            scope.launch {
                                when (action) {
                                    OwnPlayBulkAction.ADD -> organizationRepository.editOwnPlayMemberships(
                                        sourceId,
                                        category.categoryId,
                                        ids,
                                        LiveOwnPlayMembershipEditMode.ADD,
                                    )
                                    OwnPlayBulkAction.REPLACE -> organizationRepository.editOwnPlayMemberships(
                                        sourceId,
                                        category.categoryId,
                                        ids,
                                        LiveOwnPlayMembershipEditMode.REPLACE,
                                    )
                                    OwnPlayBulkAction.REMOVE -> organizationRepository.editOwnPlayMemberships(
                                        sourceId,
                                        category.categoryId,
                                        ids,
                                        LiveOwnPlayMembershipEditMode.REMOVE,
                                    )
                                    OwnPlayBulkAction.NORMAL -> organizationRepository.setOwnPlayChannelTreatment(
                                        sourceId,
                                        category.categoryId,
                                        ids,
                                        LiveOwnPlayChannelTreatment.NORMAL_CHANNEL,
                                    )
                                    OwnPlayBulkAction.MARKER -> organizationRepository.setOwnPlayChannelTreatment(
                                        sourceId,
                                        category.categoryId,
                                        ids,
                                        LiveOwnPlayChannelTreatment.SECTION_MARKER,
                                    )
                                }
                                selectedIds = emptySet()
                            }
                        },
                    )
                }
            }
            if (memberships.any { it.manualOrder != null }) {
                item(key = "reset-channel-order") {
                    OwnPlaySecondaryButton(
                        text = "Reset channel order in ${category.displayName}",
                        onClick = {
                            scope.launch {
                                organizationRepository.resetChannelOrder(
                                    LiveChannelMembershipScope(
                                        sourceId = sourceId,
                                        mode = LiveOrganizationMode.OWNPLAY,
                                        categoryId = category.categoryId,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (visibleChannels.isEmpty()) {
                item(key = "empty") {
                    OwnPlayStatePanel(
                        title = if (normalizedQuery.isBlank()) "No channels in this category" else "No channel matches",
                        message = if (normalizedQuery.isBlank()) {
                            "Search for channels to add them manually."
                        } else {
                            "Try another channel name."
                        },
                    )
                }
            }
            items(visibleChannels, key = { it.channelId }) { channel ->
                val membership = membershipById[channel.channelId]
                OwnPlayMembershipChannelCard(
                    channel = channel,
                    membership = membership,
                    selected = channel.channelId in selectedIds,
                    reorderEnabled = normalizedQuery.isBlank() && membership != null,
                    canMoveUp = (memberIndexById[channel.channelId] ?: -1) > 0,
                    canMoveDown = (memberIndexById[channel.channelId] ?: -1) in 0 until currentMemberIds.lastIndex,
                    onToggleSelected = {
                        selectedIds = selectedIds.toMutableSet().apply {
                            if (!add(channel.channelId)) remove(channel.channelId)
                        }
                    },
                    onToggleHidden = membership?.let { current ->
                        {
                            scope.launch {
                                organizationRepository.setChannelHidden(
                                    LiveChannelMembershipPersonalizationKey(
                                        sourceId = sourceId,
                                        mode = LiveOrganizationMode.OWNPLAY,
                                        categoryId = category.categoryId,
                                        channelId = channel.channelId,
                                    ),
                                    !current.hidden,
                                )
                            }
                        }
                    },
                    onMove = { direction ->
                        val moved = ManualOrderPolicy.move(currentMemberIds, channel.channelId, direction)
                        if (moved != currentMemberIds) scope.launch {
                            organizationRepository.setChannelOrder(
                                LiveChannelMembershipScope(
                                    sourceId = sourceId,
                                    mode = LiveOrganizationMode.OWNPLAY,
                                    categoryId = category.categoryId,
                                ),
                                moved,
                            )
                        }
                    },
                )
            }
        }
    }
}

private enum class OwnPlayBulkAction { ADD, REPLACE, REMOVE, NORMAL, MARKER }

@Composable
private fun OwnPlayBulkMembershipActions(
    selectionCount: Int,
    markerEligible: Boolean,
    onAction: (OwnPlayBulkAction) -> Unit,
) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            Text(
                "$selectionCount selected",
                style = MaterialTheme.typography.titleSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Add keeps existing facets. Replace keeps the selected category path and removes other OwnPlay memberships. Remove excludes this category subtree from future automatic refreshes.",
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextSecondary,
            )
            OwnPlayPrimaryButton("Add membership", { onAction(OwnPlayBulkAction.ADD) }, Modifier.fillMaxWidth())
            OwnPlaySecondaryButton("Replace memberships", { onAction(OwnPlayBulkAction.REPLACE) }, Modifier.fillMaxWidth())
            OwnPlaySecondaryButton("Remove from category", { onAction(OwnPlayBulkAction.REMOVE) }, Modifier.fillMaxWidth())
            OwnPlaySecondaryButton("Treat as normal channel", { onAction(OwnPlayBulkAction.NORMAL) }, Modifier.fillMaxWidth())
            if (markerEligible) {
                OwnPlaySecondaryButton("Treat as section marker", { onAction(OwnPlayBulkAction.MARKER) }, Modifier.fillMaxWidth())
                Text(
                    "Section-marker segmentation is recalculated on the next successful provider refresh. The selected row is excluded from OwnPlay rendering immediately.",
                    style = MaterialTheme.typography.labelSmall,
                    color = OwnPlayColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun OwnPlayMembershipChannelCard(
    channel: ManageableLiveChannel,
    membership: LiveChannelMembership?,
    selected: Boolean,
    reorderEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleSelected: () -> Unit,
    onToggleHidden: (() -> Unit)?,
    onMove: (Int) -> Unit,
) {
    OwnPlayPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelected),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
            ) {
                Text(if (selected) "●" else "○", color = if (selected) OwnPlayColors.Accent else OwnPlayColors.TextMuted)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        channel.localName ?: channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnPlayColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            membership == null -> "Not in this category"
                            membership.hidden -> "In category · hidden here"
                            membership.origin.name == "MANUAL" -> "In category · manual"
                            else -> "In category · automatic"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnPlayColors.TextSecondary,
                    )
                }
            }
            if (membership != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md)) {
                    if (onToggleHidden != null) {
                        Text(
                            if (membership.hidden) "Show here" else "Hide here",
                            modifier = Modifier.clickable(onClick = onToggleHidden),
                            color = OwnPlayColors.Accent,
                        )
                    }
                    if (reorderEnabled) {
                        Text(
                            "↑",
                            modifier = Modifier.clickable(enabled = canMoveUp) { onMove(-1) },
                            color = if (canMoveUp) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                        )
                        Text(
                            "↓",
                            modifier = Modifier.clickable(enabled = canMoveDown) { onMove(1) },
                            color = if (canMoveDown) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnPlayManagementHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    OwnPlayTopBar(showTagline = false)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
    ) {
        Text("‹ Live organization", modifier = Modifier.clickable(onClick = onBack), style = MaterialTheme.typography.labelLarge, color = OwnPlayColors.Accent)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = OwnPlayColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OwnPlayColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
