package com.ataiva.eden.testing.extensions

import io.kotest.matchers.shouldBe

/**
 * Custom extensions to provide Kotest-like matchers for collections
 * These are needed because the standard Kotest matchers might not be available
 * or might have conflicts in the multiplatform setup
 */

/**
 * Asserts that a Map is empty
 */
fun <K, V> Map<K, V>.shouldBeEmpty() {
    this.isEmpty() shouldBe true
}

/**
 * Asserts that a List is empty
 */
fun <T> List<T>.shouldBeEmpty() {
    this.isEmpty() shouldBe true
}

/**
 * Asserts that a Collection is empty
 */
fun <T> Collection<T>.shouldBeEmpty() {
    this.isEmpty() shouldBe true
}

/**
 * Asserts that an Array is empty
 */
fun <T> Array<T>.shouldBeEmpty() {
    this.isEmpty() shouldBe true
}

/**
 * Asserts that a Collection has the expected size
 */
infix fun <T> Collection<T>.shouldHaveSize(size: Int) {
    this.size shouldBe size
}

/**
 * Asserts that a Map has the expected size
 */
infix fun <K, V> Map<K, V>.shouldHaveSize(size: Int) {
    this.size shouldBe size
}

/**
 * Asserts that an Array has the expected size
 */
infix fun <T> Array<T>.shouldHaveSize(size: Int) {
    this.size shouldBe size
}