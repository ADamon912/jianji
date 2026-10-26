package com.example.jianji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jianji.domain.*
import kotlin.math.roundToInt
import java.math.BigDecimal
import java.math.RoundingMode

private fun axisMoney(minor: Long): String {
    val yuan = BigDecimal.valueOf(minor, 2)
    return when {
        minor >= 10_000_000_000L -> yuan.divide(BigDecimal("100000000"), 1, RoundingMode.HALF_UP).toPlainString() + "亿"
        minor >= 1_000_000 -> yuan.divide(BigDecimal("10000"), 1, RoundingMode.HALF_UP).toPlainString() + "万"
        else -> money(minor)
    }
}

@Composable
fun CategoryBarChart(snapshot: StatisticsSnapshot, onDetails: (Set<String>) -> Unit) {
    val maximum = snapshot.slices.maxOf { it.totalMinor }
    Text("单位：元 · 最高 ${money(maximum)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = "消费类型支出柱状图" }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        snapshot.slices.forEach { slice ->
            Column(Modifier.width(76.dp).clickable { onDetails(slice.categoryIds) }.semantics { contentDescription = "${slice.label}，支出${money(slice.totalMinor)}元，查看明细" }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(axisMoney(slice.totalMinor), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(36.dp).height((156f * (slice.totalMinor.toDouble() / maximum).toFloat()).coerceAtLeast(2f).dp)
                            .background(Brush.verticalGradient(listOf(Color(slice.color).copy(alpha = 0.65f), Color(slice.color))), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                    }
                }
                HorizontalDivider()
                Text(slice.label, Modifier.padding(top = 10.dp).heightIn(min = 40.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (snapshot.slices.size > 3) Text("左右滑动查看更多类型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TrendLineChart(snapshot: TrendSnapshot) {
    val points = snapshot.points
    if (points.isEmpty()) return
    var selected by remember(snapshot) { mutableIntStateOf(points.lastIndex) }
    val maximum = points.maxOf { it.totalMinor }.coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val selectedPoint = points[selected]
    Text("按${snapshot.unit}汇总 · 点击曲线查看金额", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("${selectedPoint.label}  ¥${money(selectedPoint.totalMinor)}", fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(62.dp).fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(axisMoney(maximum), style = MaterialTheme.typography.labelSmall)
            Text(axisMoney(maximum / 2), style = MaterialTheme.typography.labelSmall)
            Text("0", style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().semantics {
            contentDescription = "支出趋势折线图"
            stateDescription = "${selectedPoint.label}，${money(selectedPoint.totalMinor)}元"
        }.pointerInput(snapshot) {
            detectTapGestures { offset ->
                val pad = 8.dp.toPx()
                selected = if (points.size == 1) 0 else (((offset.x - pad) / (size.width - 2 * pad).coerceAtLeast(1f)) * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)
            }
        }) {
            val pad = 8.dp.toPx()
            val width = (size.width - 2 * pad).coerceAtLeast(1f)
            val height = size.height - 2 * pad
            repeat(5) { line -> val y = pad + height * line / 4; drawLine(grid, Offset(pad, y), Offset(pad + width, y), 1.dp.toPx()) }
            fun position(index: Int) = Offset(pad + if (points.size == 1) width / 2 else width * index / points.lastIndex,
                pad + height * (1 - points[index].totalMinor.toDouble() / maximum).toFloat())
            val path = Path().apply { points.indices.forEach { i -> val p = position(i); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val area = Path().apply { addPath(path); lineTo(position(points.lastIndex).x, pad + height); lineTo(position(0).x, pad + height); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.01f))))
            drawPath(path, color, style = Stroke(2.5.dp.toPx()))
            drawLine(color.copy(alpha = 0.35f), Offset(position(selected).x, pad), Offset(position(selected).x, pad + height), 1.dp.toPx())
            drawCircle(color, 4.dp.toPx(), position(selected))
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 70.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.first().label, style = MaterialTheme.typography.labelSmall)
        if (points.size > 1) Text(points.last().label, style = MaterialTheme.typography.labelSmall)
    }
}
