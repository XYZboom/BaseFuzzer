package com.github.xyzboom.bf.tree

interface ITreeNode : ITreeChild, ITreeParent {
    override val children: MutableList<INode>
}