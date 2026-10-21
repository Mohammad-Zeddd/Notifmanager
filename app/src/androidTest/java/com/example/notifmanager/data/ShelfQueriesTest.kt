package com.example.notifmanager.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the queries that feed the shelf notification and the retention rule. */
@RunWith(AndroidJUnit4::class)
class ShelfQueriesTest {

    private lateinit var db: NotificationDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        dao = db.notificationDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun record(
        dedupeKey: String,
        postedAt: Long = 1_000L,
        appLabel: String = "Example"
    ) = NotificationEntity(
        dedupeKey = dedupeKey,
        sbnKey = "key-$dedupeKey",
        sourcePackage = "com.example.app",
        appLabel = appLabel,
        titleSnippet = "Title",
        textSnippet = "Text",
        postedAt = postedAt
    )

    @Test
    fun activeQueries_ignoreRemovedNotifications() {
        runBlocking {
            dao.insert(record("a", postedAt = 1_000))
            dao.insert(record("b", postedAt = 2_000))
            dao.insert(record("c", postedAt = 3_000))
            dao.markRemoved("key-b", removedAt = 5_000)

            assertEquals(2, dao.activeCount())
            assertEquals(listOf("c", "a"), dao.latestActive(10).map { it.dedupeKey })
            assertEquals(listOf("c"), dao.latestActive(1).map { it.dedupeKey })
        }
    }

    @Test
    fun topApps_countsPerAppAndReturnsTheBusiestThree() {
        runBlocking {
            repeat(3) { dao.insert(record("w$it", appLabel = "WhatsApp")) }
            repeat(2) { dao.insert(record("g$it", appLabel = "Gmail")) }
            dao.insert(record("p0", appLabel = "Pinterest"))
            dao.insert(record("m0", appLabel = "Meesho"))

            val top = dao.topApps()

            assertEquals(3, top.size)
            assertEquals(AppCount("WhatsApp", 3), top[0])
            assertEquals(AppCount("Gmail", 2), top[1])
            assertEquals("Meesho", top[2].appLabel) // tie on 1, so alphabetical order decides
        }
    }

    @Test
    fun markMissingRemoved_respectsTheSnapshotTime() {
        runBlocking {
            dao.insert(record("a", postedAt = 1_000)) // still active in the snapshot
            dao.insert(record("b", postedAt = 2_000)) // missing from the snapshot: removed
            dao.insert(record("c", postedAt = 9_000)) // posted after the snapshot: untouched

            dao.markMissingRemoved(activeKeys = listOf("key-a"), snapshotTime = 5_000)

            val byKey = dao.latestActive(10).associateBy { it.dedupeKey }
            assertEquals(setOf("a", "c"), byKey.keys)
            assertNull(byKey.getValue("a").removedAt)
            assertNull(byKey.getValue("c").removedAt)
        }
    }

    @Test
    fun deleteRemovedOlderThan_onlyDeletesOldRemovedRecords() {
        runBlocking {
            dao.insert(record("oldRemoved", postedAt = 1_000))
            dao.insert(record("oldActive", postedAt = 1_000))
            dao.insert(record("newRemoved", postedAt = 9_000))
            dao.markRemoved("key-oldRemoved", removedAt = 2_000)
            dao.markRemoved("key-newRemoved", removedAt = 9_500)

            dao.deleteRemovedOlderThan(cutoff = 5_000)

            val keys = dao.observeRecent().first().map { it.dedupeKey }.toSet()
            assertEquals(setOf("oldActive", "newRemoved"), keys)
        }
    }
}
