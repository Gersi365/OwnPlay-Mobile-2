from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


main = "app/src/main/java/app/ownplay/mobile/MainActivity.kt"
replace_once(
    main,
    "        val currentTarget = services.playbackController.state.value.activeTarget\n        when {\n            isInPictureInPictureMode && currentTarget == VideoTarget.FULLSCREEN -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.PIP)\n            }\n\n            !isInPictureInPictureMode &&\n                contentFullscreen &&\n                currentTarget == VideoTarget.PIP -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.FULLSCREEN)\n            }\n        }\n        updateFullscreenOrientationListener()\n",
    "        val currentTarget = services.playbackController.state.value.activeTarget\n        when {\n            isInPictureInPictureMode && currentTarget == VideoTarget.FULLSCREEN -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.PIP)\n            }\n\n            !isInPictureInPictureMode &&\n                contentFullscreen &&\n                currentTarget == VideoTarget.PIP -> lifecycleScope.launch {\n                services.playbackController.transferVideoTarget(VideoTarget.FULLSCREEN)\n            }\n        }\n        fullscreenOrientationLatch.reset()\n        updateFullscreenOrientationListener()\n",
)

library = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt"
replace_once(
    library,
    "            is LibraryPlaybackResolution.Success -> {\n                scope.launch {\n                    libraryVisibilityPreferences.showContinueWatching(\n                        sourceId = resolved.value.sourceId,\n                        mediaKind = resolved.value.mediaKind,\n                        contentId = resolved.value.contentId,\n                    )\n                    playbackController.load(resolved.value.toLoadRequest())\n                    activePlayback = resolved.value\n                }\n            }\n",
    "            is LibraryPlaybackResolution.Success -> {\n                onFullscreenChanged(true)\n                activePlayback = resolved.value\n                scope.launch {\n                    libraryVisibilityPreferences.showContinueWatching(\n                        sourceId = resolved.value.sourceId,\n                        mediaKind = resolved.value.mediaKind,\n                        contentId = resolved.value.contentId,\n                    )\n                    playbackController.load(resolved.value.toLoadRequest())\n                }\n            }\n",
)
replace_once(
    library,
    "    LaunchedEffect(activePlayback) {\n        onFullscreenChanged(activePlayback != null)\n    }\n",
    "    LaunchedEffect(activePlayback) {\n        if (activePlayback == null) {\n            onFullscreenChanged(false)\n        }\n    }\n",
)

print("Stage 32 follow-up patch applied")
