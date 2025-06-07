package com.example.capstone_2

interface WebSocketMessageListener {
    fun onWebSocketMessage()
}

interface SpeechController {
    fun startListening()

    fun setSTT()
}