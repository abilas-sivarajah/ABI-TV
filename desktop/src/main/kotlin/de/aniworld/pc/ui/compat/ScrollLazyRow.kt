package de.aniworld.pc.ui.compat

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.aniworld.pc.ui.theme.AccentCyan

/**
 * LazyRow mit Bildlaufleiste: Auf dem TV scrollt die Reihe mit dem Fokus der Fernbedienung,
 * am PC käme man mit der Maus sonst nicht an die Karten jenseits des Fensterrands.
 */
@Composable
fun ScrollLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: LazyListScope.() -> Unit
) {
    val overflows by remember(state) { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    Column(modifier) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content
        )
        if (overflows) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(state),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                        end = contentPadding.calculateEndPadding(LayoutDirection.Ltr)
                    ),
                style = defaultScrollbarStyle().copy(
                    thickness = 6.dp,
                    shape = RoundedCornerShape(3.dp),
                    unhoverColor = Color.White.copy(alpha = 0.18f),
                    hoverColor = AccentCyan.copy(alpha = 0.7f)
                )
            )
        }
    }
}
