package com.ataiva.eden.testing.extensions

/**
 * Test extensions and utilities for common testing patterns
 * Provides convenient methods for test assertions and data manipulation
 */

/**
 * String extensions for testing
 */
fun String.isValidEmail(): Boolean {
    return this.contains("@") && this.contains(".")
}

fun String.isValidId(): Boolean {
    return this.isNotEmpty() && this.length >= 3
}

fun String.isValidSlug(): Boolean {
    return this.matches(Regex("^[a-z0-9-]+$"))
}

/**
 * Collection extensions for testing
 */
fun <T> List<T>.hasSize(expectedSize: Int): Boolean {
    return this.size == expectedSize
}

fun <T> List<T>.containsAll(vararg items: T): Boolean {
    return items.all { this.contains(it) }
}

fun <T> Set<T>.hasPermission(permission: String): Boolean {
    return this.any { it.toString().contains(permission) }
}

/**
 * Map extensions for testing
 */
fun Map<String, Any>.hasKey(key: String): Boolean {
    return this.containsKey(key)
}

fun Map<String, Any>.hasValue(value: Any): Boolean {
    return this.containsValue(value)
}

/**
 * Test assertion helpers
 */
object TestAssertions {
    
    fun assertNotEmpty(value: String, fieldName: String = "value") {
        require(value.isNotEmpty()) { "$fieldName should not be empty" }
    }
    
    fun assertValidEmail(email: String) {
        require(email.isValidEmail()) { "Invalid email format: $email" }
    }
    
    fun assertValidId(id: String, fieldName: String = "id") {
        require(id.isValidId()) { "Invalid $fieldName format: $id" }
    }
    
    fun assertValidSlug(slug: String) {
        require(slug.isValidSlug()) { "Invalid slug format: $slug" }
    }
    
    fun <T> assertListSize(list: List<T>, expectedSize: Int, listName: String = "list") {
        require(list.hasSize(expectedSize)) { 
            "$listName should have size $expectedSize, but was ${list.size}" 
        }
    }
    
    fun <T> assertContains(list: List<T>, item: T, listName: String = "list") {
        require(list.contains(item)) { "$listName should contain $item" }
    }
    
    fun assertMapHasKey(map: Map<String, Any>, key: String, mapName: String = "map") {
        require(map.hasKey(key)) { "$mapName should contain key: $key" }
    }
    
    fun assertBooleanTrue(value: Boolean, message: String) {
        require(value) { message }
    }
    
    fun assertBooleanFalse(value: Boolean, message: String) {
        require(!value) { message }
    }
}

/**
 * Test data generators
 */
object TestDataGenerators {
    
    private val random = kotlin.random.Random.Default
    
    fun randomString(length: Int = 10): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
    
    fun randomEmail(): String {
        return "${randomString(8)}@${randomString(6)}.com"
    }
    
    fun randomId(): String {
        return "test-${randomString(8)}-${random.nextInt(1000, 9999)}"
    }
    
    fun randomSlug(): String {
        return randomString(8).lowercase()
    }
    
    fun randomInt(min: Int = 1, max: Int = 100): Int {
        return random.nextInt(min, max + 1)
    }
    
    fun randomBoolean(): Boolean {
        return random.nextBoolean()
    }
    
    fun <T> randomListOf(generator: () -> T, size: Int = 3): List<T> {
        return (1..size).map { generator() }
    }
    
    fun randomMapOf(size: Int = 3): Map<String, String> {
        return (1..size).associate { 
            randomString(5) to randomString(10) 
        }
    }
}

/**
 * Test timing utilities
 */
object TestTiming {
    
    fun measureTime(block: () -> Unit): Long {
        // Simple timing implementation for testing using multiplatform approach
        val start = kotlin.system.getTimeMillis()
        block()
        return kotlin.system.getTimeMillis() - start
    }
    
    fun assertExecutionTime(maxMillis: Long, block: () -> Unit) {
        val time = measureTime(block)
        require(time <= maxMillis) {
            "Execution took ${time}ms, expected <= ${maxMillis}ms"
        }
    }
}

/**
 * Test validation helpers
 */
object TestValidation {
    
    fun validateUserData(
        id: String,
        email: String,
        isActive: Boolean = true
    ) {
        TestAssertions.assertValidId(id, "user id")
        TestAssertions.assertValidEmail(email)
        TestAssertions.assertBooleanTrue(isActive, "User should be active by default")
    }
    
    fun validateOrganizationData(
        id: String,
        name: String,
        slug: String,
        isActive: Boolean = true
    ) {
        TestAssertions.assertValidId(id, "organization id")
        TestAssertions.assertNotEmpty(name, "organization name")
        TestAssertions.assertValidSlug(slug)
        TestAssertions.assertBooleanTrue(isActive, "Organization should be active by default")
    }
    
    fun validatePermissionData(
        id: String,
        name: String,
        resource: String,
        action: String
    ) {
        TestAssertions.assertValidId(id, "permission id")
        TestAssertions.assertNotEmpty(name, "permission name")
        TestAssertions.assertNotEmpty(resource, "permission resource")
        TestAssertions.assertNotEmpty(action, "permission action")
        require(name.contains(":")) { "Permission name should contain ':' separator" }
    }
    
    fun validateSessionData(
        id: String,
        userId: String,
        token: String,
        isActive: Boolean = true
    ) {
        TestAssertions.assertValidId(id, "session id")
        TestAssertions.assertValidId(userId, "user id")
        TestAssertions.assertNotEmpty(token, "session token")
        TestAssertions.assertBooleanTrue(isActive, "Session should be active by default")
    }
}

/**
 * Test comparison utilities
 */
object TestComparison {
    
    fun <T> listsEqual(list1: List<T>, list2: List<T>): Boolean {
        return list1.size == list2.size && list1.containsAll(list2)
    }
    
    fun <K, V> mapsEqual(map1: Map<K, V>, map2: Map<K, V>): Boolean {
        return map1.size == map2.size && map1.all { (k, v) -> map2[k] == v }
    }
    
    fun setsEqual(set1: Set<String>, set2: Set<String>): Boolean {
        return set1.size == set2.size && set1.containsAll(set2)
    }
}