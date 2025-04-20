package com.github.xyzboom.bf.tree

import kotlin.jvm.JvmWildcard

interface ITreeNode : ITreeChild, ITreeParent {
    override val children: MutableList<out @JvmWildcard INode>
}