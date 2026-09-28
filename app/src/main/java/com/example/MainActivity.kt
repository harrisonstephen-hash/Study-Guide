package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.StudyTab
import com.example.ui.StudyPulseViewModel
import com.example.ui.components.ConceptExplainerTab
import com.example.ui.components.FlashcardsTab
import com.example.ui.components.HeaderNav
import com.example.ui.components.MaterialInputTab
import com.example.ui.components.QuizTab
import com.example.ui.theme.StudyPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: StudyPulseViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            StudyPulseTheme(darkTheme = isDarkMode) {
                StudyPulseApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun StudyPulseApp(viewModel: StudyPulseViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    // Handle back button for sub-tabs to return to Material Input tab
    BackHandler(enabled = selectedTab != StudyTab.MATERIAL_INPUT) {
        viewModel.selectTab(StudyTab.MATERIAL_INPUT)
    }

    val rawNotes by viewModel.rawNotes.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val attachedImageBitmap by viewModel.attachedImageBitmap.collectAsStateWithLifecycle()
    val attachedImageUri by viewModel.attachedImageUri.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingStep by viewModel.processingStep.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val studySets by viewModel.allStudySets.collectAsStateWithLifecycle()
    val activeStudySet by viewModel.activeStudySet.collectAsStateWithLifecycle()

    val flashcards by viewModel.flashcards.collectAsStateWithLifecycle()
    val currentCardIndex by viewModel.currentCardIndex.collectAsStateWithLifecycle()
    val isCardFlipped by viewModel.isCardFlipped.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val cardFilter by viewModel.cardFilter.collectAsStateWithLifecycle()

    val quizQuestions by viewModel.quizQuestions.collectAsStateWithLifecycle()
    val currentQuizIndex by viewModel.currentQuizIndex.collectAsStateWithLifecycle()
    val selectedOptionIndex by viewModel.selectedOptionIndex.collectAsStateWithLifecycle()
    val userAnswers by viewModel.userAnswers.collectAsStateWithLifecycle()
    val isQuizCompleted by viewModel.isQuizCompleted.collectAsStateWithLifecycle()
    val showExplanation by viewModel.showExplanation.collectAsStateWithLifecycle()

    val isEli5Mode by viewModel.isEli5Mode.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val followUpQuery by viewModel.followUpQuery.collectAsStateWithLifecycle()
    val isAskingFollowUp by viewModel.isAskingFollowUp.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HeaderNav(
                selectedTab = selectedTab,
                isDarkMode = isDarkMode,
                userName = userName,
                onTabSelected = { viewModel.selectTab(it) },
                onToggleDarkMode = { viewModel.toggleDarkMode() },
                onUpdateUserName = { viewModel.updateUserName(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                StudyTab.MATERIAL_INPUT -> {
                    MaterialInputTab(
                        rawNotes = rawNotes,
                        difficulty = difficulty,
                        userName = userName,
                        attachedFiles = attachedFiles,
                        attachedImageBitmap = attachedImageBitmap,
                        attachedImageUri = attachedImageUri,
                        isProcessing = isProcessing,
                        processingStep = processingStep,
                        errorMessage = errorMessage,
                        studySets = studySets,
                        activeStudySet = activeStudySet,
                        onUpdateNotes = { viewModel.updateRawNotes(it) },
                        onAddAttachedFiles = { viewModel.addAttachedFiles(it) },
                        onRemoveAttachedFile = { viewModel.removeAttachedFile(it) },
                        onClearAllFiles = { viewModel.clearAllAttachedFiles() },
                        onSetAttachedImage = { bitmap, uri -> viewModel.setAttachedImage(bitmap, uri) },
                        onClearAttachedImage = { viewModel.clearAttachedImage() },
                        onSetDifficulty = { viewModel.setDifficulty(it) },
                        onLoadSample = { viewModel.loadSampleNotes(it) },
                        onSelectStudySet = { viewModel.loadStudySetById(it.id) },
                        onDeleteStudySet = { viewModel.deleteStudySet(it) },
                        onProcessNotes = { viewModel.processNotes() }
                    )
                }
                StudyTab.FLASHCARDS -> {
                    FlashcardsTab(
                        activeStudySet = activeStudySet,
                        flashcards = flashcards,
                        currentCardIndex = currentCardIndex,
                        isCardFlipped = isCardFlipped,
                        isGridView = isGridView,
                        cardFilter = cardFilter,
                        onToggleCardFlip = { viewModel.toggleCardFlip() },
                        onToggleGridView = { viewModel.toggleGridView() },
                        onSetFilter = { viewModel.setCardFilter(it) },
                        onNextCard = { viewModel.nextCard(it) },
                        onPrevCard = { viewModel.prevCard(it) },
                        onMarkMastery = { card, mastered -> viewModel.markCardMastery(card, mastered) },
                        onGoToInput = { viewModel.selectTab(StudyTab.MATERIAL_INPUT) }
                    )
                }
                StudyTab.QUIZ_MODE -> {
                    QuizTab(
                        activeStudySet = activeStudySet,
                        quizQuestions = quizQuestions,
                        currentQuestionIndex = currentQuizIndex,
                        selectedOptionIndex = selectedOptionIndex,
                        userAnswers = userAnswers,
                        isQuizCompleted = isQuizCompleted,
                        showExplanation = showExplanation,
                        onSelectOption = { viewModel.selectQuizOption(it) },
                        onNextQuestion = { viewModel.nextQuizQuestion() },
                        onRestartQuiz = { viewModel.restartQuiz() },
                        onGoToInput = { viewModel.selectTab(StudyTab.MATERIAL_INPUT) }
                    )
                }
                StudyTab.CONCEPT_EXPLAINER -> {
                    ConceptExplainerTab(
                        activeStudySet = activeStudySet,
                        isEli5Mode = isEli5Mode,
                        chatMessages = chatMessages,
                        followUpQuery = followUpQuery,
                        isAskingFollowUp = isAskingFollowUp,
                        onToggleMode = { viewModel.toggleExplainerMode() },
                        onUpdateQuery = { viewModel.updateFollowUpQuery(it) },
                        onSendFollowUp = { viewModel.sendFollowUp() },
                        onGoToInput = { viewModel.selectTab(StudyTab.MATERIAL_INPUT) }
                    )
                }
            }
        }
    }
}
