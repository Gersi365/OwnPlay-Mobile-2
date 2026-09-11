from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('.')
path = root / 'app/src/main/java/app/ownplay/mobile/feature/settings/ui/LiveManagementScreen.kt'
text = path.read_text()

def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'expected one match, got {count}: {old[:100]!r}')
    text = text.replace(old, new, 1)

replace_once(
    'import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n',
    'import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\nimport androidx.compose.foundation.gestures.scrollBy\n',
)
replace_once(
    'import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.itemsIndexed\n',
    'import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyListState\nimport androidx.compose.foundation.lazy.itemsIndexed\nimport androidx.compose.foundation.lazy.rememberLazyListState\n',
)
replace_once(
    'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\n',
    'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.geometry.Offset\n',
)
replace_once('import androidx.compose.ui.text.font.FontWeight\n', 'import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow\n')
replace_once('import kotlinx.coroutines.launch\n', 'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n')
replace_once(
'''private class ReorderVisualState {
    var dragging by mutableStateOf(false)
    var offsetY by mutableFloatStateOf(0f)
    var itemHeightPx by mutableFloatStateOf(0f)
}
''',
'''private class ReorderVisualState {
    var dragging by mutableStateOf(false)
    var offsetY by mutableFloatStateOf(0f)
    var itemHeightPx by mutableFloatStateOf(0f)
    var autoScrollStepPx by mutableFloatStateOf(0f)
}
''')
replace_once(
'''private fun Modifier.reorderVisual(state: ReorderVisualState): Modifier =
    this
        .onSizeChanged { state.itemHeightPx = it.height.toFloat() }
        .zIndex(if (state.dragging) 1f else 0f)
        .graphicsLayer { translationY = state.offsetY }
''',
'''private fun Modifier.reorderVisual(state: ReorderVisualState): Modifier =
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
''')
replace_once(
'''    val reorderEnabled = normalizedQuery.isBlank()

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(
            title = "Manage Live",
            subtitle = "Categories · hold the ⋮⋮ grip, then drag up or down",
''',
'''    val reorderEnabled = normalizedQuery.isBlank()
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(
            title = "Manage Live",
            subtitle = "Categories · hold the grip and drag. Move to an edge to scroll.",
''')
replace_once(
'''        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
''',
'''        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
''')
replace_once(
'''                    Row(
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
                            state = dragState,
                        ) { direction -> onMoveCategory(category.categoryKey, direction) }
                    }
''',
'''                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenCategory(category.categoryKey) }
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
                                text = if (category.hidden) "Hidden · Tap for channels" else "Visible · Tap for channels",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = { onToggleCategory(category) },
                            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Sm),
                        ) {
                            Text(if (category.hidden) "Show" else "Hide", style = MaterialTheme.typography.labelMedium)
                        }
                        DragHandle(
                            itemKey = category.categoryKey,
                            contentDescription = "Reorder ${category.name}",
                            enabled = reorderEnabled,
                            state = dragState,
                            listState = listState,
                        ) { direction -> onMoveCategory(category.categoryKey, direction) }
                    }
''')
replace_once(
'''    val reorderEnabled = normalizedQuery.isBlank()

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(title, "Channels · hold the ⋮⋮ grip, then drag up or down", onBack)
''',
'''    val reorderEnabled = normalizedQuery.isBlank()
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        ManagementHeader(title, "Channels · hold the grip and drag. Move to an edge to scroll.", onBack)
''')
marker = '''        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
'''
if text.count(marker) != 1:
    raise SystemExit(f'expected remaining channel LazyColumn marker once, got {text.count(marker)}')
text = text.replace(marker, '''        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Sm),
''', 1)
replace_once(
'''                    Row(
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
                            state = dragState,
                        ) { direction -> onMoveChannel(channel.channelId, direction) }
                    }
''',
'''                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OwnPlaySpacing.Md, vertical = OwnPlaySpacing.Sm),
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
                                text = if (channel.hidden) "Hidden" else "Visible",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnPlayColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                        TextButton(
                            onClick = { onToggleChannel(channel) },
                            contentPadding = PaddingValues(horizontal = OwnPlaySpacing.Sm),
                        ) {
                            Text(if (channel.hidden) "Show" else "Hide", style = MaterialTheme.typography.labelMedium)
                        }
                        DragHandle(
                            itemKey = channel.channelId,
                            contentDescription = "Reorder ${channel.name}",
                            enabled = reorderEnabled,
                            state = dragState,
                            listState = listState,
                        ) { direction -> onMoveChannel(channel.channelId, direction) }
                    }
''')
replace_once(
    '"Hide/Show is immediate. Hold the grip to drag an item into place."',
    '"Hide/Show is immediate. Hold the grip to reorder; drag to an edge to scroll."',
)
replace_once(
'''        Text(title, style = MaterialTheme.typography.headlineMedium, color = OwnPlayColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OwnPlayColors.TextSecondary)
''',
'''        Text(
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
''')
marker = '@Composable\nprivate fun DragHandle('
if text.count(marker) != 1:
    raise SystemExit('DragHandle marker mismatch')
prefix = text.split(marker, 1)[0]
text = prefix + '''@Composable
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
            .size(44.dp)
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
'''
path.write_text(text)

main_path = root / 'app/src/main/java/app/ownplay/mobile/MainActivity.kt'
main = main_path.read_text()
old = '''                onFullscreenChanged = { fullscreen ->
                    contentFullscreen = fullscreen
                    setContentOrientation(fullscreen)
                    setImmersiveFullscreen(fullscreen)
                    updatePictureInPictureParams()
                },
'''
if main.count(old) != 1:
    raise SystemExit('MainActivity fullscreen callback marker mismatch')
main = main.replace(old, '                onFullscreenChanged = ::handleContentFullscreenChanged,\n', 1)
anchor = '    private fun setContentOrientation(fullscreen: Boolean) {\n'
method = '''    private fun handleContentFullscreenChanged(fullscreen: Boolean) {
        contentFullscreen = fullscreen
        updatePictureInPictureParams()
        window.decorView.postOnAnimation {
            if (contentFullscreen != fullscreen || isFinishing || isDestroyed) {
                return@postOnAnimation
            }
            setContentOrientation(fullscreen)
            setImmersiveFullscreen(fullscreen)
        }
    }

'''
if main.count(anchor) != 1:
    raise SystemExit('MainActivity orientation anchor mismatch')
main = main.replace(anchor, method + anchor, 1)
main_path.write_text(main)
