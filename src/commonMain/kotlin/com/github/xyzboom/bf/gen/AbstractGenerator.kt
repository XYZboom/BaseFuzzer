package com.github.xyzboom.bf.gen

import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.bf.tree.ITreeChild
import kotlin.random.Random

@Suppress("unused")
abstract class AbstractGenerator() {
    var random: Random = Random

    val INode.depth: Int
        get() = when (this) {
            is ITreeChild -> parent.depth + 1
            else -> 1
        }
}