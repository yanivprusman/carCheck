package com.automatelinux.carCheck.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.automatelinux.carCheck.data.LookupResult
import com.automatelinux.carCheck.data.OcrLine
import com.automatelinux.carCheck.data.Plate
import com.automatelinux.carCheck.data.PlateCandidate
import com.automatelinux.carCheck.data.PlateOcr
import com.automatelinux.carCheck.data.RecentEntry
import com.automatelinux.carCheck.data.RecentStore
import com.automatelinux.carCheck.data.VehicleLookup
import com.automatelinux.carCheck.data.VehicleReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Where a picture of the car comes from. */
enum class ImageSource { Camera, Gallery }

/** Things only the host platform can do; commonMain asks, Android answers with an Intent. */
interface HostActions {
    /** Send [image] (named after [name]) through the share sheet, with [caption] for apps that take one. */
    fun shareImage(image: ImageBitmap, name: String, caption: String)
    fun openUrl(url: String)
    fun dial(phone: String)
    fun copy(label: String, text: String)

    /** Get a picture from [source]; the host then feeds its OCR into [CarCheckModel.readPlates]. */
    fun scanPlate(source: ImageSource)
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
        data object NoPlateInImage : Notice()
        data class ImageFailed(val detail: String) : Notice()
    }

    data class State(
        val input: String = "",
        val busy: Boolean = false,
        /** A picture is being read for a plate. */
        val reading: Boolean = false,
        val notice: Notice? = null,
        val report: VehicleReport? = null,
        val recents: List<RecentEntry> = emptyList(),
        /** Plates the last picture held, largest first; shown so the user can pick another. */
        val imagePlates: List<PlateCandidate> = emptyList(),
    )

    private val _state = MutableStateFlow(State(recents = recents.load()))
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null
    private var readJob: Job? = null

    fun setInput(digits: String) {
        _state.update { it.copy(input = digits.filter { c -> c.isDigit() }.take(Plate.MAX_DIGITS), notice = null, imagePlates = emptyList()) }
    }

    /**
     * Read the plate off a picture. [read] runs the platform's OCR and returns what it saw;
     * the decision is made here: one plate is looked up at once, a clearly nearer plate among
     * several is looked up with the rest offered, and plates of similar size are offered only.
     */
    fun readPlates(read: suspend () -> List<OcrLine>) {
        readJob?.cancel()
        job?.cancel()
        _state.update { it.copy(reading = true, busy = false, notice = null, imagePlates = emptyList()) }
        readJob = scope.launch {
            val lines = try {
                read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(reading = false, notice = Notice.ImageFailed(e.message ?: (e::class.simpleName ?: "unknown"))) }
                return@launch
            }
            val found = PlateOcr.candidates(lines)
            when {
                found.isEmpty() -> _state.update { it.copy(reading = false, notice = Notice.NoPlateInImage) }
                found.size == 1 -> {
                    _state.update { it.copy(reading = false) }
                    search(found[0].digits)
                }
                else -> {
                    val nearest = found[0]
                    val dominant = nearest.size >= found[1].size * DOMINANT_RATIO
                    _state.update { it.copy(reading = false, input = nearest.digits, imagePlates = found) }
                    if (dominant) search(nearest.digits)
                }
            }
        }
    }

    fun search() = search(_state.value.input)

    fun search(digits: String) {
        val plate = Plate.parse(digits)
        if (plate == null) {
            _state.update { it.copy(notice = Notice.TooShort) }
            return
        }
        job?.cancel()
        // A lookup outranks a picture still being read — the user typed over it.
        readJob?.cancel()
        _state.update { it.copy(input = plate.digits, busy = true, reading = false, notice = null) }
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

    private companion object {
        /** A plate this much taller than the next one in the picture is the car the picture is of. */
        const val DOMINANT_RATIO = 1.5f
    }
}
