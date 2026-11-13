package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HabitEntity::class, TaskEntity::class, HabitSubtaskEntity::class, TimeSessionEntity::class, HabitCompletionEntity::class, HabitFreezeEntity::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun habitSubtaskDao(): HabitSubtaskDao
    abstract fun timeSessionDao(): TimeSessionDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun habitFreezeDao(): HabitFreezeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            val cursor = db.query("PRAGMA table_info(`$tableName`)")
            try {
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            } finally {
                cursor.close()
            }
            return false
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "habits", "durationDays")) {
                    db.execSQL("ALTER TABLE habits ADD COLUMN durationDays INTEGER")
                }
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "habits", "isArchived")) {
                    db.execSQL("ALTER TABLE habits ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS `achievements` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `habitName` TEXT NOT NULL, `category` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `durationDays` INTEGER NOT NULL, `totalCompletions` INTEGER NOT NULL, `longestStreak` INTEGER NOT NULL, `bestWeekLabel` TEXT NOT NULL, `bestWeekRate` REAL NOT NULL, `overallRate` REAL NOT NULL, `graduatedAt` TEXT NOT NULL, `chosenAction` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL DEFAULT 0, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "habits", "reminderTime")) {
                    db.execSQL("ALTER TABLE habits ADD COLUMN reminderTime TEXT")
                }
                if (!hasColumn(db, "habits", "frequencyType")) {
                    db.execSQL("ALTER TABLE habits ADD COLUMN frequencyType TEXT NOT NULL DEFAULT 'DAILY'")
                }
                if (!hasColumn(db, "habits", "daysOfWeek")) {
                    db.execSQL("ALTER TABLE habits ADD COLUMN daysOfWeek TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'")
                }
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit_subtasks` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `dateStr` TEXT NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `time_sessions` (`habitId` TEXT NOT NULL, `dateStr` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `dateStr`))")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create table for normalized habit completions
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit_completions` (`habitId` TEXT NOT NULL, `dateStr` TEXT NOT NULL, PRIMARY KEY(`habitId`, `dateStr`))")
                
                // Migrate existing completions from habits table to habit_completions table
                val cursor = db.query("SELECT id, completions FROM habits")
                try {
                    val idIndex = cursor.getColumnIndex("id")
                    val completionsIndex = cursor.getColumnIndex("completions")
                    if (idIndex != -1 && completionsIndex != -1) {
                        while (cursor.moveToNext()) {
                            val habitId = cursor.getString(idIndex)
                            val completionsStr = cursor.getString(completionsIndex) ?: ""
                            if (completionsStr.isNotBlank()) {
                                val dates = completionsStr.split(",").filter { it.isNotBlank() }
                                for (date in dates) {
                                    db.execSQL(
                                        "INSERT OR IGNORE INTO habit_completions (habitId, dateStr) VALUES (?, ?)",
                                        arrayOf(habitId, date)
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit_freezes` (`habitId` TEXT NOT NULL, `dateStr` TEXT NOT NULL, `reason` TEXT NOT NULL DEFAULT 'Rest Day', PRIMARY KEY(`habitId`, `dateStr`))")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `achievements`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "habit_ledger_database"
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
