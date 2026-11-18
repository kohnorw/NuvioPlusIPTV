package com.nuvio.tv.livetv.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.livetv.data.LiveTvPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repo: CalendarRepository,
    livePrefs: LiveTvPreferences
) : ViewModel() {

    val state: StateFlow<CalendarState> = repo.state

    /** The clock as set for Live TV (12 or 24 hours). */
    val use24h: StateFlow<Boolean> = livePrefs.settings.map { it.use24HourClock }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _now = MutableStateFlow(System.currentTimeMillis())
    /** Now, to the minute (what's on now, "In 3 h"). */
    val now: StateFlow<Long> = _now.asStateFlow()

    init {
        repo.start()
        viewModelScope.launch {
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L)
                _now.value = System.currentTimeMillis()
            }
        }
    }

    fun refresh() = repo.refresh(force = true)
}
