package com.example.util

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast

object PhoneAssistantHelper {
    /**
     * Launches the user's default on-device AI voice assistant (Google Assistant, Gemini, etc.)
     */
    fun launchPhoneAssistant(context: Context) {
        val intents = listOf(
            // Primary intent for voice assistant / voice command
            Intent(Intent.ACTION_VOICE_COMMAND).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // System assist intent (launches default digital assistant app)
            Intent(Intent.ACTION_ASSIST).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Fallback hands-free voice search
            Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(RecognizerIntent.EXTRA_SECURE, true)
            },
            // Standard web voice search
            Intent(RecognizerIntent.ACTION_WEB_SEARCH).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        var launched = false
        for (intent in intents) {
            try {
                context.startActivity(intent)
                launched = true
                break
            } catch (e: Exception) {
                // Try next assistant intent
            }
        }

        if (!launched) {
            try {
                // Fallback attempt: Google app or search activity
                val searchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(searchIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open phone AI assistant", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
