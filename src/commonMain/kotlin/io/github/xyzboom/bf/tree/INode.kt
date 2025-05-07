package io.github.xyzboom.bf.tree

interface INode {
    fun <D, R> accept(visitor: IVisitor<D, R>, data: D): R {
        return visitor.visitNode(this, data)
    }
}