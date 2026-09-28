package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Flashcard
import com.example.data.model.QuizQuestion
import com.example.data.model.StudySet

@Database(
    entities = [StudySet::class, Flashcard::class, QuizQuestion::class],
    version = 1,
    exportSchema = false
)
abstract class StudyPulseDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: StudyPulseDatabase? = null

        fun getDatabase(context: Context): StudyPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyPulseDatabase::class.java,
                    "studypulse_database"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
