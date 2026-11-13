package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.model.Habit
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationAndAnalyticsTest {

    @Test
    fun testMigration7To8_DataIntegrityAndZeroLoss() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }

        // 1. Create a raw SQLite Database at Version 7 schema
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                                "`id` TEXT NOT NULL, " +
                                "`name` TEXT NOT NULL, " +
                                "`category` TEXT NOT NULL, " +
                                "`colorHex` TEXT NOT NULL, " +
                                "`daysOfWeek` TEXT NOT NULL, " +
                                "`durationDays` INTEGER, " +
                                "`createdAt` TEXT NOT NULL, " +
                                "`completions` TEXT NOT NULL, " +
                                "`reminderTime` TEXT, " +
                                "`isArchived` INTEGER NOT NULL DEFAULT 0, " +
                                "`trackTimeSpent` INTEGER NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY(`id`))"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = factory.create(config)
        val rawDb = openHelper.writableDatabase

        // 2. Insert habits with different completion string patterns (including comma edge-cases)
        rawDb.execSQL(
            "INSERT INTO habits (id, name, category, colorHex, daysOfWeek, durationDays, createdAt, completions, reminderTime, isArchived, trackTimeSpent) " +
                    "VALUES ('habit-1', 'Read Book', 'Health', '#FFF', '1,2,3,4,5', 30, '2026-07-01', '2026-07-01,2026-07-02,2026-07-03', null, 0, 0)"
        )
        rawDb.execSQL(
            "INSERT INTO habits (id, name, category, colorHex, daysOfWeek, durationDays, createdAt, completions, reminderTime, isArchived, trackTimeSpent) " +
                    "VALUES ('habit-2', 'No Completions', 'Health', '#FFF', '1', null, '2026-07-01', '', null, 0, 0)"
        )
        rawDb.execSQL(
            "INSERT INTO habits (id, name, category, colorHex, daysOfWeek, durationDays, createdAt, completions, reminderTime, isArchived, trackTimeSpent) " +
                    "VALUES ('habit-3', 'Edge Case Commas', 'Health', '#FFF', '0,6', null, '2026-07-01', ',2026-07-04,,2026-07-05,', null, 0, 0)"
        )

        // Verify version 7 state before migration
        val cursorBefore = rawDb.query("SELECT id, completions FROM habits")
        var countBefore = 0
        while (cursorBefore.moveToNext()) {
            countBefore++
            val id = cursorBefore.getString(0)
            val comps = cursorBefore.getString(1)
            if (id == "habit-1") {
                assertEquals("2026-07-01,2026-07-02,2026-07-03", comps)
            }
        }
        cursorBefore.close()
        assertEquals(3, countBefore)

        // 3. Run the MIGRATION_7_8
        AppDatabase.MIGRATION_7_8.migrate(rawDb)

        // 4. Assert completions table exists and has exact transferred values
        val tableCursor = rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='habit_completions'")
        assertTrue("habit_completions table should exist", tableCursor.moveToFirst())
        tableCursor.close()

        val completionsCursor = rawDb.query("SELECT habitId, dateStr FROM habit_completions ORDER BY habitId, dateStr")
        val completionsList = mutableListOf<Pair<String, String>>()
        while (completionsCursor.moveToNext()) {
            completionsList.add(Pair(completionsCursor.getString(0), completionsCursor.getString(1)))
        }
        completionsCursor.close()

        // We expect:
        // habit-1 -> 2026-07-01, 2026-07-02, 2026-07-03 (3 entries)
        // habit-3 -> 2026-07-04, 2026-07-05 (2 entries)
        // habit-2 -> no entries
        assertEquals(5, completionsList.size)
        assertTrue(completionsList.contains(Pair("habit-1", "2026-07-01")))
        assertTrue(completionsList.contains(Pair("habit-1", "2026-07-02")))
        assertTrue(completionsList.contains(Pair("habit-1", "2026-07-03")))
        assertTrue(completionsList.contains(Pair("habit-3", "2026-07-04")))
        assertTrue(completionsList.contains(Pair("habit-3", "2026-07-05")))
        assertFalse("habit-2 should have no completions", completionsList.any { it.first == "habit-2" })

        rawDb.close()
        openHelper.close()
        dbFile.delete()
    }

    @Test
    fun testAnalyticsEquivalence_CompletionsComparison() {
        // Pre-migration domain mapping with completions from v7-formatted parsed strings vs post-migration Sets
        val preMigrationCompletionString = "2026-07-01,2026-07-02,2026-07-03"
        val parsedSetPreMigration = preMigrationCompletionString.split(",").filter { it.isNotBlank() }.toSet()

        val postMigrationCompletionsSet = setOf("2026-07-01", "2026-07-02", "2026-07-03")

        assertEquals(parsedSetPreMigration, postMigrationCompletionsSet)

        // Create the habit mock for both representations
        val habitPre = Habit(
            id = "habit-1",
            name = "Test Habit",
            category = "Health",
            colorHex = "#FFF",
            days = listOf(1, 2, 3, 4, 5),
            durationDays = 30,
            createdAt = "2026-07-01",
            completions = parsedSetPreMigration,
            reminderTime = null,
            isArchived = false,
            trackTimeSpent = false
        )

        val habitPost = Habit(
            id = "habit-1",
            name = "Test Habit",
            category = "Health",
            colorHex = "#FFF",
            days = listOf(1, 2, 3, 4, 5),
            durationDays = 30,
            createdAt = "2026-07-01",
            completions = postMigrationCompletionsSet,
            reminderTime = null,
            isArchived = false,
            trackTimeSpent = false
        )

        // Confirm all derived analytic calculations are strictly identical
        assertEquals(DateUtils.currentStreak(habitPre), DateUtils.currentStreak(habitPost))
        assertEquals(DateUtils.longestStreak(habitPre), DateUtils.longestStreak(habitPost))
        assertEquals(DateUtils.completionRate(habitPre, 30), DateUtils.completionRate(habitPost, 30))
        assertEquals(DateUtils.daysText(habitPre), DateUtils.daysText(habitPost))
        assertEquals(DateUtils.durationInfo(habitPre), DateUtils.durationInfo(habitPost))
    }

    @Test
    fun testRepositoryDeleteCascadingCompletions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val repository = HabitRepository(
            db.habitDao(),
            db.taskDao(),
            db.habitSubtaskDao(),
            db.timeSessionDao(),
            db.habitCompletionDao(),
            db.habitFreezeDao()
        )

        val habitA = Habit(
            id = "habit-a",
            name = "Habit A",
            category = "Work",
            colorHex = "#FAA",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-07-01",
            completions = setOf("2026-07-01", "2026-07-02"),
            reminderTime = null,
            isArchived = false,
            trackTimeSpent = false
        )

        val habitB = Habit(
            id = "habit-b",
            name = "Habit B",
            category = "Fitness",
            colorHex = "#FBB",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-07-01",
            completions = setOf("2026-07-01", "2026-07-03"),
            reminderTime = null,
            isArchived = false,
            trackTimeSpent = false
        )

        // Insert both
        repository.insertHabit(habitA)
        repository.insertHabit(habitB)

        // Verify they exist in DB
        val activeHabits = repository.allHabits.first()
        assertEquals(2, activeHabits.size)

        val compsA = db.habitCompletionDao().getCompletionsForHabit("habit-a")
        val compsB = db.habitCompletionDao().getCompletionsForHabit("habit-b")
        assertEquals(2, compsA.size)
        assertEquals(2, compsB.size)

        // Delete habit A
        repository.deleteHabit("habit-a")

        // Verify only habit-a is deleted and its completions are gone, but habit-b and its completions remain
        val habitsAfterDelete = repository.allHabits.first()
        assertEquals(1, habitsAfterDelete.size)
        assertEquals("habit-b", habitsAfterDelete[0].id)

        val compsAAfter = db.habitCompletionDao().getCompletionsForHabit("habit-a")
        val compsBAfter = db.habitCompletionDao().getCompletionsForHabit("habit-b")
        assertTrue("Completions for habit-a should be completely cascade-deleted", compsAAfter.isEmpty())
        assertEquals("Completions for habit-b should be unaffected", 2, compsBAfter.size)

        db.close()
    }

    @Test
    fun testBackupRoundTrip_PreservesArchivedHabitsFreezesAndNullDuration() {
        val activeHabit = HabitEntity(
            id = "h_active",
            name = "Morning Meditation",
            category = "Mind",
            colorHex = "#10B981",
            frequencyType = "DAILY",
            daysOfWeek = "1,2,3,4,5",
            durationDays = null,
            createdAt = "2026-07-01",
            completions = "",
            reminderTime = null,
            isArchived = false,
            trackTimeSpent = true
        )
        val archivedHabit = HabitEntity(
            id = "h_archived",
            name = "30 Day Challenge",
            category = "Fitness",
            colorHex = "#E5A93C",
            frequencyType = "DAILY",
            daysOfWeek = "0,1,2,3,4,5,6",
            durationDays = 30,
            createdAt = "2026-06-01",
            completions = "",
            reminderTime = "08:30",
            isArchived = true,
            trackTimeSpent = false
        )
        val completions = listOf(
            HabitCompletionEntity("h_active", "2026-07-01"),
            HabitCompletionEntity("h_archived", "2026-06-15")
        )
        val freezes = listOf(
            HabitFreezeEntity("h_active", "2026-07-02", "Traveling")
        )

        val jsonObj = com.example.util.BackupManager.buildBackupJson(
            habitEntities = listOf(activeHabit, archivedHabit),
            completionEntities = completions,
            freezeEntities = freezes,
            timeSessionEntities = emptyList(),
            subtaskEntities = emptyList(),
            taskEntities = emptyList()
        )

        val parsed = com.example.util.BackupManager.parseBackupJson(jsonObj.toString())
        assertEquals(2, parsed.habits.size)

        val restoredActive = parsed.habits.first { it.id == "h_active" }
        val restoredArchived = parsed.habits.first { it.id == "h_archived" }

        // Verify null durationDays and null reminderTime were not corrupted into 0 or "null"
        assertEquals(null, restoredActive.durationDays)
        assertEquals(null, restoredActive.reminderTime)
        assertFalse(restoredActive.isArchived)

        // Verify archived habit and its durationDays / reminderTime were preserved
        assertTrue(restoredArchived.isArchived)
        assertEquals(30, restoredArchived.durationDays)
        assertEquals("08:30", restoredArchived.reminderTime)

        // Verify freezes and completions round-tripped
        assertEquals(2, parsed.completions.size)
        assertEquals(1, parsed.freezes.size)
        assertEquals("Traveling", parsed.freezes[0].reason)
        assertEquals("2026-07-02", parsed.freezes[0].dateStr)
    }

    @Test
    fun testReminderTimeParser_Handles24hAnd12hAndInvalidFormats() {
        assertEquals(8 to 30, com.example.util.NotificationHelper.parseReminderHourMinute("08:30"))
        assertEquals(0 to 5, com.example.util.NotificationHelper.parseReminderHourMinute("00:05"))
        assertEquals(21 to 45, com.example.util.NotificationHelper.parseReminderHourMinute("21:45"))
        assertEquals(8 to 30, com.example.util.NotificationHelper.parseReminderHourMinute("08:30 AM"))
        assertEquals(20 to 30, com.example.util.NotificationHelper.parseReminderHourMinute("08:30 PM"))
        assertEquals(0 to 15, com.example.util.NotificationHelper.parseReminderHourMinute("12:15 AM"))
        assertEquals(12 to 15, com.example.util.NotificationHelper.parseReminderHourMinute("12:15 PM"))
        assertEquals(null, com.example.util.NotificationHelper.parseReminderHourMinute(null))
        assertEquals(null, com.example.util.NotificationHelper.parseReminderHourMinute(""))
        assertEquals(null, com.example.util.NotificationHelper.parseReminderHourMinute("25:00"))
        assertEquals(null, com.example.util.NotificationHelper.parseReminderHourMinute("08:99"))
        assertEquals(null, com.example.util.NotificationHelper.parseReminderHourMinute("invalid"))
    }
}
