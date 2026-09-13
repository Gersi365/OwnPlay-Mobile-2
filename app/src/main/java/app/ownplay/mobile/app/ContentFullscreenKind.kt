package app.ownplay.mobile.app

enum class ContentFullscreenKind {
    NONE,
    LIVE,
    LIBRARY,
    ;

    val isFullscreen: Boolean
        get() = this != NONE
}
