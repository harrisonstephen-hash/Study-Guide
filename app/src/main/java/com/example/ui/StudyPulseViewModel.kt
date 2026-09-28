package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.StudyPulseDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.DifficultyLevel
import com.example.data.model.Flashcard
import com.example.data.model.QuizQuestion
import com.example.data.model.StudySet
import com.example.data.model.StudyTab
import com.example.data.model.UploadedFileItem
import com.example.data.repository.StudyRepository
import com.example.util.DocumentHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class CardFilter { ALL, NEED_REVIEW, MASTERED }

class StudyPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StudyRepository

    init {
        val db = StudyPulseDatabase.getDatabase(application)
        repository = StudyRepository(db.studyDao())
    }

    // User Profile
    private val _userName = MutableStateFlow("Harrison")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun updateUserName(name: String) {
        if (name.isNotBlank()) {
            _userName.value = name.trim()
        }
    }

    // App Navigation & Theme
    private val _selectedTab = MutableStateFlow(StudyTab.MATERIAL_INPUT)
    val selectedTab: StateFlow<StudyTab> = _selectedTab.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun selectTab(tab: StudyTab) {
        _selectedTab.value = tab
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    // Study Sets list
    val allStudySets: StateFlow<List<StudySet>> = repository.allStudySets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Study Set
    private val _activeStudySet = MutableStateFlow<StudySet?>(null)
    val activeStudySet: StateFlow<StudySet?> = _activeStudySet.asStateFlow()

    // Flashcards for active set
    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val flashcards: StateFlow<List<Flashcard>> = _flashcards.asStateFlow()

    // Quiz Questions for active set
    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    // Material Input tab state
    private val _rawNotes = MutableStateFlow("")
    val rawNotes: StateFlow<String> = _rawNotes.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<UploadedFileItem>>(emptyList())
    val attachedFiles: StateFlow<List<UploadedFileItem>> = _attachedFiles.asStateFlow()

    fun addAttachedFiles(items: List<UploadedFileItem>) {
        _attachedFiles.value = _attachedFiles.value + items
        _errorMessage.value = null
    }

    fun removeAttachedFile(id: String) {
        _attachedFiles.value = _attachedFiles.value.filterNot { it.id == id }
    }

    fun clearAllAttachedFiles() {
        _attachedFiles.value = emptyList()
        _attachedImageBitmap.value = null
        _attachedImageUri.value = null
    }

    private val _attachedImageBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val attachedImageBitmap: StateFlow<android.graphics.Bitmap?> = _attachedImageBitmap.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<android.net.Uri?>(null)
    val attachedImageUri: StateFlow<android.net.Uri?> = _attachedImageUri.asStateFlow()

    fun setAttachedImage(bitmap: android.graphics.Bitmap?, uri: android.net.Uri?) {
        _attachedImageBitmap.value = bitmap
        _attachedImageUri.value = uri
        _errorMessage.value = null
    }

    fun clearAttachedImage() {
        _attachedImageBitmap.value = null
        _attachedImageUri.value = null
    }

    private val _difficulty = MutableStateFlow(DifficultyLevel.INTERMEDIATE)
    val difficulty: StateFlow<DifficultyLevel> = _difficulty.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingStep = MutableStateFlow("")
    val processingStep: StateFlow<String> = _processingStep.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateRawNotes(text: String) {
        _rawNotes.value = text
        _errorMessage.value = null
    }

    fun setDifficulty(diff: DifficultyLevel) {
        _difficulty.value = diff
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // Flashcard interaction state
    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    private val _isCardFlipped = MutableStateFlow(false)
    val isCardFlipped: StateFlow<Boolean> = _isCardFlipped.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _cardFilter = MutableStateFlow(CardFilter.ALL)
    val cardFilter: StateFlow<CardFilter> = _cardFilter.asStateFlow()

    fun toggleCardFlip() {
        _isCardFlipped.value = !_isCardFlipped.value
    }

    fun setCardFlipped(flipped: Boolean) {
        _isCardFlipped.value = flipped
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun setCardFilter(filter: CardFilter) {
        _cardFilter.value = filter
        _currentCardIndex.value = 0
        _isCardFlipped.value = false
    }

    fun nextCard(totalCards: Int) {
        if (totalCards > 0) {
            _currentCardIndex.value = (_currentCardIndex.value + 1) % totalCards
            _isCardFlipped.value = false
        }
    }

    fun prevCard(totalCards: Int) {
        if (totalCards > 0) {
            _currentCardIndex.value = if (_currentCardIndex.value - 1 < 0) totalCards - 1 else _currentCardIndex.value - 1
            _isCardFlipped.value = false
        }
    }

    fun markCardMastery(card: Flashcard, isMastered: Boolean) {
        viewModelScope.launch {
            repository.updateFlashcardMastery(card.id, isMastered)
            // Update local list
            _flashcards.value = _flashcards.value.map {
                if (it.id == card.id) it.copy(isMastered = isMastered) else it
            }
        }
    }

    // Quiz tab state
    private val _currentQuizIndex = MutableStateFlow(0)
    val currentQuizIndex: StateFlow<Int> = _currentQuizIndex.asStateFlow()

    private val _selectedOptionIndex = MutableStateFlow<Int?>(null)
    val selectedOptionIndex: StateFlow<Int?> = _selectedOptionIndex.asStateFlow()

    private val _userAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val userAnswers: StateFlow<Map<Int, Int>> = _userAnswers.asStateFlow()

    private val _isQuizCompleted = MutableStateFlow(false)
    val isQuizCompleted: StateFlow<Boolean> = _isQuizCompleted.asStateFlow()

    private val _showExplanation = MutableStateFlow(false)
    val showExplanation: StateFlow<Boolean> = _showExplanation.asStateFlow()

    fun selectQuizOption(optionIndex: Int) {
        if (_selectedOptionIndex.value != null) return // Already answered
        val currentQIndex = _currentQuizIndex.value
        _selectedOptionIndex.value = optionIndex
        val updated = _userAnswers.value.toMutableMap()
        updated[currentQIndex] = optionIndex
        _userAnswers.value = updated
        _showExplanation.value = true
    }

    fun nextQuizQuestion() {
        val total = _quizQuestions.value.size
        if (_currentQuizIndex.value + 1 < total) {
            _currentQuizIndex.value += 1
            _selectedOptionIndex.value = _userAnswers.value[_currentQuizIndex.value]
            _showExplanation.value = _selectedOptionIndex.value != null
        } else {
            _isQuizCompleted.value = true
        }
    }

    fun restartQuiz() {
        _currentQuizIndex.value = 0
        _selectedOptionIndex.value = null
        _userAnswers.value = emptyMap()
        _isQuizCompleted.value = false
        _showExplanation.value = false
    }

    // Concept Explainer tab state
    private val _isEli5Mode = MutableStateFlow(true)
    val isEli5Mode: StateFlow<Boolean> = _isEli5Mode.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _followUpQuery = MutableStateFlow("")
    val followUpQuery: StateFlow<String> = _followUpQuery.asStateFlow()

    private val _isAskingFollowUp = MutableStateFlow(false)
    val isAskingFollowUp: StateFlow<Boolean> = _isAskingFollowUp.asStateFlow()

    fun toggleExplainerMode() {
        _isEli5Mode.value = !_isEli5Mode.value
    }

    fun updateFollowUpQuery(text: String) {
        _followUpQuery.value = text
    }

    fun sendFollowUp() {
        val query = _followUpQuery.value.trim()
        if (query.isBlank() || _isAskingFollowUp.value) return

        val userMsg = ChatMessage(isUser = true, text = query)
        _chatMessages.value = _chatMessages.value + userMsg
        _followUpQuery.value = ""
        _isAskingFollowUp.value = true

        viewModelScope.launch {
            try {
                val currentSet = _activeStudySet.value
                val contextNotes = currentSet?.rawContent ?: _rawNotes.value
                val answer = repository.askSocraticFollowUp(
                    contextNotes = contextNotes,
                    chatHistory = _chatMessages.value,
                    question = query,
                    isEli5 = _isEli5Mode.value
                )
                val tutorMsg = ChatMessage(isUser = false, text = answer)
                _chatMessages.value = _chatMessages.value + tutorMsg
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    isUser = false,
                    text = "I encountered an error retrieving the explanation. Please try again."
                )
            } finally {
                _isAskingFollowUp.value = false
            }
        }
    }

    // Sample Notes loader for 1-click test drive
    fun loadSampleNotes(category: String) {
        when (category) {
            "Algorithms" -> {
                _rawNotes.value = """
                    Binary Search Trees (BST) & Algorithmic Complexity:
                    A Binary Search Tree is a rooted binary tree data structure where each internal node satisfies the binary search property:
                    - Left subtree keys are strictly less than the node's key: ∀ y ∈ left(x), key(y) < key(x)
                    - Right subtree keys are strictly greater than the node's key: ∀ y ∈ right(x), key(y) > key(x)
                    
                    Time Complexity:
                    - Lookup: Average O(log n), Worst-case O(n) when degenerated into a linked list
                    - Insertion: Average O(log n), Worst-case O(n)
                    - Deletion: Average O(log n), Worst-case O(n)
                    
                    Traversals:
                    - In-order (Left, Root, Right): yields sorted ascending output
                    - Pre-order (Root, Left, Right): ideal for tree serialization and cloning
                    - Post-order (Left, Right, Root): ideal for bottom-up node deletion and cleanup
                    
                    Self-Balancing Trees (AVL & Red-Black):
                    - AVL maintains balance factor |height(left) - height(right)| <= 1 using left and right rotations
                    - Red-Black trees enforce node color rules guaranteeing path length ratio <= 2
                """.trimIndent()
            }
            "Biology" -> {
                _rawNotes.value = """
                    Cellular Respiration & Bioenergetics:
                    Cellular respiration is the metabolic pathway that oxidizes biochemical energy from nutrients into ATP (adenosine triphosphate).
                    Overall Chemical Equation:
                    C6H12O6 + 6 O2 -> 6 CO2 + 6 H2O + ~30-32 ATP + heat
                    
                    Three Major Phases:
                    1. Glycolysis:
                       - Takes place in the cytosol (anaerobic, requires no oxygen)
                       - Glucose (6C) is cleaved into 2 Pyruvate (3C)
                       - Net yield: 2 ATP (substrate-level phosphorylation) and 2 NADH
                    2. Citric Acid Cycle (Krebs Cycle):
                       - Occurs in mitochondrial matrix
                       - Pyruvate is converted to Acetyl-CoA, which enters the cycle
                       - Produces 2 ATP/GTP, 6 NADH, 2 FADH2, and releases CO2
                    3. Oxidative Phosphorylation & Electron Transport Chain:
                       - Embedded in inner mitochondrial membrane
                       - Electrons from NADH and FADH2 move across complexes I-IV
                       - Terminal electron acceptor is O2, forming H2O
                       - Proton-motive force drives rotary catalysis in ATP Synthase (yielding ~26-28 ATP)
                """.trimIndent()
            }
            "Physics" -> {
                _rawNotes.value = """
                    Quantum Superposition & Quantum Computing:
                    In classical computing, a bit exists strictly in state 0 or 1.
                    In quantum mechanics, a qubit exists as a normalized linear combination of basis states |0⟩ and |1⟩:
                    |ψ⟩ = α|0⟩ + β|1⟩, where |α|² + |β|² = 1.
                    
                    Key Phenomena:
                    1. Quantum Superposition:
                       - Allows quantum algorithms (e.g. Shor's and Grover's) to evaluate multidimensional state spaces concurrently.
                    2. Quantum Entanglement:
                       - Non-local state correlation where measuring one entangled qubit instantly determines the state of the other.
                    3. Measurement & Wavefunction Collapse:
                       - Observing a qubit forces it into a definite classical state 0 (probability |α|²) or 1 (probability |β|²).
                    4. Quantum Gates:
                       - Hadamard gate (H) creates equal superposition from |0⟩: (|0⟩ + |1⟩)/√2.
                       - CNOT gate entangles control and target qubits.
                """.trimIndent()
            }
            "Psychology" -> {
                _rawNotes.value = """
                    Cognitive Psychology: Memory Systems & Cognitive Biases:
                    Human memory operates through distinct multi-store architectural stages:
                    1. Sensory Memory:
                       - Iconic (visual) holds traces for ~250-500ms
                       - Echoic (auditory) retains acoustic patterns for ~3-4 seconds
                    2. Working Memory (Baddeley-Hitch Model):
                       - Central Executive: coordinates attentional focus and task switching
                       - Phonological Loop: articulatory rehearsal of verbal speech tokens
                       - Visuospatial Sketchpad: generates and manipulates mental imagery
                       - Episodic Buffer: binds multi-modal information with temporal sequencing
                    3. Long-Term Memory (LTM):
                       - Declarative (Explicit): Episodic (personal autobiographical events) and Semantic (factual world knowledge)
                       - Non-Declarative (Implicit): Procedural motor skills, priming, and classical conditioning
                    
                    Cognitive Retrieval & Biases:
                    - Spacing Effect: distributed practice produces superior retention over massed cramming
                    - Testing Effect (Active Recall): retrieval effort induces neuroplastic long-term potentiation (LTP)
                    - Confirmation Bias: tendency to search for, interpret, and recall information confirming preexisting beliefs
                    - Availability Heuristic: estimating event probability based on how easily examples come to mind
                """.trimIndent()
            }
            "Bank Statement" -> {
                _rawNotes.value = """
                    FIRST NATIONAL BANK - MONTHLY STATEMENT OF ACCOUNT
                    Account Type: Premier Checking (Account # ****4918)
                    Statement Period: January 1, 2026 - January 31, 2026

                    ACCOUNT FINANCIAL SUMMARY:
                    • Starting Ledger Balance: $5,360.57
                    • Total Deposits & Credits (2 items): +$5,250.00
                    • Total Withdrawals & Debits (48 items): -$3,768.42
                    • Net Monthly Cash Flow: +$1,481.58
                    • Ending Ledger Balance: $6,842.15

                    DEPOSITS & INCOME:
                    • Jan 05: ACME CORP DIRECT DEP PAYROLL: +$2,625.00
                    • Jan 19: ACME CORP DIRECT DEP PAYROLL: +$2,625.00

                    CATEGORIZED DEBITS & WITHDRAWALS:
                    1. Housing & Living ($1,650.00):
                       • Jan 02: LUXE PROPERTIES ONLINE RENT PAYMENT: -$1,650.00
                    2. Utilities & Communications ($240.50):
                       • Jan 08: CITY WATER & POWER: -$115.50
                       • Jan 12: FIBER BROADBAND 1GBPS: -$80.00
                       • Jan 22: MOBILE WIRELESS BILL: -$45.00
                    3. Groceries & Essentials ($480.25):
                       • Jan 03: WHOLE FOODS MARKET: -$142.10
                       • Jan 10: TRADER JOE'S: -$118.40
                       • Jan 17: COSTCO WHOLESALE: -$165.75
                       • Jan 24: LOCAL ORGANIC MARKET: -$54.00
                    4. Food & Dining Out ($420.00):
                       • 14 restaurant and cafe transactions totaling -$420.00
                    5. Recurring Digital Subscriptions ($75.47):
                       • Jan 06: NETFLIX.COM STREAMING 4K: -$15.49
                       • Jan 11: SPOTIFY USA PREMIUM: -$11.99
                       • Jan 15: EQUINOX FITNESS GYM DUES: -$45.00
                       • Jan 28: APPLE.COM/BILL ICLOUD 200GB: -$2.99
                    6. Transportation & Fuel ($185.00):
                       • Jan 07: SHELL OIL GAS STATION: -$55.00
                       • Jan 18: CHEVRON FUEL: -$50.00
                       • Jan 25: METRO TRANSIT CARD RELOAD: -$80.00
                    7. Fees & Surcharges ($3.50):
                       • Jan 14: NON-NETWORK ATM CONVENIENCE SURCHARGE: -$3.50
                """.trimIndent()
            }
        }
    }

    // Process Notes via Gemini AI / Local fallback
    fun processNotes() {
        val notes = _rawNotes.value.trim()
        val image = _attachedImageBitmap.value
        val files = _attachedFiles.value
        if (notes.isBlank() && image == null && files.isEmpty()) {
            _errorMessage.value = "Please paste study notes or upload PDF bank statements/images!"
            return
        }

        _isProcessing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val rawFiles = DocumentHelper.prepareRawFileData(getApplication(), files)
                _processingStep.value = when {
                    files.any { it.isPdf } -> "Analyzing entire PDF bank statement across pages..."
                    files.isNotEmpty() -> "Processing ${files.size} uploaded documents & statements..."
                    image != null -> "Inspecting & transcribing document photo..."
                    else -> "Analyzing with Gemini AI..."
                }
                delay(350)
                _processingStep.value = "Auditing transactions, cashflow & 10+ flashcards..."
                delay(300)
                _processingStep.value = "Formulating 6+ adaptive quiz questions..."

                val newSetId = repository.processAndSaveNotes(notes, _difficulty.value.label, rawFiles, image)

                _processingStep.value = "Finalizing study set..."
                delay(200)

                // Load newly created study set
                loadStudySetById(newSetId)

                // Automatically advance user to Flashcards tab to immediately enjoy the results!
                _selectedTab.value = StudyTab.FLASHCARDS
            } catch (e: Exception) {
                _errorMessage.value = "Processing failed: ${e.message ?: "Unknown error"}. Check connection or try sample notes."
            } finally {
                _isProcessing.value = false
                _processingStep.value = ""
            }
        }
    }

    fun deleteStudySet(setId: Long) {
        viewModelScope.launch {
            repository.deleteStudySet(setId)
            if (_activeStudySet.value?.id == setId) {
                _activeStudySet.value = null
                _flashcards.value = emptyList()
                _quizQuestions.value = emptyList()
            }
        }
    }

    fun loadStudySetById(setId: Long) {
        viewModelScope.launch {
            repository.getStudySetById(setId).collect { set ->
                _activeStudySet.value = set
            }
        }
        viewModelScope.launch {
            repository.getFlashcards(setId).collect { cards ->
                _flashcards.value = cards
                _currentCardIndex.value = 0
                _isCardFlipped.value = false
            }
        }
        viewModelScope.launch {
            repository.getQuizQuestions(setId).collect { questions ->
                _quizQuestions.value = questions
                restartQuiz()
            }
        }
        // Initialize chat with greeting for this set
        _chatMessages.value = listOf(
            ChatMessage(
                isUser = false,
                text = "Welcome to your StudyPulse Socratic Tutor! I've analyzed your notes. Feel free to toggle between 'Explain Like I'm 5' and 'Technical Deep Dive', or ask me any question to clarify concepts!"
            )
        )
    }

    init {
        // Automatically check if any existing study sets exist or seed sample if database is fresh
        viewModelScope.launch {
            repository.latestStudySet.collect { set ->
                if (set != null && _activeStudySet.value == null) {
                    loadStudySetById(set.id)
                } else if (set == null && _activeStudySet.value == null) {
                    // Seed initial sample session for instant demo pleasure
                    loadSampleNotes("Algorithms")
                    processNotes()
                }
            }
        }
    }
}
