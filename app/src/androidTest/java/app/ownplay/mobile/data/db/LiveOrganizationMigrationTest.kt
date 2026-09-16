package app.ownplay.mobile.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownplay.mobile.feature.live.domain.LiveOrganizationScopePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveOrganizationMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OwnPlayDatabase::class.java,
    )

    @Test
    fun migration2To3SeedsProviderScopesAndKeepsLegacyBridgeSynchronized() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO sources
                    (sourceId, displayName, type, baseLocator, credentialReference, enabled, createdAt, updatedAt)
                VALUES
                    ('source-1', 'Provider', 'XTREAM', 'https://example.invalid', NULL, 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO live_channels
                    (channelId, sourceId, providerKey, providerStreamId, categoryKey, name, tvgId, tvgName,
                     logoUrl, streamLocator, providerOrder, available, lastSeenGeneration)
                VALUES
                    ('channel-1', 'source-1', '101', '101', 'italy-fhd', 'DAZN 1 FHD', NULL, NULL,
                     NULL, 'xtream://live/101', 4, 1, 7),
                    ('channel-2', 'source-1', '102', '102', NULL, 'Uncategorized', NULL, NULL,
                     NULL, 'xtream://live/102', 5, 1, 7)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO category_personalization
                    (sourceId, kind, categoryKey, hidden, manualOrder)
                VALUES ('source-1', 'LIVE', 'italy-fhd', 1, 2)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO channel_personalization
                    (channelId, favorite, hidden, localName, localLogo, manualOrder)
                VALUES
                    ('channel-1', 1, 1, 'DAZN One', NULL, 3),
                    ('channel-2', 0, 0, NULL, NULL, 4)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            OwnPlayDatabase.MIGRATION_2_3,
        )

        assertSingleText(
            migrated,
            "SELECT activeMode FROM live_organization_preferences WHERE sourceId = 'source-1'",
            "PROVIDER",
        )
        assertSingleIntPair(
            migrated,
            """
            SELECT hidden, manualOrder
            FROM live_category_scope_personalization
            WHERE sourceId = 'source-1'
              AND organizationMode = 'PROVIDER'
              AND categoryId = 'italy-fhd'
            """.trimIndent(),
            first = 1,
            second = 2,
        )
        assertProviderMembership(migrated, "channel-1", "italy-fhd", hidden = 1, manualOrder = 3)
        assertProviderMembership(
            migrated,
            "channel-2",
            LiveOrganizationScopePolicy.PROVIDER_UNCATEGORIZED_CATEGORY_ID,
            hidden = 0,
            manualOrder = 4,
        )

        assertSingleIntPair(
            migrated,
            "SELECT favorite, hidden FROM channel_personalization WHERE channelId = 'channel-1'",
            first = 1,
            second = 1,
        )
        assertSingleText(
            migrated,
            "SELECT localName FROM channel_personalization WHERE channelId = 'channel-1'",
            "DAZN One",
        )

        migrated.execSQL(
            "UPDATE channel_personalization SET hidden = 0, manualOrder = 9 WHERE channelId = 'channel-1'",
        )
        assertProviderMembership(migrated, "channel-1", "italy-fhd", hidden = 0, manualOrder = 9)

        migrated.execSQL(
            "UPDATE live_channels SET categoryKey = 'italy-hevc' WHERE channelId = 'channel-1'",
        )
        assertProviderMembership(migrated, "channel-1", "italy-hevc", hidden = 0, manualOrder = 9)
        assertEquals(
            0,
            countRows(
                migrated,
                """
                SELECT COUNT(*)
                FROM live_channel_membership_personalization
                WHERE channelId = 'channel-1'
                  AND organizationMode = 'PROVIDER'
                  AND categoryId = 'italy-fhd'
                """.trimIndent(),
            ),
        )

        migrated.execSQL(
            """
            INSERT INTO sources
                (sourceId, displayName, type, baseLocator, credentialReference, enabled, createdAt, updatedAt)
            VALUES
                ('source-2', 'Second Provider', 'M3U', 'https://example.invalid/list.m3u', NULL, 1, 2, 2)
            """.trimIndent(),
        )
        assertSingleText(
            migrated,
            "SELECT activeMode FROM live_organization_preferences WHERE sourceId = 'source-2'",
            "PROVIDER",
        )

        migrated.close()
    }

    private fun assertProviderMembership(
        database: SupportSQLiteDatabase,
        channelId: String,
        categoryId: String,
        hidden: Int,
        manualOrder: Int,
    ) {
        database.query(
            """
            SELECT categoryId, hidden, manualOrder
            FROM live_channel_membership_personalization
            WHERE sourceId = 'source-1'
              AND organizationMode = 'PROVIDER'
              AND channelId = ?
            """.trimIndent(),
            arrayOf(channelId),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(categoryId, cursor.getString(0))
            assertEquals(hidden, cursor.getInt(1))
            assertEquals(manualOrder, cursor.getInt(2))
            assertEquals(1, cursor.count)
        }
    }

    private fun assertSingleText(
        database: SupportSQLiteDatabase,
        sql: String,
        expected: String,
    ) {
        database.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
            assertEquals(1, cursor.count)
        }
    }

    private fun assertSingleIntPair(
        database: SupportSQLiteDatabase,
        sql: String,
        first: Int,
        second: Int,
    ) {
        database.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(first, cursor.getInt(0))
            assertEquals(second, cursor.getInt(1))
            assertEquals(1, cursor.count)
        }
    }

    private fun countRows(database: SupportSQLiteDatabase, sql: String): Int =
        database.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "live-organization-migration-test"
    }
}
