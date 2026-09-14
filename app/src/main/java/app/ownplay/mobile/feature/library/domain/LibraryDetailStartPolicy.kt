package app.ownplay.mobile.feature.library.domain

data class LibraryDetailStartActions(
    val primary: LibraryStartMode,
    val secondary: LibraryStartMode?,
)

/**
 * Keeps Resume discoverable whenever valid saved progress exists while still honoring
 * the user's preferred primary action. The opposite start mode remains available as
 * one secondary action instead of adding a large bank of playback buttons.
 */
object LibraryDetailStartPolicy {
    fun actions(
        hasProgress: Boolean,
        preferResume: Boolean,
    ): LibraryDetailStartActions = when {
        !hasProgress -> LibraryDetailStartActions(
            primary = LibraryStartMode.BEGINNING,
            secondary = null,
        )

        preferResume -> LibraryDetailStartActions(
            primary = LibraryStartMode.RESUME,
            secondary = LibraryStartMode.BEGINNING,
        )

        else -> LibraryDetailStartActions(
            primary = LibraryStartMode.BEGINNING,
            secondary = LibraryStartMode.RESUME,
        )
    }
}
