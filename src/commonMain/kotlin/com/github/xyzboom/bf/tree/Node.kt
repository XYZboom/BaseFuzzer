package com.github.xyzboom.bf.tree

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@Serializable
open class Node @JvmOverloads constructor(
    open val name: String,
    open val children: MutableList<Node> = mutableListOf(),
    open val parent: Node? = null,
    open val ref: Node? = null
)