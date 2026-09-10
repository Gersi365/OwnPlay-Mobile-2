from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


# Pure display conversion stays in the already-tested player-local policy.
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/domain/PlayerLocalControlPolicy.kt",
    "package app.ownplay.mobile.playback.domain\n\nobject PlayerLocalControlPolicy {\n",
    "package app.ownplay.mobile.playback.domain\n\nimport kotlin.math.roundToInt\n\nobject PlayerLocalControlPolicy {\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/domain/PlayerLocalControlPolicy.kt",
    "    fun clampBrightness(value: Float): Float = value.coerceIn(MIN_BRIGHTNESS, MAX_LEVEL)\n\n    fun volumeAfterStep",
    "    fun clampBrightness(value: Float): Float = value.coerceIn(MIN_BRIGHTNESS, MAX_LEVEL)\n\n    fun levelPercent(value: Float): Int =\n        (value.coerceIn(0f, MAX_LEVEL) * 100f).roundToInt()\n\n    fun volumeAfterStep",
)

# Transient, non-interactive HUD event surface. SharedFlow avoids persistent global UI state.
hud_path = Path("app/src/main/java/app/ownplay/mobile/playback/ui/PlayerLocalControlHud.kt")
if hud_path.exists():
    raise SystemExit(f"Refusing to overwrite existing {hud_path}")
hud_path.write_text('''package app.ownplay.mobile.playback.ui\n\nimport androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.widthIn\nimport androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.Surface\nimport androidx.compose.material3.Text\nimport androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.dp\nimport app.ownplay.mobile.design.OwnPlayColors\nimport app.ownplay.mobile.design.OwnPlayShapeTokens\nimport app.ownplay.mobile.design.OwnPlaySpacing\nimport app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport kotlinx.coroutines.channels.BufferOverflow\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.MutableSharedFlow\nimport kotlinx.coroutines.flow.asSharedFlow\nimport kotlinx.coroutines.flow.collectLatest\n\ninternal enum class PlayerLocalControlHudKind {\n    VOLUME,\n    BRIGHTNESS,\n}\n\ninternal data class PlayerLocalControlHudEvent(\n    val kind: PlayerLocalControlHudKind,\n    val level: Float,\n)\n\nobject PlayerLocalControlHud {\n    private val mutableEvents = MutableSharedFlow<PlayerLocalControlHudEvent>(\n        extraBufferCapacity = 1,\n        onBufferOverflow = BufferOverflow.DROP_OLDEST,\n    )\n    internal val events = mutableEvents.asSharedFlow()\n\n    fun showVolume(level: Float) {\n        show(PlayerLocalControlHudKind.VOLUME, level)\n    }\n\n    fun showBrightness(level: Float) {\n        show(PlayerLocalControlHudKind.BRIGHTNESS, level)\n    }\n\n    private fun show(kind: PlayerLocalControlHudKind, level: Float) {\n        mutableEvents.tryEmit(\n            PlayerLocalControlHudEvent(\n                kind = kind,\n                level = level.coerceIn(0f, PlayerLocalControlPolicy.MAX_LEVEL),\n            ),\n        )\n    }\n}\n\n@Composable\nfun PlayerLocalControlHudOverlay(modifier: Modifier = Modifier) {\n    var event by remember { mutableStateOf<PlayerLocalControlHudEvent?>(null) }\n\n    LaunchedEffect(Unit) {\n        PlayerLocalControlHud.events.collectLatest { next ->\n            event = next\n            delay(1_200)\n            event = null\n        }\n    }\n\n    val visible = event ?: return\n    Surface(\n        modifier = modifier,\n        color = OwnPlayColors.Surface.copy(alpha = 0.92f),\n        shape = OwnPlayShapeTokens.Medium,\n        border = BorderStroke(1.dp, OwnPlayColors.Divider),\n        tonalElevation = 0.dp,\n    ) {\n        Column(\n            modifier = Modifier\n                .widthIn(min = 112.dp)\n                .padding(horizontal = OwnPlaySpacing.Lg, vertical = OwnPlaySpacing.Md),\n            horizontalAlignment = Alignment.CenterHorizontally,\n        ) {\n            Text(\n                text = when (visible.kind) {\n                    PlayerLocalControlHudKind.VOLUME -> "VOLUME"\n                    PlayerLocalControlHudKind.BRIGHTNESS -> "BRIGHTNESS"\n                },\n                style = MaterialTheme.typography.labelLarge,\n                color = OwnPlayColors.TextSecondary,\n            )\n            Text(\n                text = "${PlayerLocalControlPolicy.levelPercent(visible.level)}%",\n                style = MaterialTheme.typography.headlineSmall,\n                color = OwnPlayColors.TextPrimary,\n                fontWeight = FontWeight.Bold,\n            )\n        }\n    }\n}\n''')

# Touch gestures emit HUD events only after changing OwnPlay-local brightness/player gain.
replace_once(
    "app/src/main/java/app/ownplay/mobile/playback/ui/PlayerLocalControls.kt",
    "                    PlayerGestureMode.BRIGHTNESS -> activity?.let { host ->\n                        host.setOwnPlayBrightness(host.currentOwnPlayBrightness() + delta)\n                    }\n                    PlayerGestureMode.VOLUME -> {\n                        localVolume = PlayerLocalControlPolicy.clampVolume(localVolume + delta)\n                        controllerScope.launch {\n",
    "                    PlayerGestureMode.BRIGHTNESS -> activity?.let { host ->\n                        val brightness = PlayerLocalControlPolicy.clampBrightness(\n                            host.currentOwnPlayBrightness() + delta,\n                        )\n                        host.setOwnPlayBrightness(brightness)\n                        PlayerLocalControlHud.showBrightness(brightness)\n                    }\n                    PlayerGestureMode.VOLUME -> {\n                        localVolume = PlayerLocalControlPolicy.clampVolume(localVolume + delta)\n                        PlayerLocalControlHud.showVolume(localVolume)\n                        controllerScope.launch {\n",
)

# Physical +/- stays intercepted only while player-local controls are active and now shows the same HUD.
replace_once(
    "app/src/main/java/app/ownplay/mobile/MainActivity.kt",
    "import app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport app.ownplay.mobile.playback.domain.VideoTarget\n",
    "import app.ownplay.mobile.playback.domain.PlayerLocalControlPolicy\nimport app.ownplay.mobile.playback.domain.VideoTarget\nimport app.ownplay.mobile.playback.ui.PlayerLocalControlHud\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/MainActivity.kt",
    "        appPlaybackVolume = PlayerLocalControlPolicy.volumeAfterStep(appPlaybackVolume, direction)\n        lifecycleScope.launch {\n",
    "        appPlaybackVolume = PlayerLocalControlPolicy.volumeAfterStep(appPlaybackVolume, direction)\n        PlayerLocalControlHud.showVolume(appPlaybackVolume)\n        lifecycleScope.launch {\n",
)

# Live Preview and fullscreen render the transient HUD without adding transport controls.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "import app.ownplay.mobile.playback.domain.VideoTarget\nimport app.ownplay.mobile.playback.ui.playerLocalVerticalControls\n",
    "import app.ownplay.mobile.playback.domain.VideoTarget\nimport app.ownplay.mobile.playback.ui.PlayerLocalControlHudOverlay\nimport app.ownplay.mobile.playback.ui.playerLocalVerticalControls\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "            LivePlaybackSurface(\n                target = VideoTarget.PREVIEW,\n                playbackController = playbackController,\n                controllerScope = controllerScope,\n                modifier = Modifier.fillMaxSize(),\n            )\n\n            LiveBadge(\n",
    "            LivePlaybackSurface(\n                target = VideoTarget.PREVIEW,\n                playbackController = playbackController,\n                controllerScope = controllerScope,\n                modifier = Modifier.fillMaxSize(),\n            )\n\n            PlayerLocalControlHudOverlay(\n                modifier = Modifier.align(Alignment.Center),\n            )\n\n            LiveBadge(\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/live/ui/LiveShell.kt",
    "        )\n\n        if (overlayVisible) {\n",
    "        )\n\n        PlayerLocalControlHudOverlay(\n            modifier = Modifier.align(Alignment.Center),\n        )\n\n        if (overlayVisible) {\n",
)

# VOD / episode / offline fullscreen shares the same transient HUD.
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    "import app.ownplay.mobile.playback.domain.VideoTarget\nimport app.ownplay.mobile.playback.ui.playerLocalVerticalControls\n",
    "import app.ownplay.mobile.playback.domain.VideoTarget\nimport app.ownplay.mobile.playback.ui.PlayerLocalControlHudOverlay\nimport app.ownplay.mobile.playback.ui.playerLocalVerticalControls\n",
)
replace_once(
    "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt",
    "        )\n\n        if (overlayVisible) {\n",
    "        )\n\n        PlayerLocalControlHudOverlay(\n            modifier = Modifier.align(Alignment.Center),\n        )\n\n        if (overlayVisible) {\n",
)

# Focused policy coverage for deterministic HUD percentages.
replace_once(
    "app/src/test/java/app/ownplay/mobile/playback/PlayerLocalControlPolicyTest.kt",
    "    @Test\n    fun `volume hardware steps clamp to player range`() {\n",
    "    @Test\n    fun `hud percentages clamp and round deterministically`() {\n        assertEquals(0, PlayerLocalControlPolicy.levelPercent(-0.5f))\n        assertEquals(65, PlayerLocalControlPolicy.levelPercent(0.646f))\n        assertEquals(100, PlayerLocalControlPolicy.levelPercent(1.5f))\n    }\n\n    @Test\n    fun `volume hardware steps clamp to player range`() {\n",
)

# Preserve the user decision and the source/physical-QA boundary in Stage 12 audit evidence.
audit = Path("docs/audit/STAGE_12_STABILIZATION.txt")
audit_text = audit.read_text()
entry = '''\n\nPlayer-local HUD refinement\n- The user explicitly requested visible feedback while changing OwnPlay-local volume and brightness in Preview/fullscreen.\n- A compact transient HUD now displays VOLUME or BRIGHTNESS plus the current percentage for approximately 1.2 seconds after the latest adjustment. Continuous gestures refresh the timeout rather than stacking overlays.\n- The HUD is non-interactive and does not add Play/Pause, seek, fullscreen, close, generic Media3 controls, or persistent chrome to Live Preview.\n- Touch brightness continues to use only the activity Window brightness override; touch and physical +/- volume continue to use only ExoPlayer gain. No system volume or system brightness value is modified.\n- The same HUD is rendered in Live Preview, Live fullscreen, and unified Movie/Series/Offline fullscreen playback. PiP remains outside this control surface.\n- This is source-only evidence. Exact-head source validation is required, and physical-device behavior remains unverified until a later explicitly authorized QA APK is tested.\n'''
if "Player-local HUD refinement" in audit_text:
    raise SystemExit("Audit entry already exists")
audit.write_text(audit_text.rstrip() + entry)
