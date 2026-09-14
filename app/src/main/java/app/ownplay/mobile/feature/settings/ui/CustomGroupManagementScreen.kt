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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayPanel
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayStatePanel
import app.ownplay.mobile.design.OwnPlayTopBar
import app.ownplay.mobile.feature.live.domain.LiveCustomGroup
import app.ownplay.mobile.feature.live.domain.LiveCustomGroupPolicy
import app.ownplay.mobile.feature.live.domain.LiveManagementCatalog
import app.ownplay.mobile.feature.live.domain.LiveRepository
import app.ownplay.mobile.feature.live.domain.ManageableLiveChannel
import kotlinx.coroutines.launch

@Composable
internal fun CustomGroupManagementScreen(
    liveRepository: LiveRepository,
    catalog: LiveManagementCatalog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedGroupId by rememberSaveable(catalog.activeSourceId) { mutableStateOf<String?>(null) }
    val selectedGroup = catalog.customGroups.firstOrNull { it.groupId == selectedGroupId }

    BackHandler(enabled = selectedGroupId != null) {
        selectedGroupId = null
    }

    if (selectedGroup != null) {
        CustomGroupMembershipScreen(
            liveRepository = liveRepository,
            catalog = catalog,
            group = selectedGroup,
            onBack = { selectedGroupId = null },
            modifier = modifier,
        )
    } else {
        CustomGroupListScreen(
            liveRepository = liveRepository,
            catalog = catalog,
            onBack = onBack,
            onOpenGroup = { selectedGroupId = it },
            modifier = modifier,
        )
    }
}

@Composable
private fun CustomGroupListScreen(
    liveRepository: LiveRepository,
    catalog: LiveManagementCatalog,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var newGroupName by rememberSaveable(catalog.activeSourceId) { mutableStateOf("") }
    var editingGroupId by rememberSaveable(catalog.activeSourceId) { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable(catalog.activeSourceId) { mutableStateOf("") }
    val normalizedNewName = LiveCustomGroupPolicy.normalizeName(newGroupName)

    Column(modifier = modifier.fillMaxSize()) {
        CustomGroupHeader(
            title = "Custom groups",
            subtitle = "Create local channel groups that survive provider refresh.",
            onBack = onBack,
        )

        if (catalog.activeSourceId == null) {
            OwnPlayStatePanel(
                title = "No active source",
                message = "Select a source first. Custom groups are stored per source.",
                modifier = Modifier.padding(OwnPlaySpacing.Lg),
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it.take(LiveCustomGroupPolicy.MAX_NAME_LENGTH + 20) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("New group") },
            )
            TextButton(
                enabled = normalizedNewName != null,
                onClick = {
                    val sourceId = catalog.activeSourceId
                    val name = normalizedNewName ?: return@TextButton
                    newGroupName = ""
                    scope.launch { liveRepository.createCustomGroup(sourceId, name) }
                },
            ) {
                Text("Create")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            if (catalog.customGroups.isEmpty()) {
                item(key = "no-custom-groups") {
                    OwnPlayStatePanel(
                        title = "No custom groups",
                        message = "Create a group, then choose which Live channels belong to it.",
                    )
                }
            }

            items(catalog.customGroups, key = { it.groupId }) { group ->
                OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OwnPlaySpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
                    ) {
                        if (editingGroupId == group.groupId) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it.take(LiveCustomGroupPolicy.MAX_NAME_LENGTH + 20) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Group name") },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { editingGroupId = null }) {
                                    Text("Cancel")
                                }
                                TextButton(
                                    enabled = LiveCustomGroupPolicy.normalizeName(renameText) != null,
                                    onClick = {
                                        val normalized = LiveCustomGroupPolicy.normalizeName(renameText)
                                            ?: return@TextButton
                                        editingGroupId = null
                                        scope.launch { liveRepository.renameCustomGroup(group.groupId, normalized) }
                                    },
                                ) {
                                    Text("Save")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenGroup(group.groupId) },
                                ) {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = OwnPlayColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${group.channelIds.size} channels",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OwnPlayColors.TextSecondary,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        renameText = group.name
                                        editingGroupId = group.groupId
                                    },
                                ) {
                                    Text("Rename")
                                }
                                TextButton(onClick = { onOpenGroup(group.groupId) }) {
                                    Text("Manage")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomGroupMembershipScreen(
    liveRepository: LiveRepository,
    catalog: LiveManagementCatalog,
    group: LiveCustomGroup,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable(group.groupId) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val channels = remember(catalog.channels, normalizedQuery) {
        if (normalizedQuery.isEmpty()) catalog.channels
        else catalog.channels.filter { channel ->
            channel.name.contains(normalizedQuery, ignoreCase = true)
        }
    }
    val membership = remember(group.channelIds) { group.channelIds.toHashSet() }

    Column(modifier = modifier.fillMaxSize()) {
        CustomGroupHeader(
            title = group.name,
            subtitle = "Add or remove channels from this local group.",
            onBack = onBack,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            singleLine = true,
            label = { Text("Search channels") },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            if (channels.isEmpty()) {
                item(key = "no-custom-group-channel-results") {
                    OwnPlayStatePanel(
                        title = "No matching channels",
                        message = "Try another channel name.",
                    )
                }
            }

            items(channels, key = { it.channelId }) { channel ->
                CustomGroupChannelRow(
                    channel = channel,
                    included = channel.channelId in membership,
                    onToggle = {
                        val included = channel.channelId in membership
                        scope.launch {
                            liveRepository.setCustomGroupMembership(
                                groupId = group.groupId,
                                channelId = channel.channelId,
                                included = !included,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomGroupChannelRow(
    channel: ManageableLiveChannel,
    included: Boolean,
    onToggle: () -> Unit,
) {
    OwnPlayPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OwnPlaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        channel.hidden -> "Hidden from Live browsing"
                        included -> "Included in this group"
                        else -> "Not in this group"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnPlayColors.TextSecondary,
                )
            }
            TextButton(onClick = onToggle) {
                Text(if (included) "Remove" else "Add")
            }
        }
    }
}

@Composable
private fun CustomGroupHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Column {
        OwnPlayTopBar(showTagline = false)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OwnPlaySpacing.Sm),
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = OwnPlayColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnPlayColors.TextSecondary,
                )
            }
        }
    }
}