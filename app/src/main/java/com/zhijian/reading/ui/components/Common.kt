package com.zhijian.reading.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The small label above a group of fields. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = Type.overline,
        color = palette.textTertiary,
        modifier = modifier,
    )
}

/** The rule between two groups. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.borderSubtle),
    )
}

/** A format's badge: small, in the format's own colour. */
@Composable
fun FormatBadge(format: String, modifier: Modifier = Modifier) {
    val fg = palette.format(format)
    Text(
        text = format.uppercase(),
        color = fg,
        style = Type.numeric.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
        modifier = modifier
            .background(fg.copy(alpha = 0.12f), RoundedCornerShape(Radius.sm.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A pill, as the filter row and the tag clouds use. */
@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    val base = modifier
        .height(30.dp)
        .background(
            if (selected) pal.accent else pal.surface,
            RoundedCornerShape(Radius.pill.dp),
        )
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 13.dp)

    Row(base, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = Type.smallStrong,
            color = if (selected) pal.textOnAccent else pal.textSecondary,
            maxLines = 1,
        )
    }
}

/** A larger pill for a book's tags in the detail panel. */
@Composable
fun TagPill(text: String, onClick: (() -> Unit)? = null, selected: Boolean = false) {
    val pal = palette
    Row(
        Modifier
            .height(28.dp)
            .background(
                if (selected) pal.accentSubtle else pal.bgSidebar,
                RoundedCornerShape(Radius.pill.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = Type.small,
            color = if (selected) pal.textAccent else pal.textSecondary,
            maxLines = 1,
        )
    }
}

/** Five stars, filled to the rating. */
@Composable
fun Stars(
    rating: Int,
    starSize: Dp = 13.dp,
    gap: Dp = 1.dp,
    modifier: Modifier = Modifier,
    onRate: ((Int) -> Unit)? = null,
) {
    val filled = Color(0xFFE8A33D)
    val empty = palette.textTertiary.copy(alpha = 0.45f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            Canvas(
                Modifier
                    .size(starSize)
                    .then(if (onRate != null) Modifier.clickable { onRate(i + 1) } else Modifier),
            ) {
                val path = starPath(size.minDimension / 2f)
                drawPath(path, if (i < rating) filled else empty)
            }
            if (i < 4) Spacer(Modifier.width(gap))
        }
    }
}

private fun starPath(r: Float): Path {
    val path = Path()
    val cx = r
    val cy = r
    val inner = r * 0.40f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r * 0.96f else inner
        val angle = (-PI / 2 + i * PI / 5).toFloat()
        val x = cx + radius * cos(angle)
        val y = cy + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** A segmented control, the Apple Books way. */
@Composable
fun SegmentRow(
    selected: Int,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    Row(
        modifier
            .background(pal.bgSidebar, RoundedCornerShape(Radius.md.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(28.dp)
                    .background(
                        if (on) pal.surface else Color.Transparent,
                        RoundedCornerShape(Radius.sm.dp),
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = Type.smallStrong,
                    color = if (on) pal.text else pal.textSecondary,
                )
            }
        }
    }
}

/** A key/value line in the detail panel: label at a fixed width, value after. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (value.isBlank()) return
    val pal = palette
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = Type.label,
            color = pal.textTertiary,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = if (mono) Type.numeric.copy(fontSize = 13.sp, lineHeight = 18.sp) else Type.label,
            color = if (accent || onClick != null) pal.textAccent else pal.text,
            modifier = Modifier
                .weight(1f)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
    }
}

@Composable
fun HSpacer(w: Dp) = Spacer(Modifier.width(w))
