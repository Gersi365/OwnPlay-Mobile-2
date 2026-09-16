package app.ownplay.mobile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import app.ownplay.mobile.feature.live.domain.LiveClassificationConfidence
import app.ownplay.mobile.feature.live.domain.LiveManagementSource
import app.ownplay.mobile.feature.live.domain.LiveOrganizationMode
import app.ownplay.mobile.feature.live.domain.LiveOrganizationPresentationPolicy
import app.ownplay.mobile.feature.live.domain.LiveOrganizationRepository
import app.ownplay.mobile.feature.live.domain.LiveOrganizationReviewCategory
import app.ownplay.mobile.feature.live.domain.LiveOrganizationReviewSnapshot
import app.ownplay.mobile.feature.live.domain.LiveOrganizationSnapshot
import app.ownplay.mobile.feature.live.domain.LiveRepository
import kotlinx.coroutines.launch

@Composable
fun LiveOrganizationScreen(
    liveRepository: LiveRepository,
    organizationRepository: LiveOrganizationRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceFlow = remember(liveRepository) { liveRepository.observeManagementSource() }
    val managementSource by sourceFlow.collectAsState(initial = LiveManagementSource())
    val sourceId = managementSource.sourceId
    val organization by produceState<LiveOrganizationSnapshot?>(
        initialValue = null,
        organizationRepository,
        sourceId,
    ) {
        val currentSourceId = sourceId
        if (currentSourceId == null) {
            value = null
        } else {
            organizationRepository.observeOrganization(currentSourceId).collect { snapshot ->
                value = snapshot
            }
        }
    }
    val review = remember(organization) {
        organization?.let(LiveOrganizationPresentationPolicy::review)
    }
    val scope = rememberCoroutineScope()
    var reviewVisible by rememberSaveable(sourceId) { mutableStateOf(false) }
    var editorVisible by rememberSaveable(sourceId) { mutableStateOf(false) }

    BackHandler(enabled = reviewVisible || editorVisible) {
        reviewVisible = false
        editorVisible = false
    }

    val currentOrganization = organization
    if (editorVisible && currentOrganization != null) {
        LiveOwnPlayManagementScreen(
            sourceId = sourceId,
            sourceName = managementSource.sourceName,
            liveRepository = liveRepository,
            organization = currentOrganization,
            organizationRepository = organizationRepository,
            onBack = { editorVisible = false },
            modifier = modifier,
        )
        return
    }

    if (reviewVisible && review != null && currentOrganization != null) {
        LiveOrganizationReviewScreen(
            sourceName = managementSource.sourceName,
            review = review,
            activeMode = currentOrganization.activeMode,
            onBack = { reviewVisible = false },
            onEnableOwnPlay = {
                val id = sourceId ?: return@LiveOrganizationReviewScreen
                scope.launch {
                    organizationRepository.setActiveMode(id, LiveOrganizationMode.OWNPLAY)
                    reviewVisible = false
                }
            },
            modifier = modifier,
        )
        return
    }

    LiveOrganizationOverview(
        source = managementSource,
        organization = organization,
        review = review,
        onBack = onBack,
        onUseProvider = {
            val id = sourceId ?: return@LiveOrganizationOverview
            scope.launch { organizationRepository.setActiveMode(id, LiveOrganizationMode.PROVIDER) }
        },
        onReviewOwnPlay = { reviewVisible = true },
        onEditOwnPlay = { editorVisible = true },
        modifier = modifier,
    )
}

@Composable
private fun LiveOrganizationOverview(
    source: LiveManagementSource,
    organization: LiveOrganizationSnapshot?,
    review: LiveOrganizationReviewSnapshot?,
    onBack: () -> Unit,
    onUseProvider: () -> Unit,
    onReviewOwnPlay: () -> Unit,
    onEditOwnPlay: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OrganizationHeader(
            title = "Live organization",
            subtitle = "Choose how channels are grouped for this source.",
            onBack = onBack,
        )
        if (source.sourceId == null) {
            OwnPlayStatePanel(
                title = "No active source",
                message = "Select and refresh a source before configuring Live organization.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = OwnPlaySpacing.Lg,
                vertical = OwnPlaySpacing.Md,
            ),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            item(key = "source") {
                Text(
                    text = source.sourceName ?: "Active source",
                    style = MaterialTheme.typography.labelLarge,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            item(key = "provider") {
                OrganizationModeCard(
                    title = "Provider Categories",
                    summary = "Use the provider's category structure and Provider-mode personalization.",
                    selected = organization?.activeMode != LiveOrganizationMode.OWNPLAY,
                    enabled = true,
                    onClick = onUseProvider,
                )
            }
            item(key = "ownplay") {
                val available = review?.available == true
                val categoryCount = review?.categoryCount ?: 0
                val classifiedChannelCount = review?.classifiedChannelCount ?: 0
                OrganizationModeCard(
                    title = "OwnPlay Categories",
                    summary = when {
                        !available -> "No reviewed OwnPlay organization is available yet. Refresh the source to run discovery."
                        organization?.activeMode == LiveOrganizationMode.OWNPLAY ->
                            "Enabled · $categoryCount categories · $classifiedChannelCount classified channels"
                        else -> "Available · $categoryCount categories · $classifiedChannelCount classified channels"
                    },
                    selected = organization?.activeMode == LiveOrganizationMode.OWNPLAY,
                    enabled = available,
                    onClick = onReviewOwnPlay,
                )
            }
            if (review?.available == true) {
                item(key = "discovery") {
                    DiscoverySummaryPanel(
                        review = review,
                        activeMode = organization?.activeMode ?: LiveOrganizationMode.PROVIDER,
                        onReview = onReviewOwnPlay,
                    )
                }
                item(key = "edit-ownplay") {
                    OwnPlaySecondaryButton(
                        text = "Edit OwnPlay Categories",
                        onClick = onEditOwnPlay,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun OrganizationModeCard(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OwnPlayPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Md),
        ) {
            Text(
                text = if (selected) "●" else "○",
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) OwnPlayColors.Accent else OwnPlayColors.TextMuted,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) OwnPlayColors.TextPrimary else OwnPlayColors.TextMuted,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) OwnPlayColors.TextSecondary else OwnPlayColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun DiscoverySummaryPanel(
    review: LiveOrganizationReviewSnapshot,
    activeMode: LiveOrganizationMode,
    onReview: () -> Unit,
) {
    val countries = review.roots.map { it.displayName }.take(3).joinToString(" · ")
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            Text(
                text = "OwnPlay found an alternative organization",
                style = MaterialTheme.typography.titleMedium,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    if (countries.isNotBlank()) append(countries).append(" · ")
                    append(review.categoryCount).append(" categories · ")
                    append(review.classifiedChannelCount).append(" classified")
                    if (review.mediumConfidenceChannelCount > 0) {
                        append(" · ").append(review.mediumConfidenceChannelCount).append(" need review")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextSecondary,
            )
            OwnPlaySecondaryButton(
                text = if (activeMode == LiveOrganizationMode.OWNPLAY) "Review OwnPlay Categories" else "Review before enabling",
                onClick = onReview,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LiveOrganizationReviewScreen(
    sourceName: String?,
    review: LiveOrganizationReviewSnapshot,
    activeMode: LiveOrganizationMode,
    onBack: () -> Unit,
    onEnableOwnPlay: () -> Unit,
    modifier: Modifier,
) {
    val rows = remember(review) { flattenReview(review.roots) }
    Column(modifier = modifier.fillMaxSize()) {
        OrganizationHeader(
            title = "Review OwnPlay Categories",
            subtitle = sourceName ?: "Discovered Live organization",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = OwnPlaySpacing.Lg,
                vertical = OwnPlaySpacing.Md,
            ),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            item(key = "summary") {
                OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${review.categoryCount} categories · ${review.classifiedChannelCount} classified channels",
                            style = MaterialTheme.typography.titleMedium,
                            color = OwnPlayColors.TextPrimary,
                        )
                        Text(
                            text = if (review.mediumConfidenceChannelCount > 0) {
                                "${review.mediumConfidenceChannelCount} channels have medium-confidence classification evidence."
                            } else {
                                "All persisted automatic memberships are high confidence."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                }
            }
            items(rows, key = { row -> row.categoryId }) { row ->
                ReviewCategoryCard(row)
            }
            item(key = "activation") {
                if (activeMode == LiveOrganizationMode.OWNPLAY) {
                    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "OwnPlay Categories are enabled for this source.",
                            modifier = Modifier.padding(OwnPlaySpacing.Md),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnPlayColors.TextSecondary,
                        )
                    }
                } else {
                    OwnPlayPrimaryButton(
                        text = "Enable OwnPlay Categories",
                        onClick = onEnableOwnPlay,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCategoryCard(category: LiveOrganizationReviewCategory) {
    OwnPlayPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (category.depth * 12).dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OwnPlaySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = OwnPlayColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(category.totalChannelCount).append(" channels")
                    category.confidence?.let { confidence ->
                        append(" · ").append(confidenceLabel(confidence)).append(" confidence")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = OwnPlayColors.TextSecondary,
            )
            if (category.evidenceKeys.isNotEmpty()) {
                Text(
                    text = "Evidence: " + category.evidenceKeys.take(3).joinToString(" · ", transform = ::evidenceLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = OwnPlayColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OrganizationHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    OwnPlayTopBar(showTagline = false)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Xs),
    ) {
        Text(
            text = "‹ Manage Live",
            modifier = Modifier.clickable(onClick = onBack),
            style = MaterialTheme.typography.labelLarge,
            color = OwnPlayColors.Accent,
        )
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun flattenReview(
    roots: List<LiveOrganizationReviewCategory>,
): List<LiveOrganizationReviewCategory> = buildList {
    fun append(category: LiveOrganizationReviewCategory) {
        add(category)
        category.children.forEach(::append)
    }
    roots.forEach(::append)
}

private fun confidenceLabel(confidence: LiveClassificationConfidence): String = when (confidence) {
    LiveClassificationConfidence.LOW -> "Low"
    LiveClassificationConfidence.MEDIUM -> "Medium"
    LiveClassificationConfidence.HIGH -> "High"
}

private fun evidenceLabel(value: String): String = value
    .replace(":", " · ")
    .replace('-', ' ')
