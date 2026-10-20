package com.example.notifmanager.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the database layer against a real (but in-memory) Room database.
 * The in-memory database lives in RAM and is thrown away after each test,
 * so these tests never touch your real stored notifications.
 */
@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {

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

    // Builds a test record. Only the fields that matter to a test need to be passed in.
    private fun record(
        dedupeKey: String,
        postedAt: Long = 1_000L,
        sbnKey: String = "key-$dedupeKey"
    ) = NotificationEntity(
        dedupeKey = dedupeKey,
        sbnKey = sbnKey,
        sourcePackage = "com.example.app",
        appLabel = "Example",
        titleSnippet = "Title",
        textSnippet = "Text",
        postedAt = postedAt
    )

    // The current contents of the list the inbox screen would show.
    private suspend fun all(): List<NotificationEntity> = dao.observeRecent().first()

    @Test
    fun insert_storesRecordWithDefaults() {
        runBlocking {
            dao.insert(record("a"))

            val rows = all()
            assertEquals(1, rows.size)
            assertEquals("Example", rows[0].appLabel)
            assertEquals("UNSORTED", rows[0].category)
            assertNull(rows[0].removedAt)
        }
    }

    @Test
    fun insert_sameDedupeKey_isIgnored() {
        runBlocking {
            val first = dao.insert(record("a"))
            val second = dao.insert(record("a"))

            assertTrue(first > 0)
            assertEquals(-1L, second) // Room returns -1 when IGNORE skips a row
            assertEquals(1, all().size)
        }
    }

    @Test
    fun recent_isSortedNewestFirst() {
        runBlocking {
            dao.insert(record("old", postedAt = 1_000))
            dao.insert(record("new", postedAt = 3_000))
            dao.insert(record("mid", postedAt = 2_000))

            assertEquals(listOf("new", "mid", "old"), all().map { it.dedupeKey })
        }
    }

    @Test
    fun recent_isCappedAt200() {
        runBlocking {
            for (i in 1..205) {
                dao.insert(record("k$i", postedAt = i.toLong()))
            }

            val rows = all()
            assertEquals(200, rows.size)
            assertEquals(205L, rows.first().postedAt) // newest kept
            assertEquals(6L, rows.last().postedAt)    // oldest five dropped from the list
        }
    }

    @Test
    fun markRemoved_updatesOnlyTheMatchingNotification() {
        runBlocking {
            dao.insert(record("a"))
            dao.insert(record("b"))

            dao.markRemoved("key-a", removedAt = 5_000L)

            val byKey = all().associateBy { it.dedupeKey }
            assertEquals(5_000L, byKey.getValue("a").removedAt)
            assertNull(byKey.getValue("b").removedAt)
        }
    }

    @Test
    fun markRemoved_doesNotOverwriteAnEarlierRemovalTime() {
        runBlocking {
            dao.insert(record("a"))

            dao.markRemoved("key-a", removedAt = 5_000L)
            dao.markRemoved("key-a", removedAt = 9_000L)

            assertEquals(5_000L, all().single().removedAt)
        }
    }

    @Test
    fun clearAll_emptiesTheTable() {
        runBlocking {
            dao.insert(record("a"))
            dao.insert(record("b"))

            dao.clearAll()

            assertTrue(all().isEmpty())
        }
    }
}
