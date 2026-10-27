package com.example.jianji.data

import com.example.jianji.domain.*
import org.json.JSONArray
import org.json.JSONObject
import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import java.time.LocalDate
import java.util.UUID

data class LedgerFile(val categories: List<Category>, val expenses: List<Expense>)

/** File schema is independent of the Room database schema. No local receipts or settings. */
object LedgerTransfer {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_RECORDS = 10_000

    fun validate(file: LedgerFile, today: Long = LocalDate.now().toEpochDay()) {
        require(file.categories.size <= 200 && file.expenses.size <= MAX_RECORDS) { "最多支持 200 个类型、10,000 笔账单" }
        fun uuid(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
        require(file.categories.map { it.id }.distinct().size == file.categories.size) { "文件包含重复类型 ID" }
        require(file.categories.map { CategoryRules.key(it.name) }.distinct().size == file.categories.size) { "文件包含重复类型名称" }
        file.categories.forEach {
            require(uuid(it.id) && CategoryRules.valid(it.name) && CategoryRules.name(it.name) == it.name &&
                it.colorKey in CategoryRules.colors && it.sortOrder >= 0 && it.iconKey in
                setOf("custom", "food", "transport", "shopping", "home", "fun", "health", "education", "other")) { "消费类型格式不正确" }
        }
        val ids = file.categories.map { it.id }.toSet()
        require(file.expenses.map { it.id }.distinct().size == file.expenses.size) { "文件包含重复账单 ID" }
        file.expenses.forEachIndexed { index, it ->
            require(uuid(it.id) && it.categoryId in ids && it.amountMinor in 1..MoneyParser.MAX_MINOR &&
                it.spentOn in 0..today && it.note == it.note.trim() && validNote(it.note) &&
                it.createdAt >= 0 && it.updatedAt >= 0 && it.revision in 1 until Long.MAX_VALUE) {
                "第 ${index + 1} 笔账单无效：请检查金额、日期、类型、备注和修订信息"
            }
        }
    }

    fun json(file: LedgerFile): ByteArray {
        validate(file)
        val root = JSONObject().put("format", "jianji-ledger").put("schemaVersion", 1)
            .put("currencyCode", "CNY").put("exportedAt", System.currentTimeMillis())
        root.put("categories", JSONArray().apply { file.categories.forEach { c ->
            put(JSONObject().put("id", c.id).put("name", c.name).put("iconKey", c.iconKey)
                .put("colorKey", c.colorKey).put("sortOrder", c.sortOrder).put("archived", c.archived))
        } })
        root.put("expenses", JSONArray().apply { file.expenses.forEach { e ->
            put(JSONObject().put("id", e.id).put("amountMinor", e.amountMinor).put("categoryId", e.categoryId)
                .put("spentOn", e.spentOn).put("note", e.note).put("createdAt", e.createdAt)
                .put("updatedAt", e.updatedAt).put("revision", e.revision))
        } })
        return bounded(root.toString().toByteArray(Charsets.UTF_8))
    }

    fun parse(bytes: ByteArray): LedgerFile {
        bounded(bytes)
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        val root = JsonReader(StringReader(text)).use { reader ->
            val value = readJson(reader, 0) as? JSONObject ?: error("请选择简记导出的 JSON 文件")
            require(reader.peek() == JsonToken.END_DOCUMENT) { "JSON 文件末尾包含额外内容" }
            value
        }
        require(root.string("format") == "jianji-ledger" && root.integer("schemaVersion") == 1L) { "不支持此文件格式或版本" }
        require(root.string("currencyCode") == "CNY") { "仅支持人民币 CNY 账单" }
        require(root.integer("exportedAt") >= 0) { "导出时间无效" }
        val categories = root.getJSONArray("categories")
        val expenses = root.getJSONArray("expenses")
        require(categories.length() <= 200 && expenses.length() <= MAX_RECORDS) { "文件超过类型或账单数量上限" }
        val file = LedgerFile((0 until categories.length()).map { index ->
            val c = categories.getJSONObject(index)
            val order = c.integer("sortOrder")
            require(order in 0..Int.MAX_VALUE.toLong()) { "类型排序值无效" }
            val archived = c.get("archived")
            require(archived is Boolean) { "类型状态无效" }
            Category(c.string("id"), c.string("name"), c.string("iconKey"), c.integer("colorKey"), order.toInt(), archived)
        }, (0 until expenses.length()).map { index ->
            val e = expenses.getJSONObject(index)
            Expense(e.string("id"), e.integer("amountMinor"), e.string("categoryId"), e.integer("spentOn"),
                e.string("note"), e.integer("createdAt"), e.integer("updatedAt"), e.integer("revision"))
        })
        validate(file)
        return file
    }

    fun csv(file: LedgerFile): ByteArray {
        validate(file)
        val categories = file.categories.associateBy { it.id }
        fun cell(raw: String): String {
            val dangerous = raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || raw.firstOrNull() in listOf('\t', '\r', '\n')
            val safe = if (dangerous) "'$raw" else raw
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val text = buildString {
            append("\uFEFF账单ID,消费日期,消费类型,金额（元）,币种,备注\r\n")
            file.expenses.forEach { e ->
                append(listOf(e.id, LocalDate.ofEpochDay(e.spentOn).toString(), categories.getValue(e.categoryId).name,
                    money(e.amountMinor), "CNY", e.note).joinToString(",", transform = ::cell))
                append("\r\n")
            }
        }
        return bounded(text.toByteArray(Charsets.UTF_8))
    }

    private fun bounded(bytes: ByteArray): ByteArray {
        require(bytes.size <= MAX_BYTES) { "文件不能超过 10 MiB" }
        return bytes
    }
    // Android's JSONObject(String) accepts comments, duplicate keys and non-JSON syntax.
    // Strict streaming tokenization also bounds nesting before building the small file tree.
    private fun readJson(reader: JsonReader, depth: Int): Any {
        require(depth <= 8) { "JSON 文件嵌套层级过多" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    require(length() < 32) { "JSON 对象包含过多字段" }
                    val name = reader.nextName()
                    require(!has(name)) { "JSON 文件包含重复字段" }
                    put(name, readJson(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while (reader.hasNext()) {
                    require(length() < MAX_RECORDS) { "文件超过数量上限" }
                    put(readJson(reader, depth + 1))
                }
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().toLongOrNull() ?: error("数字字段必须是有效整数")
            JsonToken.BOOLEAN -> reader.nextBoolean()
            else -> error("JSON 文件包含不支持的值")
        }
    }
    private fun JSONObject.string(key: String): String = (get(key) as? String) ?: error("字段 $key 必须是文本")
    private fun JSONObject.integer(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "字段 $key 必须是整数" }
        return (value as Number).toLong()
    }
}

class ImportPreview internal constructor(
    internal val file: LedgerFile,
    internal val epoch: String,
    internal val sequence: Long,
    internal val categories: List<CategoryEntity>,
    internal val expenses: List<ExpenseEntity>,
    val skipped: Int,
    val matchedCategories: Int,
    internal val categoryNames: Map<String, String>,
) {
    val added get() = expenses.size
    val newCategories get() = categories.size
    val totalMinor get() = expenses.sumOf { it.amountMinor }
}
