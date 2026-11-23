package com.aurawallet.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurawallet.app.domain.model.Categorization
import com.aurawallet.app.data.repository.HistoryRepository
import com.aurawallet.app.data.repository.HistoryStatus
import com.aurawallet.app.data.local.AuraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** ЛР4: справжня Room-база в пам'яті на емуляторі. Запуск: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    private lateinit var db: AuraDatabase
    private lateinit var repo: HistoryRepository
    private var now = 1_000L

    private fun result(cat: String) = Categorization(cat, cat, 0.9, false, 10.0, "UAH", null, "", emptyList(), false, 1.0)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AuraDatabase::class.java).build()
        repo = HistoryRepository(db.history()) { now++ }
    }

    @After fun tearDown() = db.close()

    @Test fun newestFirst() = runBlocking {
        repo.addDone("перший", result("coffee"))
        repo.addDone("другий", result("transport"))
        assertEquals(listOf("другий", "перший"), repo.observeAll().first().map { it.inputText })
    }

    @Test fun deleteAndClear() = runBlocking {
        val a = repo.addDone("a", result("coffee")); repo.addDone("b", result("coffee"))
        repo.delete(a)
        assertEquals(listOf("b"), repo.observeAll().first().map { it.inputText })
        repo.clear()
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test fun pendingCannotBeSentTwice() = runBlocking {
        val id = repo.addPending("таксі 180")
        assertTrue(repo.startSending(id))
        assertFalse(repo.startSending(id))   // друге натискання ігнорується
        repo.failSending(id)
        assertEquals(HistoryStatus.PENDING, repo.observe(id).first()!!.status)
        assertTrue(repo.startSending(id))
        repo.completeSending(id, result("transport"))
        val done = repo.observe(id).first()!!
        assertEquals(HistoryStatus.DONE, done.status)
        assertEquals("transport", done.result!!.category)
    }

    @Test fun confirmedFeedsBudget() = runBlocking {
        val id = repo.addDone("кава", result("coffee"))
        assertTrue(repo.observeConfirmed().first().isEmpty())
        repo.confirm(id, "cafes", "Кафе")
        assertEquals("cafes", repo.observeConfirmed().first().single().category)
    }
}
