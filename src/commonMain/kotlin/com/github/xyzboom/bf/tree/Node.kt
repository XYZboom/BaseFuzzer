package com.github.xyzboom.bf.tree

import kotlin.jvm.JvmOverloads

open class Node @JvmOverloads constructor(
    open val name: String,
    open val children: MutableList<Node>,
    open val parent: Node? = null
)