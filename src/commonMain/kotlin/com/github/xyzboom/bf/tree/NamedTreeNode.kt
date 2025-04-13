package com.github.xyzboom.bf.tree

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@Serializable
open class NamedTreeNode @JvmOverloads constructor(
    override val name: String,
    override val children: MutableList<INode> = mutableListOf(),
    override val parent: INode? = null,
): INode, IName, ITreeNode