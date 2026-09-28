package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuizQuestion
import com.example.data.model.StudySet
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import org.json.JSONArray

@Composable
fun QuizTab(
    activeStudySet: StudySet?,
    quizQuestions: List<QuizQuestion>,
    currentQuestionIndex: Int,
    selectedOptionIndex: Int?,
    userAnswers: Map<Int, Int>,
    isQuizCompleted: Boolean,
    showExplanation: Boolean,
    onSelectOption: (Int) -> Unit,
    onNextQuestion: () -> Unit,
    onRestartQuiz: () -> Unit,
    onGoToInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (quizQuestions.isEmpty()) {
        EmptyQuizView(onGoToInput = onGoToInput, modifier = modifier)
        return
    }

    if (isQuizCompleted) {
        QuizResultsView(
            studySet = activeStudySet,
            questions = quizQuestions,
            userAnswers = userAnswers,
            onRestart = onRestartQuiz,
            modifier = modifier
        )
        return
    }

    val currentQ = quizQuestions[currentQuestionIndex.coerceIn(0, quizQuestions.size - 1)]
    val optionsList = remember(currentQ.optionsJson) {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(currentQ.optionsJson)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            list.add("Option 1")
            list.add("Option 2")
        }
        list
    }

    val progressFraction = (currentQuestionIndex + 1).toFloat() / quizQuestions.size.toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header with Progress & Question Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = activeStudySet?.title ?: "Knowledge Check",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (currentQ.isTrueFalse) "True / False Format" else "Multiple Choice Question",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Q ${currentQuestionIndex + 1} of ${quizQuestions.size}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Question Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = currentQ.questionText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Options List
            itemsIndexed(optionsList) { index, optionText ->
                val isSelectedByUser = selectedOptionIndex == index
                val isCorrectAnswer = index == currentQ.correctAnswerIndex
                val isAnswered = selectedOptionIndex != null

                val backgroundColor: Color
                val borderColor: Color
                val iconVector: androidx.compose.ui.graphics.vector.ImageVector?
                val iconTint: Color

                if (isAnswered) {
                    if (isCorrectAnswer) {
                        backgroundColor = SuccessGreen.copy(alpha = 0.15f)
                        borderColor = SuccessGreen
                        iconVector = Icons.Default.CheckCircle
                        iconTint = SuccessGreen
                    } else if (isSelectedByUser) {
                        backgroundColor = ErrorRed.copy(alpha = 0.15f)
                        borderColor = ErrorRed
                        iconVector = Icons.Default.Cancel
                        iconTint = ErrorRed
                    } else {
                        backgroundColor = MaterialTheme.colorScheme.surface
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        iconVector = null
                        iconTint = Color.Transparent
                    }
                } else {
                    backgroundColor = MaterialTheme.colorScheme.surface
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    iconVector = null
                    iconTint = Color.Transparent
                }

                Card(
                    modifier = Modifier
                        .testTag("quiz_option_$index")
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                        .clickable(enabled = !isAnswered) { onSelectOption(index) },
                    colors = CardDefaults.cardColors(containerColor = backgroundColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAnswered && (isCorrectAnswer || isSelectedByUser)) {
                                            if (isCorrectAnswer) SuccessGreen else ErrorRed
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ('A' + index).toString(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isAnswered && (isCorrectAnswer || isSelectedByUser)) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelectedByUser || isCorrectAnswer && isAnswered) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (iconVector != null) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Expandable Detailed Explanation & Logic Box
            item {
                AnimatedVisibility(
                    visible = showExplanation,
                    enter = fadeIn() + expandVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .testTag("quiz_explanation_card")
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Detailed Explanation & Logic",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = currentQ.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Action row: Next Question
        if (selectedOptionIndex != null) {
            Button(
                onClick = onNextQuestion,
                modifier = Modifier
                    .testTag("next_question_button")
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (currentQuestionIndex + 1 < quizQuestions.size) "Next Question" else "View Score & Results",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuizResultsView(
    studySet: StudySet?,
    questions: List<QuizQuestion>,
    userAnswers: Map<Int, Int>,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReviewList by remember { mutableStateOf(false) }

    val correctCount = questions.indices.count { index ->
        val chosen = userAnswers[index]
        chosen == questions[index].correctAnswerIndex
    }
    val total = questions.size
    val scorePercentage = if (total > 0) ((correctCount.toFloat() / total.toFloat()) * 100).toInt() else 0

    val gradeTitle = when {
        scorePercentage >= 90 -> "Outstanding Mastery! 🌟"
        scorePercentage >= 70 -> "Great Conceptual Grasp! 👏"
        scorePercentage >= 50 -> "Good Effort! Keep Reviewing 📖"
        else -> "Need More Practice 💪"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Summary Score Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (scorePercentage >= 70) SuccessGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = if (scorePercentage >= 70) SuccessGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Text(
                    text = "$scorePercentage%",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (scorePercentage >= 70) SuccessGreen else MaterialTheme.colorScheme.primary
                )

                Text(
                    text = gradeTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "You correctly answered $correctCount out of $total questions on ${studySet?.title ?: "this topic"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRestart,
                        modifier = Modifier
                            .testTag("retake_quiz_button")
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retake Quiz")
                    }

                    OutlinedButton(
                        onClick = { showReviewList = !showReviewList },
                        modifier = Modifier
                            .testTag("review_answers_button")
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (showReviewList) "Hide Review" else "Review Missed")
                    }
                }
            }
        }

        // Detailed Missed / Answered Questions Review
        if (showReviewList) {
            Text(
                text = "Detailed Question Review:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(questions) { index, q ->
                    val chosen = userAnswers[index]
                    val isCorrect = chosen == q.correctAnswerIndex

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCorrect) SuccessGreen.copy(alpha = 0.08f) else ErrorRed.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCorrect) SuccessGreen else ErrorRed
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Question ${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCorrect) SuccessGreen else ErrorRed
                                )
                                Text(
                                    text = if (isCorrect) "✓ Correct" else "✗ Incorrect",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCorrect) SuccessGreen else ErrorRed
                                )
                            }
                            Text(
                                text = q.questionText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Logic: ${q.explanation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyQuizView(
    onGoToInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "No Quiz Generated Yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Provide your study notes in the Material Input tab to generate interactive practice quizzes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onGoToInput,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Go to Material Input")
                }
            }
        }
    }
}
