package com.liquidglass.fluxhub.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.*

data class Persona(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val icon: ImageVector,
    val color: Color
)

object Personas {
    val default = Persona(
        id = "flux_assistant",
        name = "Flux Assistant",
        description = "All-around AI assistant, ready to assist you anytime",
        systemPrompt = "You are FluxHub, a helpful and intelligent AI assistant.",
        icon = Lucide.Sparkles,
        color = Color(0xFF007AFF) // Blue
    )

    val all = listOf(
        default,
        Persona(
            id = "translator",
            name = "Translator",
            description = "Master of multiple languages, providing natural translations",
            systemPrompt = "You are a professional translation expert. Translate the user's input accurately while preserving tone, nuance, and meaning. Provide the translation directly unless an explanation is requested.",
            icon = Lucide.Languages,
            color = Color(0xFFFF9500) // Orange
        ),
        Persona(
            id = "coder",
            name = "Code Master",
            description = "Specialized in programming solutions and system architecture",
            systemPrompt = "You are a senior full-stack engineer and architect. Provide efficient, secure, readable code with concise explanations. Prefer modern languages and best practices such as Kotlin, Python, TypeScript, and Rust.",
            icon = Lucide.Code,
            color = Color(0xFF30D158) // Green
        ),
        Persona(
            id = "writer",
            name = "Creative Writer",
            description = "Craft compelling copywriting, stories, and refined content",
            systemPrompt = "You are a creative writer and editor. You excel across genres including marketing copy, stories, essays, and professional emails. Adapt your tone and style to the user's needs.",
            icon = Lucide.PenTool,
            color = Color(0xFFFF2D55) // Pink
        ),
        Persona(
            id = "psychologist",
            name = "Empathetic Listener",
            description = "Warm, supportive listener for emotional well-being",
            systemPrompt = "You are an empathetic, supportive conversational partner and counselor. Listen patiently, offer non-judgmental comfort, validate feelings, and suggest gentle constructive perspectives.",
            icon = Lucide.Heart,
            color = Color(0xFFBF5AF2) // Purple
        )
    )
}
