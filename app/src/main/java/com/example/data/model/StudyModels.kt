package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class DifficultyLevel(val label: String, val description: String) {
    BEGINNER("Beginner", "Foundational concepts, accessible definitions, and introductory examples"),
    INTERMEDIATE("Intermediate", "Practical application, standard formulas, and contextual analysis"),
    ADVANCED("Advanced", "Deep technical nuances, algorithmic complexity, and edge cases")
}

enum class StudyTab(val label: String, val iconLabel: String) {
    MATERIAL_INPUT("Material Input", "Source Notes"),
    FLASHCARDS("Flashcards", "Flip Deck"),
    QUIZ_MODE("Quiz Mode", "Interactive Quiz"),
    CONCEPT_EXPLAINER("Concept Explainer", "Socratic Tutor")
}

@Entity(tableName = "study_sets")
data class StudySet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val rawContent: String,
    val difficulty: String,
    val eli5Summary: String,
    val deepDiveSummary: String,
    val bulletPointsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcards")
data class Flashcard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studySetId: Long,
    val front: String,
    val back: String,
    val category: String = "Core Concept",
    val isMastered: Boolean = false
)

@Entity(tableName = "quiz_questions")
data class QuizQuestion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studySetId: Long,
    val questionText: String,
    val optionsJson: String, // JSON array of string options
    val correctAnswerIndex: Int,
    val explanation: String,
    val isTrueFalse: Boolean = false
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProcessedStudyResult(
    val title: String,
    val eli5Summary: String,
    val deepDiveSummary: String,
    val bulletPoints: List<String>,
    val flashcards: List<FlashcardItem>,
    val quizQuestions: List<QuizQuestionItem>
)

data class FlashcardItem(
    val front: String,
    val back: String,
    val category: String = "Core Concept"
)

data class QuizQuestionItem(
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String,
    val isTrueFalse: Boolean = false
)

data class UploadedFileItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: android.net.Uri,
    val fileName: String,
    val mimeType: String,
    val sizeText: String,
    val isPdf: Boolean,
    val thumbnailBitmap: android.graphics.Bitmap? = null,
    val pageCount: Int = 1
)

data class RawFileData(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

