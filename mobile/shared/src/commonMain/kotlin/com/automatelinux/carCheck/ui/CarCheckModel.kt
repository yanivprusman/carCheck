package com.automatelinux.carCheck.ui

import com.automatelinux.carCheck.data.LookupResult
import com.automatelinux.carCheck.data.Plate
import com.automatelinux.carCheck.data.RecentEntry
import com.automatelinux.carCheck.data.RecentStore
import com.automatelinux.carCheck.data.VehicleLookup
import com.automatelinux.carCheck.data.VehicleReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Things only the host platform can do; commonMain asks, Android answers with an Intent. */
interface HostActions {
    fun share(text: String)
    fun openUrl(url: String)
    fun dial(phone: String)
    fun copy(label: String, text: String)
}

/** Screen state and the actions on it. Platform-free; Android wraps it in a ViewModel so it survives rotation. */
class CarCheckModel(
    private val scope: CoroutineScope,
    private val recents: RecentStore,
    private val lookup: VehicleLookup = VehicleLookup(),
) {
    sealed class Notice {
        data object TooShort : Notice()
        data object NotFound : Notice()
        data object Refreshing : Notice()
        data object Offline : Notice()
        data class Failed(val detail: String) : Notice()
    }

    data class State(
        val input: String = "",
        val busy: Boolean = false,
        val notice: Notice? = null,
        val report: VehicleReport? = null,
        val recents: List<RecentEntry> = emptyList(),
    )

    private val _state = MutableStateFlow(State(recents = recents.load()))
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun setInput(digits: String) {
        _state.update { it.copy(input = digits.filter { c -> c.isDigit() }.take(Plate.MAX_DIGITS), notice = null) }
    }

    fun search() = search(_state.value.input)

    fun search(digits: String) {
        val plate = Plate.parse(digits)
        if (plate == null) {
            _state.update { it.copy(notice = Notice.TooShort) }
            return
        }
        job?.cancel()
        _state.update { it.copy(input = plate.digits, busy = true, notice = null) }
        job = scope.launch {
            val result = lookup.lookup(plate)
            _state.update { s ->
                when (result) {
                    is LookupResult.Found -> {
                        val r = result.report
                        val entry = RecentEntry(
                            digits = plate.digits,
                            title = r.title,
                            subtitle = r.subtitle,
                            color = r.color,
                            at = Clock.System.now().toEpochMilliseconds(),
                        )
                        s.copy(busy = false, report = r, recents = recents.remember(entry))
                    }
                    LookupResult.NotFound -> s.copy(busy = false, notice = Notice.NotFound)
                    LookupResult.RegistryRefreshing -> s.copy(busy = false, notice = Notice.Refreshing)
                    LookupResult.Offline -> s.copy(busy = false, notice = Notice.Offline)
                    is LookupResult.Failed -> s.copy(busy = false, notice = Notice.Failed(result.detail))
                }
            }
        }
    }

    fun back() {
        _state.update { it.copy(report = null) }
    }

    fun removeRecent(digits: String) {
        _state.update { it.copy(recents = recents.remove(digits)) }
    }

    fun clearRecents() {
        _state.update { it.copy(recents = recents.clear()) }
    }
}
