package app.ownplay.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class,
        ProviderCategoryEntity::class,
        CategoryPersonalizationEntity::class,
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
    version = 2,
    exportSchema = true,
)
abstract class OwnPlayDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun catalogDao(): CatalogDao
    abstract fun libraryDao(): LibraryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun refreshStateDao(): RefreshStateDao
    abstract fun backupDao(): BackupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `category_personalization` (
                        `sourceId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `categoryKey` TEXT NOT NULL,
                        `hidden` INTEGER NOT NULL DEFAULT 0,
                        `manualOrder` INTEGER,
                        PRIMARY KEY(`sourceId`, `kind`, `categoryKey`),
                        FOREIGN KEY(`sourceId`) REFERENCES `sources`(`sourceId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_category_personalization_sourceId` ON `category_personalization` (`sourceId`)",
                )
            }
        }

        fun create(context: Context): OwnPlayDatabase = Room.databaseBuilder(
            context.applicationContext,
            OwnPlayDatabase::class.java,
            "ownplay-v1.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
