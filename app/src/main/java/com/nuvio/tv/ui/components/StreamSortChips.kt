package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.nuvio.tv.ui.screens.stream.StreamSortMode
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Compact sort selector for the stream list. Applies on click/OK rather than on
 * focus so arrow-keying across the row does not reorder the list underneath the
 * user.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamSortChips(
    selectedMode: StreamSortMode,
    onModeSelected: (StreamSortMode) -> Unit,
    focusRequesters: List<FocusRequester>,
    onUpKey: (() -> Unit)? = null,
    onDownKey: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.DirectionUp -> {
                    onUpKey?.invoke()
                    onUpKey != null
                }
                Key.DirectionDown -> {
                    onDownKey?.invoke()
                    onDownKey != null
                }
                else -> false
            }
        }
    ) {
        Text(
            text = stringResource(R.string.stream_sort_label),
            style = MaterialTheme.typography.labelMedium,
            color = NuvioTheme.colors.TextSecondary
        )
        Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))

        val modes = remember { StreamSortMode.values().toList() }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            contentPadding = PaddingValues(
                horizontal = NuvioTheme.spacing.sm,
                vertical = NuvioTheme.spacing.xxs
            ),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(modes) { index, mode ->
                val isSelected = selectedMode == mode
                var isFocused by remember { mutableStateOf(false) }
                val textColor = when {
                    isFocused || isSelected -> NuvioTheme.colors.OnSecondary
                    else -> NuvioTheme.colors.TextSecondary
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { onModeSelected(mode) },
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
                        text = stringResource(mode.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun StreamSortMode.labelRes(): Int = when (this) {
    StreamSortMode.MEJOR_CON_LATINO -> R.string.stream_sort_best_latino
    StreamSortMode.CALIDAD -> R.string.stream_sort_quality
    StreamSortMode.TAMANO -> R.string.stream_sort_size
}
