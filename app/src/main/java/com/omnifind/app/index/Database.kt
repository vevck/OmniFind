package com.omnifind.app.index

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One SQLite connection (the bundled SQLite: FTS5 and JSON1 built in), used by one thread at a time. The index opens
 * two on the same file in WAL mode, one that writes (indexing) and one that reads (search), so a search never waits
 * for an indexing transaction.
 */
class Database(file: File) : AutoCloseable {
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(file.path)
    private val lock = ReentrantLock()

    init {
        connection.execSQL("PRAGMA busy_timeout = 10000")
        connection.prepare("PRAGMA journal_mode = WAL").use { it.step() }
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("PRAGMA synchronous = NORMAL")
    }

    /** A value to bind: null, Long, Int, Double, String or ByteArray. */
    private fun SQLiteStatement.bindAll(args: List<Any?>) {
        args.forEachIndexed { i, value ->
            val index = i + 1
            when (value) {
                null -> bindNull(index)
                is Long -> bindLong(index, value)
                is Int -> bindLong(index, value.toLong())
                is Boolean -> bindLong(index, if (value) 1 else 0)
                is Double -> bindDouble(index, value)
                is Float -> bindDouble(index, value.toDouble())
                is String -> bindText(index, value)
                is ByteArray -> bindBlob(index, value)
                else -> error("can't bind ${value::class}")
            }
        }
    }

    fun execute(sql: String) = lock.withLock { connection.execSQL(sql) }

    /** Runs a statement that returns no rows. */
    fun run(sql: String, vararg args: Any?) = lock.withLock {
        connection.prepare(sql).use { statement ->
            statement.bindAll(args.toList())
            while (statement.step()) { /* drain */ }
        }
    }

    /** Calls [row] for every row. */
    fun query(sql: String, vararg args: Any?, row: (Row) -> Unit) = lock.withLock {
        connection.prepare(sql).use { statement ->
            statement.bindAll(args.toList())
            val wrapper = Row(statement)
            while (statement.step()) row(wrapper)
        }
    }

    fun <T> single(sql: String, vararg args: Any?, read: (Row) -> T): T? {
        var out: T? = null
        var seen = false
        query(sql, *args) { if (!seen) { out = read(it); seen = true } }
        return out
    }

    fun long(sql: String, vararg args: Any?): Long = single(sql, *args) { it.long(0) } ?: 0L

    val lastInsertId: Long get() = long("SELECT last_insert_rowid()")

    /** Runs [block] in one transaction; an exception rolls it back. Nested calls join the outer one. */
    fun <T> transaction(block: () -> T): T = lock.withLock {
        if (depth > 0) return@withLock block()
        connection.execSQL("BEGIN IMMEDIATE")
        depth++
        try {
            val result = block()
            connection.execSQL("COMMIT")
            result
        } catch (error: Throwable) {
            runCatching { connection.execSQL("ROLLBACK") }
            throw error
        } finally {
            depth--
        }
    }
    private var depth = 0

    override fun close() = lock.withLock { connection.close() }

    /** The current row of a query (columns from 0). */
    class Row(private val statement: SQLiteStatement) {
        fun long(column: Int) = statement.getLong(column)
        fun int(column: Int) = statement.getLong(column).toInt()
        fun double(column: Int) = statement.getDouble(column)
        fun text(column: Int): String? = if (statement.isNull(column)) null else statement.getText(column)
        fun blob(column: Int): ByteArray? = if (statement.isNull(column)) null else statement.getBlob(column)
        fun isNull(column: Int) = statement.isNull(column)
    }
}
