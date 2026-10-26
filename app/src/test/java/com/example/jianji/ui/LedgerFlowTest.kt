package com.example.jianji.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.jianji.JianjiApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = JianjiApplication::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LedgerFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun waitText(value: String) {
        try {
            compose.waitUntil(20_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("等待文字失败：$value\n${compose.onRoot().printToString()}", failure)
        }
    }

    @Test fun addEditDrillIntoStatisticsAndDelete() {
        ApplicationProvider.getApplicationContext<JianjiApplication>().deleteDatabase("jianji.db")
        compose.setContent { JianjiApp() }
        waitText("简记 · 消费明细")
        compose.onNodeWithContentDescription("记一笔").performClick()
        waitText("消费金额（元）")
        compose.onNodeWithTag("amountInput").performTextInput("12.30")
        waitText("餐饮")
        compose.onNodeWithText("餐饮").performScrollTo().performClick()
        compose.onNodeWithTag("noteInput").performScrollTo().performTextInput("测试午餐")
        compose.onNodeWithText("保存消费").performScrollTo().performClick()
        waitText("测试午餐")
        compose.onNodeWithText("测试午餐").performScrollTo().performClick()
        waitText("修改这笔消费")
        compose.onNodeWithText("修改这笔消费").performScrollTo().performClick()
        waitText("消费金额（元）")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("12.30").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("amountInput").performTextClearance()
        compose.onNodeWithTag("amountInput").performTextInput("20.00")
        compose.onNodeWithText("交通").performScrollTo().performClick()
        compose.onNodeWithText("保存消费").performScrollTo().performClick()
        waitText("修改这笔消费")
        compose.onNodeWithContentDescription("返回").performClick()
        waitText("统计")
        compose.onNodeWithText("统计").performClick()
        waitText("¥ 20.00")
        val categoryDetails = hasText("交通") and hasText("100.0%") and hasClickAction()
        compose.onNodeWithTag("statisticsList").performScrollToNode(categoryDetails)
        compose.onNode(categoryDetails).assertIsDisplayed().performClick()
        waitText("分类消费明细")
        waitText("测试午餐")
        compose.onNodeWithText("测试午餐").performScrollTo().performClick()
        waitText("删除消费")
        compose.onNodeWithText("删除消费").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("删除消费").assertExists()
        compose.onNodeWithText("删除消费").performClick()
        compose.onNodeWithText("删除").performClick()
        waitText("当前范围没有消费记录")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("statisticsList").performScrollToIndex(0)
        waitText("这段时间还没有消费")
        compose.onNodeWithText("¥ 0.00").assertIsDisplayed()
    }
}
