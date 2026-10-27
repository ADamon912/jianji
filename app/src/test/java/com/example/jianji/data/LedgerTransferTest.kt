package com.example.jianji.data

import android.app.Application
import com.example.jianji.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LedgerTransferTest {
    private val category = builtInCategories().first().domain()
    private fun fixture(note: String = "午餐,\"好吃\"\n🍜") = LedgerFile(listOf(category), listOf(
        Expense(UUID.randomUUID().toString(), 1234, category.id, 20_000, note, 100, 200, 2)))
    private fun reject(block: () -> Unit) {
        try { block(); fail("Expected invalid file to be rejected") } catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { } catch (_: java.io.IOException) { }
    }

    @Test fun jsonRoundTripPreservesUnicodeNotesCentsIdsAndAuditFields() {
        val file = fixture()
        assertEquals(file, LedgerTransfer.parse(LedgerTransfer.json(file)))
        assertEquals(file, LedgerTransfer.parse(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + LedgerTransfer.json(file)))
        // Device wall clocks can move backwards between creation and editing.
        val adjustedClock = file.copy(expenses = listOf(file.expenses.single().copy(updatedAt = 50)))
        assertEquals(adjustedClock, LedgerTransfer.parse(LedgerTransfer.json(adjustedClock)))
        assertEquals(LedgerFile(emptyList(), emptyList()), LedgerTransfer.parse(LedgerTransfer.json(LedgerFile(emptyList(), emptyList()))))
    }

    @Test fun csvEscapesQuotesNewlinesAndFormulaPrefixes() {
        val csv = LedgerTransfer.csv(fixture()).toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("\uFEFF账单ID,"))
        assertTrue(csv.contains("\"12.34\",\"CNY\",\"午餐,\"\"好吃\"\"\n🍜\"\r\n"))
        for (note in listOf("=1+2", "+SUM(1)", "-1+1", "@SUM(A1)")) {
            assertTrue(LedgerTransfer.csv(fixture(note)).toString(Charsets.UTF_8).contains("\"'$note\""))
        }
    }

    @Test fun invalidNumericTypesCurrencyVersionsAndDatesAreRejected() {
        val bytes = LedgerTransfer.json(fixture())
        fun mutate(action: (JSONObject) -> Unit) = JSONObject(bytes.toString(Charsets.UTF_8)).apply(action).toString().toByteArray()
        reject { LedgerTransfer.parse(mutate { it.put("schemaVersion", 2) }) }
        reject { LedgerTransfer.parse(mutate { it.put("currencyCode", "USD") }) }
        for (amount in listOf<Any>("1234", 12.34, 0L, MoneyParser.MAX_MINOR + 1)) {
            reject { LedgerTransfer.parse(mutate { it.getJSONArray("expenses").getJSONObject(0).put("amountMinor", amount) }) }
        }
        reject { LedgerTransfer.parse(mutate { it.getJSONArray("expenses").getJSONObject(0).put("spentOn", LocalDate.now().plusDays(1).toEpochDay()) }) }
    }

    @Test fun duplicateIdsMissingCategoryAndInvalidMetadataAreRejected() {
        val file = fixture()
        reject { LedgerTransfer.validate(file.copy(expenses = file.expenses + file.expenses)) }
        reject { LedgerTransfer.validate(file.copy(categories = emptyList())) }
        reject { LedgerTransfer.validate(file.copy(categories = file.categories + category.copy(id = UUID.randomUUID().toString()))) }
        for (record in listOf(file.expenses[0].copy(note = "a".repeat(201)), file.expenses[0].copy(revision = 0),
            file.expenses[0].copy(updatedAt = -1), file.expenses[0].copy(id = "bad"))) {
            reject { LedgerTransfer.validate(file.copy(expenses = listOf(record))) }
        }
    }

    @Test fun malformedUtf8TrailingContentDuplicateKeysAndDeepNestingAreRejected() {
        val json = LedgerTransfer.json(fixture()).toString(Charsets.UTF_8)
        reject { LedgerTransfer.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
        reject { LedgerTransfer.parse((json + "{}").toByteArray()) }
        reject { LedgerTransfer.parse(json.dropLast(1).toByteArray()) }
        reject { LedgerTransfer.parse(json.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1").toByteArray()) }
        reject { LedgerTransfer.parse(("[".repeat(100) + "0" + "]".repeat(100)).toByteArray()) }
    }

    @Test fun boundedInputAndRecordLimitRejectOversizeData() {
        reject { readLimited(ByteArrayInputStream(ByteArray(LedgerTransfer.MAX_BYTES + 1))) }
        assertEquals(3, readLimited(ByteArrayInputStream(byteArrayOf(1, 2, 3))).size)
        val file = fixture()
        reject { LedgerTransfer.validate(file.copy(expenses = List(LedgerTransfer.MAX_RECORDS + 1) { file.expenses[0] })) }
    }
}
