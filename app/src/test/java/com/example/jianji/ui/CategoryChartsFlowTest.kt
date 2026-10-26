package com.example.jianji.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.jianji.JianjiApplication
import com.example.jianji.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = JianjiApplication::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryChartsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun waitText(value: String) {
        try { compose.waitUntil(20_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: ComposeTimeoutException) { throw AssertionError("等待失败：$value\n${compose.onRoot().printToString()}", failure) }
    }
    private fun saveCategory(name: String) {
        compose.onNodeWithTag("categoryNameInput").performTextClearance()
        compose.onNodeWithTag("categoryNameInput").performTextInput(name)
        compose.onNodeWithText("保存类型").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("categoryNameInput").fetchSemanticsNodes().isEmpty() }
    }
    private fun screenshot(name: String) {
        val file = File("build/outputs/screenshots/1.1.1/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun chart(type: ChartType, description: String) {
        compose.onNodeWithTag("statisticsList").performScrollToNode(hasText(type.label))
        compose.onNodeWithText(type.label).performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(type.label) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("statisticsList").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        if (type == ChartType.DONUT || type == ChartType.PIE) {
            compose.onNode(hasText("宠物护理") and hasAnyAncestor(hasTestTag("circularChart"))).assertExists()
            compose.onNode(hasText("11.3%") and hasAnyAncestor(hasTestTag("circularChart"))).assertExists()
        }
        screenshot(type.name.lowercase())
    }

    @Test fun manageCategoryFromDraftSwitchChartsArchiveAndRestore() {
        val app = ApplicationProvider.getApplicationContext<JianjiApplication>()
        app.deleteDatabase("jianji.db")
        // Test fixtures only: varied categories and dates make chart screenshots reviewable.
        runBlocking {
            val repo = app.container.expenses
            repo.initialize()
            val categories = repo.observeCategories().first()
            val today = LocalDate.now()
            listOf(7500L, 2000L, 16000L).forEachIndexed { index, amount ->
                val date = today.withDayOfMonth(1).plusDays(minOf(index.toLong(), today.dayOfMonth.toLong() - 1))
                val result = repo.execute(ExpenseCommand(WriteContext(UUID.randomUUID().toString(), repo.dataEpoch()), Operation.CREATE, UUID.randomUUID().toString(), input = ExpenseInput(amount, categories[index].id, date.toEpochDay(), "图表测试记录")))
                check(result is CommandResult.Success)
            }
        }
        compose.setContent { JianjiApp() }
        waitText("简记 · 消费明细")
        compose.onNodeWithContentDescription("记一笔").performClick()
        waitText("消费金额（元）")
        compose.onNodeWithTag("amountInput").performTextInput("32.50")
        compose.onNodeWithText("管理类型").performClick()
        waitText("消费类型管理")
        compose.onNodeWithContentDescription("添加类型").performClick()
        saveCategory("宠物")
        compose.onNodeWithTag("categoryList").performScrollToNode(hasContentDescription("编辑宠物"))
        compose.onNodeWithContentDescription("编辑宠物").performClick()
        compose.onNodeWithContentDescription("湖蓝").performClick()
        saveCategory("宠物护理")
        compose.onNodeWithTag("categoryList").performScrollToNode(hasContentDescription("编辑宠物护理"))
        screenshot("categories")
        compose.onNodeWithContentDescription("返回").performClick()
        waitText("消费金额（元）")
        compose.onNodeWithTag("amountInput").assertTextContains("32.50")
        compose.onNodeWithText("宠物护理").performScrollTo().performClick()
        compose.onNodeWithTag("noteInput").performScrollTo().performTextInput("猫粮")
        compose.onNodeWithText("保存消费").performScrollTo().performClick()
        waitText("猫粮")
        screenshot("ledger")
        compose.onNodeWithText("统计").performClick()
        waitText("¥ 287.50")
        chart(ChartType.DONUT, "消费类型占比环形图")
        chart(ChartType.PIE, "消费类型占比饼图")
        chart(ChartType.BAR, "消费类型支出柱状图")
        chart(ChartType.LINE, "支出趋势折线图")
        assertEquals(ChartType.LINE, runBlocking { app.container.settings.chartType.first() })
        compose.onNodeWithText("设置").performClick()
        waitText("消费类型管理")
        compose.onNodeWithText("深色").performScrollTo().performClick()
        try { compose.waitUntil(20_000) { compose.waitForIdle(); runBlocking { app.container.settings.theme.first() } == ThemeMode.DARK } }
        catch (failure: ComposeTimeoutException) { screenshot("theme-failure"); throw AssertionError(compose.onRoot().printToString(), failure) }
        compose.waitForIdle()
        screenshot("settings-dark")
        compose.onNodeWithText("消费类型管理").performScrollTo().performClick()
        waitText("消费类型管理")
        compose.onNodeWithTag("categoryList").performScrollToNode(hasContentDescription("删除宠物护理"))
        compose.onNodeWithContentDescription("删除宠物护理").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("编辑宠物护理").assertExists()
        compose.onNodeWithContentDescription("删除宠物护理").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(20_000) { compose.waitForIdle(); runBlocking { app.container.expenses.observeCategories().first() }.any { it.name == "宠物护理" && it.archived } }
        compose.onNodeWithTag("categoryList").performScrollToNode(hasContentDescription("恢复宠物护理"))
        compose.onNodeWithContentDescription("恢复宠物护理").performClick()
        compose.waitUntil(20_000) { compose.waitForIdle(); runBlocking { app.container.expenses.observeCategories().first() }.any { it.name == "宠物护理" && !it.archived } }
        // A category with no expenses is physically removed.
        compose.onNodeWithContentDescription("添加类型").performClick()
        saveCategory("临时类型")
        compose.onNodeWithTag("categoryList").performScrollToNode(hasContentDescription("删除临时类型"))
        compose.onNodeWithContentDescription("删除临时类型").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(20_000) { compose.waitForIdle(); runBlocking { app.container.expenses.observeCategories().first() }.none { it.name == "临时类型" } }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithTag("statisticsList").performScrollToIndex(0)
        waitText("¥ 287.50")
        compose.onNodeWithTag("statisticsList").performScrollToNode(hasText("折线图"))
        compose.onNodeWithText("折线图").assertIsSelected()
        screenshot("statistics-dark")
    }
}
