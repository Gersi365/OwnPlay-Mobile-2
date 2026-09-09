package app.ownplay.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceEntity::class,
        ProviderCategoryEntity::class,
        LiveChannelEntity::class,
        ChannelPersonalizationEntity::class,
        CustomGroupEntity::class,
        CustomGroupMembershipEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        MediaFavoriteEntity::class,
        PlaybackProgressEntity::class,
        DownloadEntity::class,
        RefreshStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OwnPlayDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun catalogDao(): CatalogDao
    abstract fun refreshStateDao(): RefreshStateDao

    companion object {
        fun create(context: Context): OwnPlayDatabase = Room.databaseBuilder(
            context.applicationContext,
            OwnPlayDatabase::class.java,
            "ownplay-v1.db",
        ).build()
    }
}
