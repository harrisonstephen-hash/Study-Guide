package com.example.data.repository

import com.example.data.local.StudyDao
import com.example.data.model.ChatMessage
import com.example.data.model.Flashcard
import com.example.data.model.QuizQuestion
import com.example.data.model.RawFileData
import com.example.data.model.StudySet
import com.example.data.remote.GeminiService
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class StudyRepository(
    private val studyDao: StudyDao,
    private val geminiService: GeminiService = GeminiService()
) {

    val allStudySets: Flow<List<StudySet>> = studyDao.getAllStudySets()
    val latestStudySet: Flow<StudySet?> = studyDao.getLatestStudySet()

    fun getStudySetById(id: Long): Flow<StudySet?> = studyDao.getStudySetById(id)

    fun getFlashcards(setId: Long): Flow<List<Flashcard>> = studyDao.getFlashcardsForSet(setId)

    fun getQuizQuestions(setId: Long): Flow<List<QuizQuestion>> = studyDao.getQuizQuestionsForSet(setId)

    suspend fun processAndSaveNotes(
        rawNotes: String,
        difficulty: String,
        files: List<RawFileData> = emptyList(),
        imageBitmap: android.graphics.Bitmap? = null
    ): Long {
        val result = geminiService.processStudyNotes(rawNotes, difficulty, files, imageBitmap)

        val bulletsJson = JSONArray(result.bulletPoints).toString()

        val sourceDescription = when {
            rawNotes.isNotBlank() -> rawNotes
            files.isNotEmpty() -> "Source: ${files.size} uploaded documents (${files.joinToString(", ") { it.fileName }})"
            else -> "Source: Attached Study Notes Image (${result.title})"
        }

        val studySet = StudySet(
            title = result.title,
            rawContent = sourceDescription,
            difficulty = difficulty,
            eli5Summary = result.eli5Summary,
            deepDiveSummary = result.deepDiveSummary,
            bulletPointsJson = bulletsJson
        )

        val setId = studyDao.insertStudySet(studySet)

        val flashcardEntities = result.flashcards.map { item ->
            Flashcard(
                studySetId = setId,
                front = item.front,
                back = item.back,
                category = item.category,
                isMastered = false
            )
        }
        studyDao.insertFlashcards(flashcardEntities)

        val quizEntities = result.quizQuestions.map { q ->
            QuizQuestion(
                studySetId = setId,
                questionText = q.question,
                optionsJson = JSONArray(q.options).toString(),
                correctAnswerIndex = q.correctAnswerIndex,
                explanation = q.explanation,
                isTrueFalse = q.isTrueFalse
            )
        }
        studyDao.insertQuizQuestions(quizEntities)

        return setId
    }

    suspend fun updateFlashcardMastery(id: Long, isMastered: Boolean) {
        studyDao.updateFlashcardMastery(id, isMastered)
    }

    suspend fun deleteStudySet(setId: Long) {
        studyDao.deleteFlashcardsForSet(setId)
        studyDao.deleteQuizQuestionsForSet(setId)
        studyDao.deleteStudySet(setId)
    }

    suspend fun askSocraticFollowUp(
        contextNotes: String,
        chatHistory: List<ChatMessage>,
        question: String,
        isEli5: Boolean
    ): String {
        return geminiService.askSocraticFollowUp(contextNotes, chatHistory, question, isEli5)
    }
}
