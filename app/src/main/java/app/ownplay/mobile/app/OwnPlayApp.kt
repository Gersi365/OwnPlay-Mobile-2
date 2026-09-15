package app.ownplay.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import app.ownplay.mobile.core.OwnPlayServices
import app.ownplay.mobile.design.OwnPlayColors
import app.ownplay.mobile.design.OwnPlayModal
import app.ownplay.mobile.design.OwnPlaySpacing
import app.ownplay.mobile.design.OwnPlayTheme
import app.ownplay.mobile.feature.library.ui.LibraryShell
import app.ownplay.mobile.feature.live.ui.LiveShell
import app.ownplay.mobile.feature.settings.domain.SettingsSnapshot
import app.ownplay.mobile.feature.settings.ui.SettingsShell
import app.ownplay.mobile.playback.ui.LocalPlaybackController
import app.ownplay.mobile.playback.ui.LocalPlayerInteractionState
import app.ownplay.mobile.playback.ui.PlayerGlassPillAction
import app.ownplay.mobile.playback.ui.PlayerInteractionState
import app.ownplay.mobile.playback.ui.rememberPlayerInteractionState
import kotlinx.coroutines.launch

@Composable
fun OwnPlayApp(
    services: OwnPlayServices,
    liveAutoFullscreenRequestToken: Int = 0,
    liveAutoPreviewRequestToken: Int = 0,
    pictureInPictureActive: Boolean = false,
    onFullscreenChanged: (ContentFullscreenKind) -> Unit = {},
    onExitConfirmed: () -> Unit = {},
) {
    OwnPlayTheme {
        var selectedDestination by rememberSaveable {
            mutableStateOf(AppDestination.Live)
        }
        var contentFullscreenKind by rememberSaveable {
            mutableStateOf(ContentFullscreenKind.NONE)
        }
        var exitConfirmationVisible by rememberSaveable { mutableStateOf(false) }
        var destinationTransitionInProgress by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val playerInteractionState = rememberPlayerInteractionState()
        val settingsFlow = remember(services.settingsPreferences) { services.settingsPreferences.settings }
        val settings by settingsFlow.collectAsState(initial = SettingsSnapshot())

        fun setContentFullscreen(kind: ContentFullscreenKind) {
            contentFullscreenKind = kind
            onFullscreenChanged(kind)
        }

        fun selectDestination(destination: AppDestination) {
            if (selectedDestination == destination || destinationTransitionInProgress) {
                return
            }
            if (selectedDestination == AppDestination.Settings) {
                selectedDestination = destination
                return
            }

            destinationTransitionInProgress = true
            scope.launch {
                try {
                    services.playbackController.stop(clearMedia = true)
                } finally {
                    setContentFullscreen(ContentFullscreenKind.NONE)
                    selectedDestination = destination
                    destinationTransitionInProgress = false
                }
            }
        }

        LaunchedEffect(contentFullscreenKind, pictureInPictureActive) {
            if (!contentFullscreenKind.isFullscreen || pictureInPictureActive) {
                playerInteractionState.unlockTouch()
            }
        }

        BackHandler {
            exitConfirmationVisible = true
        }

        if (exitConfirmationVisible) {
            OwnPlayModal(
                title = "Exit OwnPlay?",
                message = "Do you want to close OwnPlay?",
                confirmLabel = "Exit",
                dismissLabel = "Cancel",
                onConfirm = onExitConfirmed,
                onDismiss = { exitConfirmationVisible = false },
            )
        }

        CompositionLocalProvider(
            LocalPlaybackController provides services.playbackController,
            LocalPlayerInteractionState provides playerInteractionState,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = OwnPlayColors.Background,
                    bottomBar = {
                        if (!contentFullscreenKind.isFullscreen) {
                            OwnPlayBottomBar(
                                selectedDestination = selectedDestination,
                                onDestinationSelected = ::selectDestination,
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                if (contentFullscreenKind.isFullscreen) {
                                    PaddingValues(0.dp)
                                } else {
                                    innerPadding
                                },
                            ),
                    ) {
                        when (selectedDestination) {
                            AppDestination.Live -> LiveShell(
                                liveRepository = services.liveRepository,
                                playbackController = services.playbackController,
                                showChannelLogos = settings.showChannelLogos,
                                hideChannelPrefix = settings.hideChannelPrefix,
                                autoFullscreenRequestToken = liveAutoFullscreenRequestToken,
                                autoPreviewRequestToken = liveAutoPreviewRequestToken,
                                onFullscreenChanged = { fullscreen ->
                                    setContentFullscreen(
                                        if (fullscreen) ContentFullscreenKind.LIVE else ContentFullscreenKind.NONE,
                                    )
                                },
                            )

                            AppDestination.Library -> LibraryShell(
                                libraryRepository = services.libraryRepository,
                                downloadRepository = services.downloadRepository,
                                downloadMetadataResolver = services.libraryDownloadMetadataResolver,
                                libraryVisibilityPreferences = services.libraryVisibilityPreferences,
                                playbackController = services.playbackController,
                                resumePlaybackEnabled = settings.resumePlaybackEnabled,
                                onFullscreenChanged = { fullscreen ->
                                    setContentFullscreen(
                                        if (fullscreen) ContentFullscreenKind.LIBRARY else ContentFullscreenKind.NONE,
                                    )
                                },
                            )

                            AppDestination.Settings -> SettingsShell(
                                sourceRepository = services.sourceRepository,
                                settingsPreferences = services.settingsPreferences,
                                backupRepository = services.backupRepository,
                                liveRepository = services.liveRepository,
                            )
                        }
                    }
                }

                if (
                    contentFullscreenKind.isFullscreen &&
                    !pictureInPictureActive &&
                    playerInteractionState.touchLocked
                ) {
                    PlayerTouchLockOverlay(playerInteractionState)
                }
            }
        }
    }
}

@Composable
private fun PlayerTouchLockOverlay(state: PlayerInteractionState) {
    BackHandler(onBack = state::unlockTouch)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
    ) {
        PlayerGlassPillAction(
            text = "Touch locked · Unlock",
            emphasized = true,
            onClick = state::unlockTouch,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(OwnPlaySpacing.Lg),
        )
    }
}
