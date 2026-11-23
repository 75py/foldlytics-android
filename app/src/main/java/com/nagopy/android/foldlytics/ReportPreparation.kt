package com.nagopy.android.foldlytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Prepares one shared report at a time in a scope that outlives the activity, such as the view
 * model's. A request while one is preparing or waiting to be shown is ignored, and a prepared
 * report stays in [prepared] until [consume], so an activity recreated meanwhile still shows it.
 */
internal class ReportPreparation(private val scope: CoroutineScope) {
    private val _preparing = MutableStateFlow(false)
    private val _prepared = MutableStateFlow<String?>(null)

    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()
    val prepared: StateFlow<String?> = _prepared.asStateFlow()

    /** Starts [prepare] unless a report is in progress; a null result shares nothing. */
    fun start(prepare: suspend () -> String?): Boolean {
        if (_preparing.value || _prepared.value != null) return false
        _preparing.value = true
        scope.launch {
            try {
                _prepared.value = prepare()
            } finally {
                _preparing.value = false
            }
        }
        return true
    }

    /** Marks [report] as handed to the share sheet. */
    fun consume(report: String) {
        _prepared.compareAndSet(report, null)
    }
}
