package com.github.xyzboom.bf.tree

interface ITreeParent {
    val children: MutableList<INode>
//    val children: List<INode>
//    fun addChild(node: INode): Boolean
    fun <D> acceptChildren(visitor: IVisitor<D, Unit>, data: D) {
        for (child in children) {
            child.accept(visitor, data)
        }
    }
}