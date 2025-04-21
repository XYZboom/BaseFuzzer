package com.github.xyzboom.bf.tree

interface ITreeParent {
    val children: MutableList<INode>
    fun addChild(node: INode)
    fun <D> acceptChildren(visitor: IVisitor<D, Unit>, data: D) {
        for (child in children) {
            child.accept(visitor, data)
        }
    }
}