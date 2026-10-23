package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.streams.NativeStreamBadgeDetector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.screens.stream.StreamQuickFilter
import com.nuvio.tv.ui.theme.NuvioTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class QuickFilterOption(
    val filter: StreamQuickFilter,
    val icon: String,
    val labelRes: Int,
    val count: Int
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuickFilterChips(
    allStreams: List<Stream>,
    selectedFilter: StreamQuickFilter,
    onFilterSelected: (StreamQuickFilter) -> Unit,
    externalFocusRequesters: List<FocusRequester>? = null,
    onUpKey: (() -> Unit)? = null,
    onDownKey: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var filterCounts by remember {
        mutableStateOf(List(4) { 0 })
    }

    LaunchedEffect(allStreams) {
        if (allStreams.isEmpty()) {
            filterCounts = List(4) { 0 }
            return@LaunchedEffect
        }
        // Coalesce the burst of list updates that arrives while probes land:
        // recomputing on every badge change caused the chips to flicker.
        delay(250)
        val counts = withContext(Dispatchers.Default) {
            listOf(
                allStreams.count { stream ->
                    stream.badges.any { it.name.contains("LATINO (Verificado)") }
                },
                allStreams.count { NativeStreamBadgeDetector.isSpanishOrLatino(it) },
                allStreams.count { NativeStreamBadgeDetector.isRemux(it) },
                allStreams.count { NativeStreamBadgeDetector.is4K(it) }
            )
        }
        filterCounts = counts
    }

    val options = remember(allStreams.size, filterCounts) {
        listOf(
            QuickFilterOption(StreamQuickFilter.ALL, "", R.string.stream_filter_all, allStreams.size),
            QuickFilterOption(
                StreamQuickFilter.LATINO_VERIFIED,
                "🇲🇽 ",
                R.string.stream_filter_latino_verified,
                filterCounts.getOrElse(0) { 0 }
            ),
            QuickFilterOption(
                StreamQuickFilter.SPANISH,
                "🌎 ",
                R.string.stream_filter_spanish,
                filterCounts.getOrElse(1) { 0 }
            ),
            QuickFilterOption(
                StreamQuickFilter.REMUX,
                "💎 ",
                R.string.stream_filter_remux,
                filterCounts.getOrElse(2) { 0 }
            ),
            QuickFilterOption(
                StreamQuickFilter.UHD_4K,
                "📺 ",
                R.string.stream_filter_4k,
                filterCounts.getOrElse(3) { 0 }
            )
        )
    }

    val internalRequesters = remember { List(5) { FocusRequester() } }
    val focusRequesters = externalFocusRequesters ?: internalRequesters

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
        contentPadding = PaddingValues(horizontal = NuvioTheme.spacing.sm, vertical = NuvioTheme.spacing.xxs),
        modifier = modifier
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (onUpKey != null) {
                            onUpKey()
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (onDownKey != null) {
                            onDownKey()
                            true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        itemsIndexed(options) { index, option ->
            val isSelected = selectedFilter == option.filter
            val labelText = stringResource(option.labelRes)
            val displayText = "${option.icon}$labelText (${option.count})"

            var isFocused by remember { mutableStateOf(false) }
            val textColor = when {
                isFocused || isSelected -> NuvioTheme.colors.OnSecondary
                option.count == 0 -> NuvioTheme.colors.TextSecondary.copy(alpha = 0.5f)
                else -> NuvioTheme.colors.TextSecondary
            }

            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(option.filter) },
                modifier = Modifier
                    .focusRequester(focusRequesters.getOrElse(index) { FocusRequester() })
                    .onFocusChanged { isFocused = it.isFocused },
                colors = FilterChipDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    focusedContainerColor = NuvioTheme.colors.Secondary,
                    selectedContainerColor = NuvioTheme.colors.Secondary,
                    focusedSelectedContainerColor = NuvioTheme.colors.Secondary,
                    contentColor = textColor,
                    focusedContentColor = textColor,
                    selectedContentColor = textColor,
                    focusedSelectedContentColor = textColor
                ),
                border = FilterChipDefaults.border(
                    border = Border(
                        border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                        shape = RoundedCornerShape(16.dp)
                    ),
                    focusedBorder = Border(
                        border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                        shape = RoundedCornerShape(16.dp)
                    ),
                    selectedBorder = Border(
                        border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Primary),
                        shape = RoundedCornerShape(16.dp)
                    ),
                    focusedSelectedBorder = Border(
                        border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                        shape = RoundedCornerShape(16.dp)
                    )
                ),
                shape = FilterChipDefaults.shape(shape = RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
        }
    }
}
