package com.github.xyzboom.bf.tree

import kotlin.jvm.JvmWildcard

interface ITreeParent {
    val children: MutableList<out @JvmWildcard INode>
}