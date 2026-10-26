package com.example.jianji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.jianji.domain.StatisticsCalculator
import com.example.jianji.domain.StatisticsSnapshot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/** Labels are measured before placement so long names and larger fonts keep their own space. */
@Composable
fun CategoryCircularChart(snapshot: StatisticsSnapshot, donut: Boolean) {
    val nameStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val angles = buildList {
        var start = -90.0
        snapshot.slices.forEachIndexed { index, slice ->
            val sweep = if (index == snapshot.slices.lastIndex) 270.0 - start else slice.ratio * 360
            add(start to sweep)
            start += sweep
        }
    }
    val directions = angles.map { (start, sweep) ->
        val radians = Math.toRadians(start + sweep / 2)
        Offset(cos(radians).toFloat(), sin(radians).toFloat())
    }
    SubcomposeLayout(Modifier.fillMaxWidth().testTag("circularChart").semantics {
        contentDescription = if (donut) "消费类型占比环形图" else "消费类型占比饼图"
    }) { constraints ->
        val width = constraints.maxWidth
        val labelWidth = (width * 0.25f).roundToInt()
        val gap = 12.dp.toPx()
        val labelGap = 8.dp.roundToPx()
        val verticalPadding = 12.dp.roundToPx()
        val radius = ((width - labelWidth * 2) / 2f - gap).coerceAtLeast(1f)
        val labels = subcompose("labels") {
            snapshot.slices.forEachIndexed { index, slice ->
                val right = directions[index].x >= 0
                Column(Modifier.widthIn(max = labelWidth.toDp()).testTag("sliceLabel:${slice.key}"),
                    horizontalAlignment = if (right) Alignment.Start else Alignment.End) {
                    Text(slice.label, color = textColor, style = nameStyle, textAlign = if (right) TextAlign.Start else TextAlign.End)
                    Text("${StatisticsCalculator.percentage(slice.totalMinor, snapshot.totalMinor)}%", color = textColor, style = valueStyle)
                }
            }
        }.map { it.measure(Constraints(maxWidth = labelWidth)) }
        val sides = listOf(false, true).map { right -> directions.indices.filter { (directions[it].x >= 0) == right }.sortedBy { directions[it].y } }
        val requiredHeight = sides.maxOf { side -> side.sumOf { labels[it].height } + labelGap * (side.size - 1).coerceAtLeast(0) } + verticalPadding * 2
        val height = maxOf(240.dp.roundToPx(), requiredHeight, (radius * 2).roundToInt() + verticalPadding * 2)
        val center = Offset(width / 2f, height / 2f)
        val tops = IntArray(labels.size)
        sides.forEach { side ->
            var nextTop = verticalPadding
            side.forEach { i ->
                val desired = (center.y + directions[i].y * radius - labels[i].height / 2f).roundToInt()
                tops[i] = maxOf(nextTop, desired)
                nextTop = tops[i] + labels[i].height + labelGap
            }
            var bottom = height - verticalPadding
            side.asReversed().forEach { i -> tops[i] = minOf(tops[i], bottom - labels[i].height); bottom = tops[i] - labelGap }
        }
        val plot = subcompose("plot") {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = minOf(24.dp.toPx(), radius * 0.35f)
                val arcRadius = radius - if (donut) stroke / 2 else 0f
                angles.forEachIndexed { index, (start, sweep) ->
                    val slice = snapshot.slices[index]
                    val origin = Offset(center.x - arcRadius, center.y - arcRadius)
                    val arcSize = Size(arcRadius * 2, arcRadius * 2)
                    if (donut) drawArc(Color(slice.color), start.toFloat(), sweep.toFloat(), false, origin, arcSize, style = Stroke(stroke))
                    else drawArc(Color(slice.color), start.toFloat(), sweep.toFloat(), true, origin, arcSize)
                    val direction = directions[index]
                    val right = direction.x >= 0
                    val edge = center + direction * radius
                    val outer = center + direction * (radius + 4.dp.toPx())
                    val endX = if (right) width - labelWidth - 3.dp.toPx() else labelWidth + 3.dp.toPx()
                    val endY = tops[index] + labels[index].height / 2f
                    val railX = center.x + (if (right) 1 else -1) * (radius + gap / 2)
                    val line = Path().apply {
                        moveTo(edge.x, edge.y); lineTo(outer.x, outer.y)
                        lineTo(railX, outer.y); lineTo(railX, endY); lineTo(endX, endY)
                    }
                    drawPath(line, Color(slice.color), style = Stroke(1.dp.toPx()))
                    drawCircle(Color(slice.color), 2.dp.toPx(), edge)
                }
            }
        }.single().measure(Constraints.fixed(width, height))
        val centerLabel = if (donut) subcompose("center") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("支出分布", color = textColor, style = nameStyle, textAlign = TextAlign.Center)
                Text("${snapshot.categories.size} 个类型", color = subtleColor, style = nameStyle, textAlign = TextAlign.Center)
            }
        }.single().measure(Constraints(maxWidth = (radius * 1.15f).roundToInt(), maxHeight = (radius * 1.15f).roundToInt())) else null
        layout(width, height) {
            plot.place(0, 0)
            centerLabel?.place((width - centerLabel.width) / 2, (height - centerLabel.height) / 2)
            labels.forEachIndexed { index, label ->
                label.place(if (directions[index].x >= 0) width - labelWidth else labelWidth - label.width, tops[index])
            }
        }
    }
}
