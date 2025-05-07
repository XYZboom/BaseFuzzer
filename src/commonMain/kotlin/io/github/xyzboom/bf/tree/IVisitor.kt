package io.github.xyzboom.bf.tree

interface IVisitor<D, R> {
    fun visitNode(node: INode, data: D): R
}