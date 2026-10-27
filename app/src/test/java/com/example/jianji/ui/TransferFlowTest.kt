package com.example.jianji.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.jianji.data.*
import com.example.jianji.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransferFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: LedgerDatabase
    private lateinit var repo: RoomExpenseRepository
    private lateinit var model: TransferViewModel
    private val uri = Uri.parse("content://test/ledger.json")
    private val dark = mutableStateOf(false)
    private val documents = object : LedgerDocuments {
        var input = byteArrayOf()
        var output = byteArrayOf()
        var failWrite = false
        override fun read(uri: Uri) = input
        override fun write(uri: Uri, bytes: ByteArray) {
            if (failWrite) throw IOException("provider failed")
            output = bytes
        }
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomExpenseRepository(db)
        runBlocking { repo.initialize() }
        val category = builtInCategories()[0].domain()
        documents.input = LedgerTransfer.json(LedgerFile(listOf(category), listOf(
            Expense(UUID.randomUUID().toString(), 3250, category.id, 20_000, "导入测试", 100, 100, 1))))
        compose.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = TransferViewModel(repo, documents) as T
            })[TransferViewModel::class.java]
        }
        compose.setContent {
            val state by model.state.collectAsState()
            JianjiTheme(dark.value) {
                TransferContent(state, {}, { model.importFile(uri) }, { model.exportFile(uri, false) },
                    { model.exportFile(uri, true) }, model::confirmImport, model::discard)
            }
        }
    }
    @After fun close() { db.close() }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun waitDone() { compose.waitUntil(20_000) { compose.waitForIdle(); !model.state.value.busy } }
    private fun screenshot(name: String) {
        val file = File("build/outputs/screenshots/1.2.0/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun previewConfirmDuplicateImportAndBothExports() {
        screenshot("transfer")
        click("选择 JSON 导入")
        waitDone()
        compose.onNodeWithText("导入预览").assertExists()
        assertEquals(0L, runBlocking { db.ledger().expenseCount() })
        screenshot("preview-light")
        compose.runOnIdle { dark.value = true }
        screenshot("preview-dark")
        click("确认合并导入")
        waitDone()
        assertEquals(1L, runBlocking { db.ledger().expenseCount() })
        assertTrue(model.state.value.message!!.startsWith("导入完成：新增 1 笔"))
        click("选择 JSON 导入")
        waitDone()
        assertEquals(1, model.state.value.preview!!.skipped)
        click("确认合并导入")
        waitDone()
        assertEquals(1L, runBlocking { db.ledger().expenseCount() })
        click("导出 JSON")
        waitDone()
        assertEquals(3250L, LedgerTransfer.parse(documents.output).expenses.single().amountMinor)
        click("导出 CSV")
        waitDone()
        assertTrue(documents.output.toString(Charsets.UTF_8).contains("\"32.50\""))
    }

    @Test fun cancellingPreviewAndPickerAndFileFailuresDoNotWrite() {
        compose.runOnIdle { model.importFile(null); model.exportFile(null, false) }
        assertNull(model.state.value.message)
        click("选择 JSON 导入")
        waitDone()
        click("取消本次导入")
        assertNull(model.state.value.preview)
        assertEquals(0L, runBlocking { db.ledger().expenseCount() })
        documents.input = "bad file".toByteArray()
        click("选择 JSON 导入")
        waitDone()
        assertTrue(model.state.value.error)
        assertNull(model.state.value.preview)
        documents.failWrite = true
        click("导出 JSON")
        waitDone()
        assertTrue(model.state.value.error)
        assertTrue(model.state.value.message!!.contains("不完整文件"))
        assertEquals(0L, runBlocking { db.ledger().expenseCount() })
    }
}
