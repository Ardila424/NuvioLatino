package com.nuvio.tv.core.streams

/**
 * Where an audio-track verdict came from. Only [isHeaderVerified] sources may be
 * presented as "Verificado en el archivo"; name heuristics must be labelled
 * differently in the UI.
 */
enum class ProbeProvenance {
    MKV_EBML,
    MP4_MOOV,
    HLS_PLAYLIST,
    MEDIA_EXTRACTOR,
    NAME_HEURISTIC;

    val isHeaderVerified: Boolean
        get() = this == MKV_EBML || this == MP4_MOOV || this == HLS_PLAYLIST
}
