package app.ownplay.mobile.app

enum class AppDestination(val label: String) {
    Live("Live"),
    Library("Library"),
    Settings("Settings"),
}

val primaryDestinations: List<AppDestination> = listOf(
    AppDestination.Live,
    AppDestination.Library,
    AppDestination.Settings,
)
