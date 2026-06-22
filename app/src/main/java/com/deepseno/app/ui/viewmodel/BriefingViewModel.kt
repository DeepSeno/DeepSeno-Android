package com.enmooy.deepseno.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmooy.deepseno.data.remote.model.DailySummary
import com.enmooy.deepseno.data.remote.model.ExtractedItem
import com.enmooy.deepseno.data.remote.model.StatusUpdate
import com.enmooy.deepseno.data.remote.model.WeeklySummary
import com.enmooy.deepseno.service.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class BriefingMode { Daily, Weekly, Monthly }

@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val apiClient: ApiClient,
) : ViewModel() {

    private val _mode = MutableStateFlow(BriefingMode.Daily)
    val mode: StateFlow<BriefingMode> = _mode

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _dailySummary = MutableStateFlow<DailySummary?>(null)
    val dailySummary: StateFlow<DailySummary?> = _dailySummary

    private val _weeklySummary = MutableStateFlow<WeeklySummary?>(null)
    val weeklySummary: StateFlow<WeeklySummary?> = _weeklySummary

    // Monthly summary reuses the weekly response shape (start_date/end_date/summary_json).
    private val _monthlySummary = MutableStateFlow<WeeklySummary?>(null)
    val monthlySummary: StateFlow<WeeklySummary?> = _monthlySummary

    private val _todos = MutableStateFlow<List<ExtractedItem>>(emptyList())
    val todos: StateFlow<List<ExtractedItem>> = _todos

    private val _items = MutableStateFlow<List<ExtractedItem>>(emptyList())
    val items: StateFlow<List<ExtractedItem>> = _items

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRegenerating = MutableStateFlow(false)
    val isRegenerating: StateFlow<Boolean> = _isRegenerating

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    val dateString: String
        get() = _selectedDate.value.format(isoFormatter)

    val displayDate: String
        get() = _selectedDate.value.format(displayFormatter)

    val weekStartDate: LocalDate
        get() = _selectedDate.value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val weekDisplayRange: String
        get() {
            val start = weekStartDate
            val end = start.plusDays(6)
            val startStr = start.format(DateTimeFormatter.ofPattern("MMM d"))
            val endStr = end.format(displayFormatter)
            return "$startStr - $endStr"
        }

    val monthStartDate: LocalDate
        get() = _selectedDate.value.withDayOfMonth(1)

    val monthDisplayRange: String
        get() = _selectedDate.value.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    /** Jump to an arbitrary date picked from the calendar, then reload. */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        loadData()
    }

    fun setMode(newMode: BriefingMode) {
        _mode.value = newMode
        loadData()
    }

    fun previousDate() {
        when (_mode.value) {
            BriefingMode.Daily -> _selectedDate.value = _selectedDate.value.minusDays(1)
            BriefingMode.Weekly -> _selectedDate.value = _selectedDate.value.minusWeeks(1)
            BriefingMode.Monthly -> _selectedDate.value = _selectedDate.value.minusMonths(1)
        }
        loadData()
    }

    fun nextDate() {
        when (_mode.value) {
            BriefingMode.Daily -> _selectedDate.value = _selectedDate.value.plusDays(1)
            BriefingMode.Weekly -> _selectedDate.value = _selectedDate.value.plusWeeks(1)
            BriefingMode.Monthly -> _selectedDate.value = _selectedDate.value.plusMonths(1)
        }
        loadData()
    }

    fun loadData() {
        when (_mode.value) {
            BriefingMode.Daily -> loadDaily()
            BriefingMode.Weekly -> loadWeekly()
            BriefingMode.Monthly -> loadMonthly()
        }
    }

    private fun loadDaily() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val api = apiClient.api ?: return@launch

                // Try getBriefing first (combined endpoint)
                try {
                    val briefing = api.getBriefing(dateString)
                    _dailySummary.value = briefing.summary
                    _todos.value = briefing.todos
                    _items.value = briefing.items
                    return@launch
                } catch (_: Exception) {
                    // Fallback to individual endpoints
                }

                // Fallback: fetch individually
                _dailySummary.value = try {
                    api.getDailySummary(dateString)
                } catch (_: Exception) {
                    null
                }

                val allItems = try {
                    api.getExtractedItems()
                } catch (_: Exception) {
                    emptyList()
                }

                _todos.value = allItems.filter { it.isTodo }
                _items.value = allItems.filter { !it.isTodo }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadWeekly() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val api = apiClient.api ?: return@launch
                val startDate = weekStartDate.format(isoFormatter)
                _weeklySummary.value = api.getWeeklySummary(startDate)
            } catch (e: Exception) {
                _weeklySummary.value = null
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadMonthly() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val api = apiClient.api ?: return@launch
                val startDate = monthStartDate.format(isoFormatter)
                _monthlySummary.value = api.getMonthlySummary(startDate)
            } catch (e: Exception) {
                _monthlySummary.value = null
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Trigger server-side regeneration of the current day/week briefing and
     * reload. Server is slow (LLM call), so we expose isRegenerating so the
     * UI can show a spinner and disable the button.
     */
    fun regenerate() {
        if (_isRegenerating.value) return
        viewModelScope.launch {
            _isRegenerating.value = true
            _errorMessage.value = null
            try {
                val api = apiClient.api ?: return@launch
                val isWeekly = _mode.value == BriefingMode.Weekly
                val modeStr = if (isWeekly) "weekly" else "daily"
                val dateForServer = if (isWeekly) weekStartDate.format(isoFormatter) else dateString
                api.regenerateBriefing(mode = modeStr, date = dateForServer)
                // Reload to pick up the freshly-written summary
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isRegenerating.value = false
            }
        }
    }

    fun toggleTodo(id: Int, currentStatus: String) {
        val newStatus = if (currentStatus == "completed") "active" else "completed"

        // Optimistic local update — no full page reload
        _todos.value = _todos.value.map { if (it.id == id) it.copy(status = newStatus) else it }

        viewModelScope.launch {
            try {
                val api = apiClient.api ?: return@launch
                api.updateItemStatus(id, StatusUpdate(status = newStatus))
            } catch (e: Exception) {
                // Revert on failure
                _todos.value = _todos.value.map { if (it.id == id) it.copy(status = currentStatus) else it }
                _errorMessage.value = e.message
            }
        }
    }
}
