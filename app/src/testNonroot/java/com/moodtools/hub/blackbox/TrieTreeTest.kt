package com.moodtools.hub.blackbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.niunaijun.blackbox.utils.TrieTree

class TrieTreeTest {
    @Test
    fun `search returns the longest matching path prefix`() {
        val tree = TrieTree().apply {
            add("/data/data/example")
            add("/data/data/example/lib")
        }

        assertEquals("/data/data/example/lib", tree.search("/data/data/example/lib/arm64/libgame.so"))
        assertEquals("/data/data/example", tree.search("/data/data/example/files/save.dat"))
    }

    @Test
    fun `search does not cross a path segment boundary`() {
        val tree = TrieTree().apply { add("/data/data/example") }

        assertNull(tree.search("/data/data/example.other/files/save.dat"))
    }
}
