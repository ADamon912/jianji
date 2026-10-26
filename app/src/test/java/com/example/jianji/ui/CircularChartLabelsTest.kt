package com.example.jianji.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.jianji.domain.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN-w360dp-h960dp", fontScale = 1.3f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircularChartLabelsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun clusteredSmallSlicesKeepAllLabelsInsideChartWithoutOverlap() {
        val values = (0..7).map { i -> CategoryTotal(Category("type$i", "自定义长名称消费类型$i", "custom", CategoryRules.colors[i], i), if (i == 0) 9900L else 10L + i, 1) }
        val snapshot = StatisticsCalculator.calculate(DateRange(0, 1), values)
        val donut = mutableStateOf(true)
        compose.setContent {
            JianjiTheme(dark = true) { Surface { Box(Modifier.fillMaxWidth().padding(20.dp)) { CategoryCircularChart(snapshot, donut.value) } } }
        }
        listOf(true, false).forEach { ring ->
            compose.runOnIdle { donut.value = ring }
            val chart = compose.onNodeWithTag("circularChart").fetchSemanticsNode().boundsInRoot
            val labels = snapshot.slices.map { slice ->
                compose.onNode(hasText(slice.label) and hasAnyAncestor(hasTestTag("sliceLabel:${slice.key}"))).assertExists()
                compose.onNode(hasText("${StatisticsCalculator.percentage(slice.totalMinor, snapshot.totalMinor)}%") and hasAnyAncestor(hasTestTag("sliceLabel:${slice.key}"))).assertExists()
                compose.onNodeWithTag("sliceLabel:${slice.key}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            labels.forEach { label -> assertTrue("Label must stay inside chart: $label", label.left >= chart.left && label.right <= chart.right && label.top >= chart.top && label.bottom <= chart.bottom) }
            labels.forEachIndexed { i, label -> labels.drop(i + 1).forEach { other -> assertTrue("Labels overlap: $label / $other", !label.overlaps(other)) } }
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/outputs/screenshots/1.1.1/${if (ring) "donut" else "pie"}-dense-dark.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
