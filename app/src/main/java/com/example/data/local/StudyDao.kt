package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Flashcard
import com.example.data.model.QuizQuestion
import com.example.data.model.StudySet
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_sets ORDER BY createdAt DESC")
    fun getAllStudySets(): Flow<List<StudySet>>

    @Query("SELECT * FROM study_sets ORDER BY createdAt DESC LIMIT 1")
    fun getLatestStudySet(): Flow<StudySet?>

    @Query("SELECT * FROM study_sets WHERE id = :id")
    fun getStudySetById(id: Long): Flow<StudySet?>

    @Query("SELECT * FROM flashcards WHERE studySetId = :setId ORDER BY id ASC")
    fun getFlashcardsForSet(setId: Long): Flow<List<Flashcard>>

    @Query("SELECT * FROM quiz_questions WHERE studySetId = :setId ORDER BY id ASC")
    fun getQuizQuestionsForSet(setId: Long): Flow<List<QuizQuestion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySet(studySet: StudySet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<Flashcard>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestions(questions: List<QuizQuestion>)

    @Query("UPDATE flashcards SET isMastered = :isMastered WHERE id = :id")
    suspend fun updateFlashcardMastery(id: Long, isMastered: Boolean)

    @Query("DELETE FROM study_sets WHERE id = :id")
    suspend fun deleteStudySet(id: Long)

    @Query("DELETE FROM flashcards WHERE studySetId = :setId")
    suspend fun deleteFlashcardsForSet(setId: Long)

    @Query("DELETE FROM quiz_questions WHERE studySetId = :setId")
    suspend fun deleteQuizQuestionsForSet(setId: Long)
}
