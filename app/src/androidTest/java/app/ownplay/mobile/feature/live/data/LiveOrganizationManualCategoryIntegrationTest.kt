package app.ownplay.mobile.feature.live.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ownplay.mobile.data.db.OwnPlayDatabase
import app.ownplay.mobile.data.db.SourceEntity
import app.ownplay.mobile.feature.live.domain.LiveOrganizationOrigin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveOrganizationManualCategoryIntegrationTest {
    private lateinit var database: OwnPlayDatabase
    private lateinit var repository: LiveOrganizationRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OwnPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.sourceDao().insert(
            SourceEntity(
                sourceId = SOURCE_ID,
                displayName = "Manual category source",
                type = "XTREAM",
                baseLocator = "https://provider.example",
                credentialReference = null,
                enabled = true,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        repository = LiveOrganizationRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun manualCategoriesSupportHierarchyAndSurviveAutoCleanup() = runBlocking {
        repository.createOwnPlayCategory(SOURCE_ID, null, "Documentary")

        val root = database.liveOrganizationDao().getOwnPlayCategoriesForEdit(SOURCE_ID).single()
        assertEquals("Documentary", root.displayName)
        assertEquals(LiveOrganizationOrigin.MANUAL.name, root.origin)
        assertTrue(root.available)
        repository.createOwnPlayCategory(SOURCE_ID, root.categoryId, "Regional")
        repository.createOwnPlayCategory(SOURCE_ID, null, " documentary ")
        repository.createOwnPlayCategory(SOURCE_ID, "missing-parent", "Ignored")

        val categories = database.liveOrganizationDao().getOwnPlayCategoriesForEdit(SOURCE_ID)
        assertEquals(2, categories.size)
        val child = categories.single { it.categoryId != root.categoryId }
        assertEquals("Regional", child.displayName)
        assertEquals(root.categoryId, child.parentCategoryId)
        assertEquals(LiveOrganizationOrigin.MANUAL.name, child.origin)

        database.liveOrganizationDao().markMissingAutoOwnPlayCategoriesUnavailable(
            sourceId = SOURCE_ID,
            generation = 999L,
        )
        val afterCleanup = database.liveOrganizationDao().getOwnPlayCategoriesForEdit(SOURCE_ID)
        assertEquals(2, afterCleanup.size)
        assertTrue(afterCleanup.all { it.available && it.origin == LiveOrganizationOrigin.MANUAL.name })
    }

    private companion object {
        const val SOURCE_ID = "manual-category-source"
    }
}
