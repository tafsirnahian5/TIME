package com.example.timeapp.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two routes with the same path register two screens under one key, and the later one wins —
 * `WorkdaySettings` and `Workday` were both "settings/workday", so Settings › Workday & calendar
 * opened My Workday and work hours could never be changed after setup.
 */
class RoutesTest {
    @Test
    fun everyRouteHasItsOwnPath() {
        val paths = Routes::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { it.name to (it.get(null) as String).substringBefore('?') }
        val clashes = paths.entries.groupBy { it.value }.filterValues { it.size > 1 }
        assertTrue("Routes sharing a path: $clashes", clashes.isEmpty())
        assertTrue("found no routes — the reflection is wrong", paths.size > 20)
    }
}
