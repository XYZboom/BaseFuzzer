package com.github.xyzboom.bf.tree

import kotlin.jvm.JvmWildcard

interface ITreeNode : INode {
    val children: MutableList<out @JvmWildcard INode>
    val parent: INode?
}