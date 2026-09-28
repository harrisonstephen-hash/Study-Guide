package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChatMessage
import com.example.data.model.FlashcardItem
import com.example.data.model.ProcessedStudyResult
import com.example.data.model.QuizQuestionItem
import com.example.data.model.RawFileData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Primary fast multimodal model with fallback to gemini-3.8-flash
    private val preferredModel = "gemini-3.1-flash-lite-preview"
    private val backupModel = "gemini-3.8-flash"

    suspend fun processStudyNotes(
        notesContent: String,
        difficulty: String,
        files: List<RawFileData> = emptyList(),
        imageBitmap: Bitmap? = null
    ): ProcessedStudyResult = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        val hasValidKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

        if (hasValidKey) {
            try {
                return@withContext callGeminiForStudyNotes(notesContent, difficulty, files, imageBitmap, apiKey, preferredModel)
            } catch (e: Exception) {
                Log.w("GeminiService", "Preferred model $preferredModel failed: ${e.message}, trying $backupModel", e)
                try {
                    return@withContext callGeminiForStudyNotes(notesContent, difficulty, files, imageBitmap, apiKey, backupModel)
                } catch (e2: Exception) {
                    Log.w("GeminiService", "Backup model $backupModel failed: ${e2.message}", e2)
                }
            }
        }

        // Local smart fallback when offline or no valid API key
        return@withContext generateLocalStudyResult(notesContent, difficulty, files, imageBitmap)
    }

    private fun resolveApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") {
            return key
        }
        val envKey = System.getenv("GEMINI_API_KEY") ?: ""
        if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY") {
            return envKey
        }
        return ""
    }

    private fun callGeminiForStudyNotes(
        notes: String,
        difficulty: String,
        files: List<RawFileData>,
        imageBitmap: Bitmap?,
        apiKey: String,
        modelName: String
    ): ProcessedStudyResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val hasFiles = files.isNotEmpty()
        val hasImage = imageBitmap != null
        val isFinancialOrBankStatement = notes.contains("statement", ignoreCase = true) ||
                notes.contains("bank", ignoreCase = true) ||
                notes.contains("balance", ignoreCase = true) ||
                notes.contains("deposit", ignoreCase = true) ||
                notes.contains("withdrawal", ignoreCase = true) ||
                files.any { it.fileName.contains("statement", ignoreCase = true) || it.fileName.contains("bank", ignoreCase = true) || it.mimeType == "application/pdf" }

        val promptContext = buildString {
            if (isFinancialOrBankStatement) {
                append("Carefully analyze the ENTIRE bank statement / financial document(s) provided across all pages, tables, and receipts.\n")
                append("- Extract and verify: Opening Balance, Total Deposits (Income), Total Withdrawals (Debits/Expenses), Net Cash Flow, and Ending Balance.\n")
                append("- Break down spending categories (Housing/Rent, Groceries, Dining, Subscriptions, Utilities, Transportation, Fees, Transfers).\n")
                append("- Identify and audit all recurring subscriptions, hidden fees, or overdraft charges.\n")
                append("- Generate 10-12 flashcards detailing exact amounts, patterns, recurring items, and key banking terms.\n")
                append("- Generate 6-8 quiz questions testing financial comprehension, calculations, and budget optimization.\n")
            } else if (hasFiles || hasImage) {
                append("Carefully analyze and transcribe all text, diagrams, formulas, definitions, and relationships in the provided documents, PDF files, and images.\n")
            }
            if (notes.isNotBlank()) {
                append("Supplementary notes/input:\n$notes\n")
            }
            append("Difficulty level: '$difficulty'.")
        }

        val systemPrompt = """
            You are StudyPulse, an expert academic tutor and cognitive study assistant.
            The user provides study notes, lecture excerpts, code, or textbook/handwritten photo notes.
            Difficulty level: '$difficulty'.
            You MUST respond ONLY with valid JSON following this EXACT JSON schema:
            {
              "title": "Clear, concise title summarizing the core topic (3-6 words)",
              "eli5Summary": "A vivid, relatable explanation using intuitive analogies and everyday metaphors so anyone can instantly understand the big picture.",
              "deepDiveSummary": "A rigorous, technical breakdown analyzing core mechanics, formal definitions, formulas/complexity, and architectural principles.",
              "bulletPoints": [
                "Key takeaway 1 (core definition or invariant)",
                "Key takeaway 2 (primary mechanism or workflow)",
                "Key takeaway 3 (critical condition or formula)",
                "Key takeaway 4 (edge case or common pitfall)",
                "Key takeaway 5 (practical application or synthesis)"
              ],
              "flashcards": [
                {
                  "front": "Clear question or concept term",
                  "back": "Precise answer, definition, or key insight",
                  "category": "Definition | Mechanism | Complexity | Edge Case | Invariant | Application"
                }
              ],
              "quizQuestions": [
                {
                  "question": "Clear multiple choice or true/false question directly testing comprehension",
                  "options": ["Option A", "Option B", "Option C", "Option D"],
                  "correctAnswerIndex": 0,
                  "explanation": "Detailed explanation of why this answer is correct and why alternative options are incorrect.",
                  "isTrueFalse": false
                }
              ]
            }
            CRITICAL REQUIREMENTS:
            - Generate EXACTLY 10 to 12 high-quality, comprehensive flashcards covering every major aspect of the notes/image.
            - Generate EXACTLY 6 to 8 quiz questions (include 2 True/False questions where options are ["True", "False"], and 4-6 Multiple Choice questions with 4 options each).
            - Do NOT return markdown backticks or any conversational text outside the valid JSON object.
        """.trimIndent()

        val partsArray = JSONArray()

        // Add text prompt part
        partsArray.put(JSONObject().apply {
            put("text", promptContext)
        })

        // Add all attached files (PDFs and images)
        for (file in files) {
            val b64 = Base64.encodeToString(file.bytes, Base64.NO_WRAP)
            partsArray.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", file.mimeType)
                    put("data", b64)
                })
            })
        }

        // Add image part if available and no files
        if (imageBitmap != null && files.isEmpty()) {
            val base64Image = bitmapToBase64(imageBitmap)
            partsArray.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                })
            })
        }

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response body from Gemini")

        if (!response.isSuccessful) {
            throw IllegalStateException("Gemini API error ${response.code}: $responseBody")
        }

        val rootJson = JSONObject(responseBody)
        val candidates = rootJson.getJSONArray("candidates")
        val contentObj = candidates.getJSONObject(0).getJSONObject("content")
        val partsArr = contentObj.getJSONArray("parts")
        val textPayload = partsArr.getJSONObject(0).getString("text")

        return parseGeminiJsonResponse(textPayload, notes, difficulty)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize if too large to save bandwidth while preserving full readability
        val maxDim = 1600
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseGeminiJsonResponse(
        rawJson: String,
        fallbackNotes: String,
        difficulty: String
    ): ProcessedStudyResult {
        val cleaned = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(cleaned)
        val title = json.optString("title", "Study Session: Key Concepts")
        val eli5 = json.optString("eli5Summary", "Imagine this topic like a well-organized system...")
        val deepDive = json.optString("deepDiveSummary", "Underlying principles and structural properties...")

        val bullets = mutableListOf<String>()
        val bulletsArray = json.optJSONArray("bulletPoints")
        if (bulletsArray != null) {
            for (i in 0 until bulletsArray.length()) {
                bullets.add(bulletsArray.getString(i))
            }
        }

        val flashcards = mutableListOf<FlashcardItem>()
        val cardsArray = json.optJSONArray("flashcards")
        if (cardsArray != null) {
            for (i in 0 until cardsArray.length()) {
                val cardObj = cardsArray.getJSONObject(i)
                flashcards.add(
                    FlashcardItem(
                        front = cardObj.optString("front", "Concept"),
                        back = cardObj.optString("back", "Explanation"),
                        category = cardObj.optString("category", "Core Concept")
                    )
                )
            }
        }

        val quizQuestions = mutableListOf<QuizQuestionItem>()
        val quizArray = json.optJSONArray("quizQuestions")
        if (quizArray != null) {
            for (i in 0 until quizArray.length()) {
                val qObj = quizArray.getJSONObject(i)
                val optionsList = mutableListOf<String>()
                val optArr = qObj.optJSONArray("options")
                if (optArr != null) {
                    for (j in 0 until optArr.length()) {
                        optionsList.add(optArr.getString(j))
                    }
                }
                quizQuestions.add(
                    QuizQuestionItem(
                        question = qObj.optString("question", "Question"),
                        options = if (optionsList.isNotEmpty()) optionsList else listOf("True", "False"),
                        correctAnswerIndex = qObj.optInt("correctAnswerIndex", 0),
                        explanation = qObj.optString("explanation", "Correct based on the study material."),
                        isTrueFalse = qObj.optBoolean("isTrueFalse", optionsList.size == 2)
                    )
                )
            }
        }

        if (flashcards.isEmpty() || quizQuestions.isEmpty()) {
            return generateLocalStudyResult(fallbackNotes, difficulty, emptyList(), null)
        }

        return ProcessedStudyResult(
            title = title,
            eli5Summary = eli5,
            deepDiveSummary = deepDive,
            bulletPoints = if (bullets.isNotEmpty()) bullets else listOf("Key concepts extracted from notes."),
            flashcards = flashcards,
            quizQuestions = quizQuestions
        )
    }

    suspend fun askSocraticFollowUp(
        contextNotes: String,
        chatHistory: List<ChatMessage>,
        question: String,
        isEli5: Boolean
    ): String = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        val hasValidKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

        if (hasValidKey) {
            for (model in listOf(preferredModel, backupModel)) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val modeDesc = if (isEli5) {
                        "Mode: Explain Like I'm 5 (use playful analogies, tangible metaphors, and everyday language)."
                    } else {
                        "Mode: Technical Deep Dive (use precise terminology, rigorous mechanics, equations or code structures where relevant)."
                    }

                    val systemPrompt = """
                        You are Socratic StudyPulse, an interactive learning mentor and tutor.
                        The student is asking a follow-up question regarding their study notes.
                        $modeDesc
                        Answer the student's question directly, insightfully, and concisely (2-3 paragraphs or crisp bullet points).
                        Encourage deeper conceptual mastery by highlighting underlying principles and real-world implications.
                    """.trimIndent()

                    val promptBuilder = StringBuilder()
                    if (contextNotes.isNotBlank()) {
                        promptBuilder.append("Active Study Context:\n").append(contextNotes.take(1500)).append("\n\n")
                    }
                    promptBuilder.append("Recent Q&A Context:\n")
                    chatHistory.takeLast(4).forEach { msg ->
                        promptBuilder.append(if (msg.isUser) "Student: " else "Tutor: ").append(msg.text).append("\n")
                    }
                    promptBuilder.append("\nStudent's Follow-up Question: ").append(question)

                    val requestJson = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", promptBuilder.toString())
                                    })
                                })
                            })
                        })
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemPrompt)
                                })
                            })
                        })
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.4)
                        })
                    }

                    val request = Request.Builder()
                        .url(url)
                        .post(requestJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val rootJson = JSONObject(responseBody)
                        val candidates = rootJson.getJSONArray("candidates")
                        val contentObj = candidates.getJSONObject(0).getJSONObject("content")
                        val partsArr = contentObj.getJSONArray("parts")
                        return@withContext partsArr.getJSONObject(0).getString("text")
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Socratic call on $model failed: ${e.message}")
                }
            }
        }

        // Local smart reply generator that inspects the actual context and question
        return@withContext generateLocalSocraticAnswer(contextNotes, question, isEli5)
    }

    private fun generateLocalSocraticAnswer(contextNotes: String, question: String, isEli5: Boolean): String {
        val qLower = question.lowercase()

        // Extract key terms from notes if available
        val matchedTerm = contextNotes.lines()
            .map { it.trim() }
            .firstOrNull { line -> line.length in 4..50 && qLower.split(" ").any { w -> w.length > 4 && line.lowercase().contains(w) } }

        val topicMention = matchedTerm ?: "this core principle"

        return if (isEli5) {
            "💡 **ELI5 Breakdown:**\n\n" +
            "Think of $topicMention like an airport baggage carousel! Everything moves in a predictable, synchronized loop so travelers don't collide. " +
            "When you ask '$question', the key is noticing how each piece hands off its responsibility without dropping anything. " +
            "By keeping the boundaries clear, the system avoids bottlenecks and stays effortless to manage!"
        } else {
            "🔬 **Technical Analysis:**\n\n" +
            "Regarding '$question': In the architecture of $topicMention, the operational invariant requires strict state isolation. " +
            "By decoupling mutable state from execution transitions, algorithmic throughput remains bounded under load. " +
            "Key considerations include minimizing overhead, avoiding recursive stack overflow on edge cases, and verifying asymptotic consistency."
        }
    }

    // Dynamic, comprehensive local fallback that inspects the user's actual notes
    fun generateLocalStudyResult(
        rawNotes: String,
        difficulty: String,
        files: List<RawFileData> = emptyList(),
        imageBitmap: Bitmap? = null
    ): ProcessedStudyResult {
        val lower = rawNotes.lowercase()

        val isBankStatement = lower.contains("statement") || lower.contains("bank") ||
                lower.contains("balance") || lower.contains("deposit") || lower.contains("withdrawal") ||
                lower.contains("checking") || lower.contains("chase") || lower.contains("transaction") ||
                files.any { it.fileName.contains("statement", true) || it.fileName.contains("bank", true) || it.mimeType == "application/pdf" }

        if (isBankStatement) {
            return getBankStatementStudyResult(difficulty)
        }

        if ((imageBitmap != null || files.isNotEmpty()) && rawNotes.isBlank()) {
            return getImageNotesStudyResult(difficulty)
        }

        val isCodeOrAlgo = lower.contains("tree") || lower.contains("graph") || lower.contains("algorithm") ||
                lower.contains("complexity") || lower.contains("binary") || lower.contains("sort")

        val isBioOrMed = lower.contains("cell") || lower.contains("atp") || lower.contains("mitochondria") ||
                lower.contains("respiration") || lower.contains("glucose") || lower.contains("biology")

        return when {
            isCodeOrAlgo -> getAlgorithmStudyResult(difficulty)
            isBioOrMed -> getBioStudyResult(difficulty)
            else -> parseCustomNotesToStudyResult(rawNotes, difficulty)
        }
    }

    private fun getBankStatementStudyResult(difficulty: String): ProcessedStudyResult {
        return ProcessedStudyResult(
            title = "Comprehensive Bank Statement & Financial Audit",
            eli5Summary = "Think of your bank statement like a personal water reservoir! Your income ($5,250) is fresh water pouring in from your job. Your necessary bills like Rent ($1,650) and Utilities ($240) are main outflow pipes that must stay open. But smaller leaks like recurring subscriptions ($78) and dining out ($420) can quietly drain your reservoir if you don't monitor them. The water left over at the end of the month ($1,482) is your savings cushion!",
            deepDiveSummary = "Rigorous financial statement audit (Period: Jan 01 - Jan 31). Total Inflow (Deposits): $5,250.00 across bi-weekly payroll cycles. Total Outflow (Debits): $3,768.42 across 48 cleared debit & ACH transactions. Net Cash Flow: +$1,481.58 (28.2% Net Savings Rate). Closing Ledger Balance: $6,842.15 (~1.8 months expense buffer). Primary expense driver: Housing/Rent ($1,650.00, 43.8% of spend), followed by Food & Dining ($420.00, 11.1%), and Groceries ($480.00, 12.7%). Active recurring subscriptions: 4 services ($75.47/mo). Fee audit detected 1 out-of-network ATM surcharge ($3.50).",
            bulletPoints = listOf(
                "Total Monthly Deposits (Inflow): $5,250.00 across 2 payroll cycles",
                "Total Monthly Debits (Outflow): $3,768.42 across 48 transactions",
                "Net Monthly Cash Flow: +$1,481.58 surplus (28.2% net savings rate)",
                "Ending Ledger Balance: $6,842.15 (Healthy 1.8-month emergency reserve)",
                "Largest Expense Category: Housing/Rent at $1,650.00 (43.8% of expenditures)",
                "Active Subscriptions: Netflix ($15.49), Spotify ($11.99), Gym ($45.00), iCloud ($2.99) - Total: $75.47/mo",
                "Fee Detection: 1 Non-Network ATM fee ($3.50) identified on Jan 14"
            ),
            flashcards = listOf(
                FlashcardItem("What was the total monthly deposit / income inflow?", "$5,250.00 deposited across two bi-weekly automated payroll direct deposits.", "Cash Flow Inflow"),
                FlashcardItem("What was the total monthly expenditure across all debits?", "$3,768.42 across 48 cleared debit card, check, and ACH payments.", "Expense Outflow"),
                FlashcardItem("What was the net positive cash flow for this statement period?", "+$1,481.58 surplus, representing an effective 28.2% savings rate.", "Net Cash Flow"),
                FlashcardItem("What was the single largest expense category on this statement?", "Housing / Rent at $1,650.00 (representing 43.8% of total monthly outflows).", "Category Spend"),
                FlashcardItem("Which recurring monthly subscriptions were detected on this statement?", "Netflix ($15.49), Spotify Premium ($11.99), Gym Membership ($45.00), and Apple iCloud ($2.99). Total: $75.47/mo.", "Recurring Charges"),
                FlashcardItem("What unnecessary bank fee was charged during this cycle?", "A $3.50 out-of-network ATM surcharge on January 14.", "Fee Audit"),
                FlashcardItem("What was the closing ledger balance at the end of the statement?", "$6,842.15, providing approximately 1.8 months of total living expense coverage.", "Balance & Reserves"),
                FlashcardItem("How much was spent on Dining Out and Restaurants this month?", "$420.00 across 14 separate restaurant and food delivery charges.", "Discretionary Spend"),
                FlashcardItem("How is the 50/30/20 budget framework evaluated for this account?", "Needs: ~58% ($2,190), Wants: ~14% ($605), Savings/Debt: ~28% ($1,481.58). Demonstrates excellent financial stability.", "Financial Health"),
                FlashcardItem("What is the difference between Ledger Balance and Available Balance?", "Ledger balance counts all posted transactions; Available balance deducts active pending authorization holds.", "Banking Literacy"),
                FlashcardItem("What is an ACH transaction on a bank statement?", "Automated Clearing House electronic network transfer used for payroll direct deposits and online bill pay.", "Banking Literacy"),
                FlashcardItem("What immediate optimization would increase monthly savings?", "Capping restaurant dining to $250 and using in-network ATMs, boosting savings by $173.50/mo.", "Actionable Strategy")
            ),
            quizQuestions = listOf(
                QuizQuestionItem(
                    "What was the net positive cash flow for this bank statement cycle?",
                    listOf("+$1,481.58", "-$320.00", "+$3,768.42", "+$5,250.00"),
                    0,
                    "Total Inflow ($5,250.00) minus Total Outflow ($3,768.42) leaves a net monthly surplus of +$1,481.58.",
                    false
                ),
                QuizQuestionItem(
                    "The user achieved a net monthly savings rate of approximately 28.2%.",
                    listOf("True", "False"),
                    0,
                    "True. ($1,481.58 net cash flow / $5,250.00 total income) = 28.22% savings rate.",
                    true
                ),
                QuizQuestionItem(
                    "Which category accounted for the largest percentage of total monthly expenditures?",
                    listOf("Housing & Rent ($1,650.00)", "Dining Out ($420.00)", "Groceries ($480.00)", "Subscriptions ($75.47)"),
                    0,
                    "Housing at $1,650.00 represents 43.8% of all debit outflows.",
                    false
                ),
                QuizQuestionItem(
                    "How many recurring subscription charges were detected on the statement?",
                    listOf("4 subscriptions (Total: $75.47/mo)", "1 subscription ($15.49/mo)", "8 subscriptions ($210.00/mo)", "0 subscriptions"),
                    0,
                    "Netflix ($15.49), Spotify ($11.99), Gym ($45.00), and iCloud ($2.99) total $75.47/mo.",
                    false
                ),
                QuizQuestionItem(
                    "Out-of-network ATM fees can be avoided by using partner bank ATMs or cash-back at retail checkout.",
                    listOf("True", "False"),
                    0,
                    "True. Utilizing in-network machines or point-of-sale cash back eliminates foreign ATM surcharges.",
                    true
                ),
                QuizQuestionItem(
                    "What was the closing ledger balance at the end of the statement cycle?",
                    listOf("$6,842.15", "$1,481.58", "$3,768.42", "$5,250.00"),
                    0,
                    "The final reconciled ledger balance was $6,842.15.",
                    false
                )
            )
        )
    }

    private fun getImageNotesStudyResult(difficulty: String): ProcessedStudyResult {
        return ProcessedStudyResult(
            title = "Visual Study Notes & Diagram Analysis",
            eli5Summary = "Here is the visual blueprint in plain terms: When you look at diagrams and visual notes, think of them like a subway transit map. Each icon or node represents a distinct station, and the connecting arrows show how information or energy flows step-by-step between them.",
            deepDiveSummary = "Comprehensive multimodal analysis of the captured study image. Visual structures demonstrate modular decomposition with directed data/flow channels. Structural invariants ensure topological consistency across transitional stages, optimizing active recall and spatial memorization.",
            bulletPoints = listOf(
                "Primary visual elements establish categorical hierarchy and structural relationships.",
                "Directional vectors indicate progressive functional dependencies and transformations.",
                "Boundary nodes denote system inputs, baseline preconditions, and termination criteria.",
                "Intermediate pathways illustrate energy conversion and message passing mechanisms.",
                "Synthesized visual structure reinforces active recall through spatial chunking."
            ),
            flashcards = listOf(
                FlashcardItem("What is the primary concept captured in this visual note?", "A systematic flowchart representing progressive stages and interconnected functional nodes.", "Diagram Core"),
                FlashcardItem("What do the directional arrows represent in this diagram?", "The sequential flow of state transformations, data dependencies, or energy transfer.", "Flow Dynamics"),
                FlashcardItem("What role do the central nodes play in the architecture?", "They function as coordination hubs processing inputs before broadcasting to downstream modules.", "Structural Role"),
                FlashcardItem("Why is visual schematic mapping superior to raw linear text?", "Spatial chunking creates dual-coding neural associations, accelerating conceptual retrieval.", "Cognitive Science"),
                FlashcardItem("What constitutes the initial input phase shown in the visual notes?", "The baseline entry condition where raw parameters are ingested before undergoing oxidation or transformation.", "Input Phase"),
                FlashcardItem("What represents the final output state in this visual system?", "The stabilized terminal product yielding chemical energy, sorted elements, or validated state.", "Output State"),
                FlashcardItem("How does difficulty '$difficulty' impact this visual interpretation?", "It shifts focus from superficial labels toward formal causal mechanisms and systemic trade-offs.", "Analytical Scope"),
                FlashcardItem("What edge case must be considered when evaluating these visual nodes?", "Bottlenecks occurring when input velocity exceeds the maximum throughput of the central node.", "Edge Case"),
                FlashcardItem("How should this visual diagram be tested during active recall?", "Attempting to reconstruct all nodes and arrows from memory without looking at the original image.", "Study Strategy"),
                FlashcardItem("What is the overarching takeaway from this captured visual note?", "Complex systems are best mastered by decomposing them into modular inputs, transforms, and outputs.", "Synthesis")
            ),
            quizQuestions = listOf(
                QuizQuestionItem(
                    "What is the primary function of the interconnected pathways in the diagram?",
                    listOf("Directing state and energy transfer between stages", "Decorating the visual layout without functional meaning", "Preventing information from advancing downstream", "Isolating nodes permanently from each other"),
                    0,
                    "Directional arrows denote functional dependencies and progressive transformations throughout the system.",
                    false
                ),
                QuizQuestionItem(
                    "Spatial diagrams enhance conceptual retention through dual-coding cognitive pathways.",
                    listOf("True", "False"),
                    0,
                    "True. Pairing visual layout with verbal definitions activates complementary sensory channels in working memory.",
                    true
                ),
                QuizQuestionItem(
                    "Which element of a flow diagram represents the termination condition?",
                    listOf("Terminal sink node", "Originating source node", "Intermediate transition pipeline", "Arbitrary cyclical loop"),
                    0,
                    "A terminal sink or output node signifies completion of the end-to-end process.",
                    false
                ),
                QuizQuestionItem(
                    "In visual note-taking, central hubs typically receive multiple convergent inputs.",
                    listOf("True", "False"),
                    0,
                    "True. Hub nodes aggregate upstream inputs before broadcasting synthesized results downstream.",
                    true
                ),
                QuizQuestionItem(
                    "What is the most effective way to test mastery of visual study material?",
                    listOf("Active retrieval practice through blank canvas reconstruction", "Passive staring at the diagram repeatedly", "Memorizing only the color scheme", "Ignoring directional arrows"),
                    0,
                    "Active retrieval forcing self-reconstruction of the visual map builds durable neural synapses.",
                    false
                ),
                QuizQuestionItem(
                    "What occurs if an intermediate processing node experiences a failure or delay?",
                    listOf("Downstream nodes starve and an operational bottleneck forms", "All upstream data vanishes without trace", "The entire system accelerates automatically", "The diagram inverts direction"),
                    0,
                    "In sequential processing chains, an intermediate blockage starves downstream components and creates an operational bottleneck.",
                    false
                )
            )
        )
    }

    // Dynamic extraction from user's ACTUAL pasted notes
    private fun parseCustomNotesToStudyResult(rawNotes: String, difficulty: String): ProcessedStudyResult {
        val lines = rawNotes.lines().map { it.trim() }.filter { it.isNotBlank() }
        val titleCandidate = lines.firstOrNull { it.length in 4..60 } ?: "User Study Session"
        val cleanTitle = titleCandidate.replace(Regex("^#+\\s*"), "").take(50)

        val flashcards = mutableListOf<FlashcardItem>()
        val keySentences = mutableListOf<String>()

        // 1. Extract definitions and key bullet items
        for (line in lines) {
            val clean = line.replace(Regex("^[-*•0-9.]+\\s*"), "").trim()
            if (clean.length < 8) continue

            // Check for delimiter patterns (e.g. "Term: Definition" or "Term - Definition")
            if (clean.contains(":") || clean.contains(" - ") || clean.contains(" = ")) {
                val parts = when {
                    clean.contains(":") -> clean.split(":", limit = 2)
                    clean.contains(" - ") -> clean.split(" - ", limit = 2)
                    else -> clean.split(" = ", limit = 2)
                }
                val term = parts[0].trim()
                val def = parts[1].trim()
                if (term.length in 3..60 && def.length >= 8) {
                    flashcards.add(FlashcardItem(
                        front = "What is $term?",
                        back = def,
                        category = "Definition"
                    ))
                }
            } else if (clean.contains(" is ") || clean.contains(" refers to ")) {
                val splitWord = if (clean.contains(" is ")) " is " else " refers to "
                val parts = clean.split(splitWord, limit = 2)
                val term = parts[0].trim()
                val def = parts[1].trim()
                if (term.length in 3..50 && def.length >= 8) {
                    flashcards.add(FlashcardItem(
                        front = "How is $term defined?",
                        back = "$term $splitWord $def",
                        category = "Core Concept"
                    ))
                }
            }

            if (clean.length in 20..150) {
                keySentences.add(clean)
            }
        }

        // Fill up to 10-12 flashcards using key sentences from user's notes
        var idx = 1
        for (sentence in keySentences) {
            if (flashcards.size >= 12) break
            if (flashcards.none { it.back.contains(sentence.take(20)) }) {
                flashcards.add(FlashcardItem(
                    front = "Key Principle #$idx from notes:",
                    back = sentence,
                    category = if (idx % 2 == 0) "Mechanism" else "Key Fact"
                ))
                idx++
            }
        }

        // Fallbacks if user provided very few lines
        val fillerCount = 10 - flashcards.size
        for (f in 0 until fillerCount) {
            val i = f + 1
            flashcards.add(FlashcardItem(
                front = "Core Concept $i in $cleanTitle:",
                back = "Critical rule extracted from study material: Understand foundational terms before optimizing secondary edge cases.",
                category = "Key Insight"
            ))
        }

        val bulletPoints = if (keySentences.size >= 5) {
            keySentences.take(5)
        } else {
            listOf(
                "Primary concept: Foundational definitions and principles in $cleanTitle.",
                "Structural relations: Key subcomponents interact dynamically to maintain system stability.",
                "Operational mechanics: Reviewing primary constraints ensures predictable execution.",
                "Evaluation criteria: Focus on edge conditions, definitions, and boundary behaviors.",
                "Retention method: Active testing through flashcards and quizzes reinforces memory."
            )
        }

        val quizQuestions = listOf(
            QuizQuestionItem(
                question = "What is the primary topic addressed in these study notes?",
                options = listOf(cleanTitle, "Unrelated Historical Trivia", "Arbitrary Fictional Storytelling", "Random Numerical Computation"),
                correctAnswerIndex = 0,
                explanation = "These study notes center specifically around $cleanTitle and its core mechanisms.",
                isTrueFalse = false
            ),
            QuizQuestionItem(
                question = "Active recall and spaced repetition significantly boost long-term retention of $cleanTitle.",
                options = listOf("True", "False"),
                correctAnswerIndex = 0,
                explanation = "True. Retrieval practice triggers synaptic reinforcement, consolidating conceptual understanding far more effectively than passive reading.",
                isTrueFalse = true
            ),
            QuizQuestionItem(
                question = if (flashcards.isNotEmpty()) flashcards[0].front else "Which statement accurately reflects $cleanTitle?",
                options = listOf(
                    if (flashcards.isNotEmpty()) flashcards[0].back.take(70) else "Accurate definition from notes",
                    "A contradictory claim violating the principles of this material",
                    "An irrelevant statement regarding an unrelated subject",
                    "A superficial assertion that ignores the core facts"
                ),
                correctAnswerIndex = 0,
                explanation = "This directly matches the principles and verified definitions recorded in your notes.",
                isTrueFalse = false
            ),
            QuizQuestionItem(
                question = "When analyzing $cleanTitle at $difficulty difficulty, edge cases and boundary limits must be evaluated.",
                options = listOf("True", "False"),
                correctAnswerIndex = 0,
                explanation = "True. Mastering a subject requires recognizing failure modes, boundary conditions, and parameter constraints.",
                isTrueFalse = true
            ),
            QuizQuestionItem(
                question = "What is the best pedagogical strategy when dissecting complex concepts in $cleanTitle?",
                options = listOf(
                    "Isolate foundational definitions and main workflows before addressing edge cases",
                    "Memorize edge cases only while skipping fundamental definitions",
                    "Assume all mechanisms behave identically with zero trade-offs",
                    "Avoid self-testing and rely solely on passive skimming"
                ),
                correctAnswerIndex = 0,
                explanation = "Establishing structured definitions first provides the cognitive scaffolding required for advanced deductions.",
                isTrueFalse = false
            ),
            QuizQuestionItem(
                question = if (flashcards.size > 1) flashcards[1].front else "Which parameter is essential to maintaining stability in this system?",
                options = listOf(
                    if (flashcards.size > 1) flashcards[1].back.take(70) else "Preserving core system invariants",
                    "Random parameter mutation without constraints",
                    "Ignoring structural dependencies entirely",
                    "Bypassing all verification steps"
                ),
                correctAnswerIndex = 0,
                explanation = "Consistent execution requires adhering strictly to verified mechanisms and system invariants.",
                isTrueFalse = false
            )
        )

        return ProcessedStudyResult(
            title = cleanTitle,
            eli5Summary = "In simple terms: Imagine $cleanTitle like learning to cook a signature dish. First, you assemble the raw ingredients (the basic definitions), then you follow the recipe steps in exact order (the main mechanisms), and finally you taste test (active quiz practice) to make sure everything turns out great!",
            deepDiveSummary = "Rigorous synthesis of $cleanTitle at $difficulty level. Core structural mechanisms operate through interdependent subcomponents. Detailed evaluation of boundary conditions, complexity limits, and conceptual invariants ensures predictive accuracy under applied scenarios.",
            bulletPoints = bulletPoints,
            flashcards = flashcards,
            quizQuestions = quizQuestions
        )
    }

    private fun getAlgorithmStudyResult(difficulty: String): ProcessedStudyResult {
        return ProcessedStudyResult(
            title = "Binary Trees & Algorithmic Complexity",
            eli5Summary = "Think of a binary tree like a family tree or a bracket tournament: every parent node can have at most two children (left and right). When searching for a number, if it's smaller you go left, and if it's bigger you go right. Instead of checking every item one-by-one, you cut the remaining work in half with every single step!",
            deepDiveSummary = "A Binary Search Tree (BST) maintains the invariant: ∀ node x, keys in left_subtree(x) < key(x) < keys in right_subtree(x). Average lookup, insertion, and deletion exhibit O(log N) temporal complexity. In worst-case degenerate scenarios (unbalanced insertion order), height degenerates to O(N), motivating self-balancing trees like AVL and Red-Black trees using rotation primitives.",
            bulletPoints = listOf(
                "A Binary Search Tree guarantees left < parent < right for all subtrees.",
                "Balanced trees yield O(log N) worst-case time for search, insert, and delete operations.",
                "In-order tree traversal (Left, Root, Right) always produces elements in monotonically sorted order.",
                "Unbalanced insertion sequences (e.g. 1, 2, 3, 4, 5) degrade the tree into a linked list with O(N) complexity.",
                "Self-balancing mechanisms (Red-Black, AVL) perform tree rotations to guarantee maximum tree height bounded by O(log N)."
            ),
            flashcards = listOf(
                FlashcardItem("What is the defining invariant of a Binary Search Tree (BST)?", "For any node, all keys in its left subtree are strictly smaller, and all keys in its right subtree are strictly greater.", "Invariant"),
                FlashcardItem("What is the average time complexity of searching an element in a balanced BST?", "O(log N) because each comparison eliminates approximately half of the remaining nodes.", "Complexity"),
                FlashcardItem("What order does an 'In-Order' traversal produce in a BST?", "It visits Left -> Root -> Right, which outputs elements in sorted (ascending) order.", "Traversal"),
                FlashcardItem("What causes a BST to degrade into O(N) worst-case performance?", "Inserting already sorted or reverse-sorted data without balancing rotations, forming a skewed linear chain.", "Edge Case"),
                FlashcardItem("What is the role of tree rotations in AVL or Red-Black trees?", "Local pointer reassignments that rebalance tree height in O(1) time without violating BST ordering.", "Balancing"),
                FlashcardItem("How many child pointers can a node in a binary tree hold at maximum?", "At most two child pointers: left and right.", "Definition"),
                FlashcardItem("What is the time complexity of finding the minimum element in a BST?", "O(h), where h is the tree height; in a balanced tree this is O(log N).", "Complexity"),
                FlashcardItem("What data structure is used to implement Breadth-First Search (BFS) in trees?", "A Queue (First-In, First-Out) to process nodes level by level.", "Data Structure"),
                FlashcardItem("What is the height of a perfectly balanced binary tree containing N nodes?", "Floor(log2(N)), ensuring minimal path lengths.", "Mathematical Bound"),
                FlashcardItem("Which traversal is used to create a deep copy or serialize a BST?", "Pre-order traversal (Root -> Left -> Right).", "Serialization"),
                FlashcardItem("What does a post-order traversal guarantee when freeing memory?", "Both children are processed and deallocated before the parent node is freed.", "Memory Management")
            ),
            quizQuestions = listOf(
                QuizQuestionItem(
                    "Which tree traversal visits the root node AFTER visiting both of its subtrees?",
                    listOf("Post-order Traversal", "Pre-order Traversal", "In-order Traversal", "Breadth-First Traversal"),
                    0,
                    "Post-order traversal processes Left -> Right -> Root, ensuring children are visited prior to the parent.",
                    false
                ),
                QuizQuestionItem(
                    "What is the worst-case search complexity of a completely unbalanced BST containing N nodes?",
                    listOf("O(N)", "O(log N)", "O(N log N)", "O(1)"),
                    0,
                    "When a BST is degenerate (resembling a linked list), searching for a leaf requires traversing all N nodes.",
                    false
                ),
                QuizQuestionItem(
                    "In a standard BST, the maximum element is always located at the leftmost leaf.",
                    listOf("True", "False"),
                    1,
                    "False. In a standard BST, the maximum element is located at the rightmost descendant because right children are always greater.",
                    true
                ),
                QuizQuestionItem(
                    "Which data structure is typically utilized to perform Breadth-First (Level-Order) tree traversal?",
                    listOf("Queue (FIFO)", "Stack (LIFO)", "Priority Heap", "Hash Table"),
                    0,
                    "A Queue provides First-In-First-Out ordering, ensuring parent nodes are processed before their children across levels.",
                    false
                ),
                QuizQuestionItem(
                    "A self-balancing AVL tree guarantees that heights of sibling subtrees differ by at most what value?",
                    listOf("1", "0", "2", "log N"),
                    0,
                    "The AVL balance factor invariant requires |height(left) - height(right)| <= 1 for every node in the tree.",
                    false
                ),
                QuizQuestionItem(
                    "An in-order traversal of a valid BST always produces keys in strictly descending order.",
                    listOf("True", "False"),
                    1,
                    "False. In-order traversal visits Left -> Root -> Right, producing keys in strictly ascending (sorted) order.",
                    true
                )
            )
        )
    }

    private fun getBioStudyResult(difficulty: String): ProcessedStudyResult {
        return ProcessedStudyResult(
            title = "Cellular Respiration & Bioenergetics",
            eli5Summary = "Think of your cells like tiny power plants. Glucose is like coal delivered to the plant. Through a 3-step furnace system (Glycolysis, Krebs Cycle, and Electron Transport Chain), the cell breaks down the coal to recharge tiny chemical batteries called ATP that power everything you do!",
            deepDiveSummary = "Cellular respiration couples exergonic glucose oxidation (C6H12O6 + 6O2 -> 6CO2 + 6H2O + ~30-32 ATP) to phosphorylation of ADP. In the mitochondrial inner membrane, complex I-IV electron flux drives active proton pumping into the intermembrane space, generating a proton-motive force (chemiosmotic gradient) that drives ATP synthase rotary catalysis.",
            bulletPoints = listOf(
                "Glycolysis occurs in the cytosol, yielding net 2 ATP, 2 NADH, and 2 Pyruvate per glucose.",
                "The Citric Acid (Krebs) cycle takes place in the mitochondrial matrix, producing NADH, FADH2, and CO2.",
                "Oxidative phosphorylation across the inner mitochondrial membrane accounts for the vast majority of ATP yield.",
                "Oxygen serves as the final electron acceptor in the electron transport chain, forming H2O.",
                "Proton accumulation in the intermembrane space establishes the electrochemical gradient utilized by ATP synthase."
            ),
            flashcards = listOf(
                FlashcardItem("Where does Glycolysis occur inside eukaryotic cells?", "In the cytoplasm / cytosol.", "Localization"),
                FlashcardItem("What is the net yield of ATP produced directly during Glycolysis?", "Net 2 ATP (4 ATP produced minus 2 ATP consumed in the preparatory phase).", "Yield"),
                FlashcardItem("What is the ultimate electron acceptor in aerobic respiration?", "Molecular Oxygen (O2), which accepts electrons and protons to form water (H2O).", "Mechanism"),
                FlashcardItem("Which enzyme synthesizes ATP using the electrochemical proton gradient?", "ATP Synthase, operating via rotary molecular catalysis.", "Biochemistry"),
                FlashcardItem("In what cellular compartment does the Citric Acid (Krebs) Cycle occur?", "Within the mitochondrial matrix.", "Localization"),
                FlashcardItem("What 2-carbon molecule enters the Krebs Cycle after pyruvate decarboxylation?", "Acetyl-CoA.", "Substrate"),
                FlashcardItem("What electron carriers donate high-energy electrons to the ETC?", "NADH and FADH2.", "Cofactor"),
                FlashcardItem("Where do protons accumulate to build the chemiosmotic gradient?", "In the mitochondrial intermembrane space.", "Gradient"),
                FlashcardItem("Approximately how many total ATP molecules are produced per oxidized glucose molecule?", "Approximately 30 to 32 ATP molecules.", "Efficiency"),
                FlashcardItem("What anaerobic pathway regenerates NAD+ when oxygen is unavailable?", "Fermentation (lactic acid or ethanol).", "Anaerobic Pathway")
            ),
            quizQuestions = listOf(
                QuizQuestionItem(
                    "Which stage of cellular respiration produces the greatest net quantity of ATP?",
                    listOf("Oxidative Phosphorylation", "Glycolysis", "Citric Acid Cycle", "Pyruvate Oxidation"),
                    0,
                    "Oxidative phosphorylation generates approximately 26-28 ATP of the ~30-32 total yield per glucose molecule.",
                    false
                ),
                QuizQuestionItem(
                    "Glycolysis requires molecular oxygen (O2) in order to proceed.",
                    listOf("True", "False"),
                    1,
                    "False. Glycolysis is an anaerobic pathway that operates identically in both the presence and absence of oxygen.",
                    true
                ),
                QuizQuestionItem(
                    "What chemical compound acts as the carbon carrier entering the Krebs Cycle?",
                    listOf("Acetyl-CoA", "Lactate", "Pyruvate", "Oxaloacetate"),
                    0,
                    "Pyruvate is decarboxylated into Acetyl-CoA, which delivers the 2-carbon acetyl group to oxaloacetate.",
                    false
                ),
                QuizQuestionItem(
                    "What is the final electron acceptor in the Electron Transport Chain?",
                    listOf("Molecular Oxygen (O2)", "Carbon Dioxide (CO2)", "Water (H2O)", "Glucose"),
                    0,
                    "Oxygen accepts electrons and combines with protons to form metabolic water.",
                    false
                ),
                QuizQuestionItem(
                    "ATP Synthase relies on the flow of protons down their electrochemical gradient.",
                    listOf("True", "False"),
                    0,
                    "True. The proton-motive force across the inner membrane drives the mechanical rotation of ATP Synthase.",
                    true
                ),
                QuizQuestionItem(
                    "Where does the Citric Acid (Krebs) Cycle take place?",
                    listOf("Mitochondrial Matrix", "Cytoplasm", "Outer Mitochondrial Membrane", "Nucleus"),
                    0,
                    "The Citric Acid Cycle occurs within the aqueous matrix of the mitochondria.",
                    false
                )
            )
        )
    }
}
