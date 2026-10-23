package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus

data class StreamScreenUiState(
    val isLoading: Boolean = true,
    val isDirectAutoPlayFlow: Boolean = false,
    val showDirectAutoPlayOverlay: Boolean = false,
    val autoPlayDecided: Boolean = false,
    val externalPlayerOverlayVisible: Boolean = false,
    val directAutoPlayMessage: String? = null,
    val directAutoPlayProgress: Float? = null,
    val videoId: String = "",
    val contentType: String = "",
    val title: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    // Episode-specific fields
    val season: Int? = null,
    val episode: Int? = null,
    val episodeName: String? = null,
    val runtime: Int? = null,
    // Movie-specific fields
    val genres: String? = null,
    val year: String? = null,
    val addonStreams: List<AddonStreams> = emptyList(),
    val allStreams: List<Stream> = emptyList(),
    val selectedAddonFilter: String? = null, // null means "All"
    val selectedQuickFilter: StreamQuickFilter = StreamQuickFilter.ALL,
    val selectedSortMode: StreamSortMode = StreamSortMode.MEJOR_CON_LATINO,
    val filteredStreams: List<Stream> = emptyList(),
    val availableAddons: List<String> = emptyList(),
    val sourceChips: List<SourceChipItem> = emptyList(),
    val autoPlayStream: Stream? = null,
    val autoPlayPlaybackInfo: StreamPlaybackInfo? = null,
    val error: String? = null,
    val playbackErrorMessage: String? = null
) {
    val isEpisode: Boolean get() = season != null && episode != null
}

enum class StreamQuickFilter {
    ALL,

    /** Only streams whose header was probed and confirmed to contain Latino audio. */
    LATINO_VERIFIED,

    /** Verified or name-heuristic Spanish (Latino + Castellano). */
    SPANISH,
    REMUX,
    UHD_4K
}

enum class StreamSortMode {
    /** Verified Latino → verified Castellano → likely Spanish → unknown → no Spanish. */
    MEJOR_CON_LATINO,

    /** Quality tier → resolution → HDR → size. */
    CALIDAD,

    /** Largest file first. */
    TAMANO
}

sealed class StreamScreenEvent {
    data class OnAddonFilterSelected(val addonName: String?) : StreamScreenEvent()
    data class OnQuickFilterSelected(val filter: StreamQuickFilter) : StreamScreenEvent()
    data class OnSortModeSelected(val mode: StreamSortMode) : StreamScreenEvent()
    data class OnStreamSelected(val stream: Stream) : StreamScreenEvent()
    data class OnStreamFocused(val stream: Stream) : StreamScreenEvent()
    data object OnAutoPlayConsumed : StreamScreenEvent()
    data object OnRefresh : StreamScreenEvent()
    data object OnRetry : StreamScreenEvent()
    data object OnBackPress : StreamScreenEvent()
    data object OnResume : StreamScreenEvent()
}

data class SharedStream(
    val hostName: String,
    val stream: Stream
)
