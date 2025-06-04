package com.ataiva.eden.database

/**
 * Paginated result container
 */
data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    val isEmpty: Boolean get() = content.isEmpty()
    val isNotEmpty: Boolean get() = content.isNotEmpty()
    val size: Int get() = content.size
    
    companion object {
        fun <T> empty(pageSize: Int = 20): Page<T> {
            return Page(
                content = emptyList(),
                totalElements = 0,
                totalPages = 0,
                pageNumber = 0,
                pageSize = pageSize,
                hasNext = false,
                hasPrevious = false
            )
        }
        
        fun <T> of(content: List<T>, pageNumber: Int, pageSize: Int, totalElements: Long): Page<T> {
            val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / pageSize + 1).toInt()
            return Page(
                content = content,
                totalElements = totalElements,
                totalPages = totalPages,
                pageNumber = pageNumber,
                pageSize = pageSize,
                hasNext = pageNumber < totalPages - 1,
                hasPrevious = pageNumber > 0
            )
        }
    }
}

/**
 * Database query result set
 */
interface ResultSet {
    suspend fun next(): Boolean
    suspend fun close()
    fun getRow(): ResultRow
    fun getRowCount(): Int
    suspend fun toList(): List<ResultRow>
}

/**
 * Database result row
 */
interface ResultRow {
    fun getString(columnName: String): String?
    fun getString(columnIndex: Int): String?
    fun getInt(columnName: String): Int?
    fun getInt(columnIndex: Int): Int?
    fun getLong(columnName: String): Long?
    fun getLong(columnIndex: Int): Long?
    fun getDouble(columnName: String): Double?
    fun getDouble(columnIndex: Int): Double?
    fun getBoolean(columnName: String): Boolean?
    fun getBoolean(columnIndex: Int): Boolean?
    fun getBytes(columnName: String): ByteArray?
    fun getBytes(columnIndex: Int): ByteArray?
    fun isNull(columnName: String): Boolean
    fun isNull(columnIndex: Int): Boolean
    fun getColumnNames(): List<String>
    fun getColumnCount(): Int
}

/**
 * Default result set implementation
 */
class DefaultResultSet(
    private val rows: List<ResultRow>
) : ResultSet {
    
    private var currentIndex = -1
    private var closed = false
    
    override suspend fun next(): Boolean {
        if (closed) return false
        currentIndex++
        return currentIndex < rows.size
    }
    
    override suspend fun close() {
        closed = true
    }
    
    override fun getRow(): ResultRow {
        if (closed || currentIndex < 0 || currentIndex >= rows.size) {
            throw IllegalStateException("No current row available")
        }
        return rows[currentIndex]
    }
    
    override fun getRowCount(): Int {
        return rows.size
    }
    
    override suspend fun toList(): List<ResultRow> {
        return rows
    }
}

/**
 * Default result row implementation
 */
class DefaultResultRow(
    private val data: Map<String, Any?>,
    private val columnNames: List<String> = data.keys.toList()
) : ResultRow {
    
    override fun getString(columnName: String): String? {
        return data[columnName]?.toString()
    }
    
    override fun getString(columnIndex: Int): String? {
        if (columnIndex < 0 || columnIndex >= columnNames.size) return null
        return getString(columnNames[columnIndex])
    }
    
    override fun getInt(columnName: String): Int? {
        return when (val value = data[columnName]) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
    
    override fun getInt(columnIndex: Int): Int? {
        if (columnIndex < 0 || columnIndex >= columnNames.size) return null
        return getInt(columnNames[columnIndex])
    }
    
    override fun getLong(columnName: String): Long? {
        return when (val value = data[columnName]) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
    
    override fun getLong(columnIndex: Int): Long? {
        if (columnIndex < 0 || columnIndex >= columnNames.size) return null
        return getLong(columnNames[columnIndex])
    }
    
    override fun getDouble(columnName: String): Double? {
        return when (val value = data[columnName]) {
            is Double -> value
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }
    
    override fun getDouble(columnIndex: Int): Double? {
        if (columnIndex < 0 || columnIndex >= columnNames.size) return null
        return getDouble(columnNames[columnIndex])
    }
    
    override fun getBoolean(columnName: String): Boolean? {
        return when (val value = data[columnName]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            is Number -> value.toInt() != 0
            else -> null
        }
    }
    
    override fun getBoolean(columnIndex: Int): Boolean? {
        if (columnIndex < 0 || columnIndex >= columnNames.size) return null
        return getBoolean(columnNames[columnIndex])
    }
    
    override fun getBytes(columnName: String): ByteArray? {
        return when (val value = data[columnName]) {
            is ByteArray -> value
            is String -> value.encodeToByteArray()
            else -> null
        }
    }
    
    override fun getBytes(columnIndex: Int): ByteArray? {
        if (columnIndex < 0 || columnIndex >= columnNames.size) return null
        return getBytes(columnNames[columnIndex])
    }
    
    override fun isNull(columnName: String): Boolean {
        return data[columnName] == null
    }
    
    override fun isNull(columnIndex: Int): Boolean {
        if (columnIndex < 0 || columnIndex >= columnNames.size) return true
        return isNull(columnNames[columnIndex])
    }
    
    override fun getColumnNames(): List<String> {
        return columnNames
    }
    
    override fun getColumnCount(): Int {
        return columnNames.size
    }
}

/**
 * Pageable interface for pagination support
 */
interface Pageable {
    val pageNumber: Int
    val pageSize: Int
    val offset: Long
    val sort: Sort?
}

/**
 * Sort specification
 */
data class Sort(
    val orders: List<Order>
) {
    data class Order(
        val property: String,
        val direction: Direction = Direction.ASC
    )
    
    enum class Direction {
        ASC, DESC
    }
    
    companion object {
        fun by(vararg properties: String): Sort {
            return Sort(properties.map { Order(it) })
        }
        
        fun by(direction: Direction, vararg properties: String): Sort {
            return Sort(properties.map { Order(it, direction) })
        }
    }
}

/**
 * Default pageable implementation
 */
data class PageRequest(
    override val pageNumber: Int,
    override val pageSize: Int,
    override val sort: Sort? = null
) : Pageable {
    
    override val offset: Long
        get() = pageNumber.toLong() * pageSize
    
    companion object {
        fun of(page: Int, size: Int): PageRequest {
            return PageRequest(page, size)
        }
        
        fun of(page: Int, size: Int, sort: Sort): PageRequest {
            return PageRequest(page, size, sort)
        }
    }
}