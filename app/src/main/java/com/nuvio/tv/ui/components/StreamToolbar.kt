package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

data class StreamOption(
    val id: String?,
    val label: String,
    val detail: String? = null
)

/**
 * Compact glass pill used by the two-row stream toolbar. The value is the
 * current selection; clicking opens a focus-safe TV dialog with the options.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamToolbarChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showChevron: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .height(34.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = NuvioTheme.colors.Secondary,
            pressedContainerColor = Color.White.copy(alpha = 0.12f)
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                shape = shape
            ),
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = shape
            )
        ),
        shape = CardDefaults.shape(shape = shape),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) NuvioTheme.colors.OnSecondary
                    else NuvioTheme.colors.TextTertiary,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                modifier = Modifier.widthIn(max = 190.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) NuvioTheme.colors.OnSecondary
                else NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showChevron) {
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) NuvioTheme.colors.OnSecondary
                    else NuvioTheme.colors.TextTertiary
                )
            }
        }
    }
}

/**
 * Glass modal used by the toolbar. Keeps the stream header at two rows instead
 * of a third row of chips for addons/sort.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamOptionDialog(
    title: String,
    options: List<StreamOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocus = remember { FocusRequester() }
    val selectedIndex = remember(options, selectedId) {
        options.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(options.size) {
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .width(440.dp)
                .clip(shape)
                .background(Color(0xFF141416).copy(alpha = 0.97f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                .padding(NuvioTheme.spacing.lg)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextTertiary
                )
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xxs)
                ) {
                    itemsIndexed(options) { index, option ->
                        var focused by remember { mutableStateOf(false) }
                        val isSelected = option.id == selectedId
                        Card(
                            onClick = { onSelect(option.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .then(
                                    if (index == selectedIndex) Modifier.focusRequester(initialFocus)
                                    else Modifier
                                )
                                .onFocusChanged { focused = it.isFocused },
                            colors = CardDefaults.colors(
                                containerColor = if (isSelected) Color.White.copy(alpha = 0.10f)
                                else Color.Transparent,
                                focusedContainerColor = NuvioTheme.colors.Secondary
                            ),
                            shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            scale = CardDefaults.scale(focusedScale = 1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = NuvioTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (focused) NuvioTheme.colors.OnSecondary
                                    else NuvioTheme.colors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (option.detail != null) {
                                    Text(
                                        text = option.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (focused) NuvioTheme.colors.OnSecondary
                                        else NuvioTheme.colors.TextTertiary
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = " ✓",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (focused) NuvioTheme.colors.OnSecondary
                                        else NuvioTheme.colors.Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
