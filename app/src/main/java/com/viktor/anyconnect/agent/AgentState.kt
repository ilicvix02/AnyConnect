package com.viktor.anyconnect.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared, observable state between the accessibility service and the UI. */
object AgentState {
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun log(line: String) {
        _log.value = (_log.value + line).takeLast(300)
    }

    fun clearLog() { _log.value = emptyList() }
    fun setRunning(b: Boolean) { _running.value = b }
    fun setConnected(b: Boolean) { _connected.value = b }
}
