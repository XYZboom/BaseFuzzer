package io.github.xyzboom.bf.ksp

import io.github.xyzboom.bf.ksp.generated.IProgNode
import io.github.xyzboom.bf.ksp.generated.ITopDeclNode
import io.github.xyzboom.bf.tree.INode

class IrProgram : IProgNode {
    override var topDeclChildren: MutableList<ITopDeclNode> = mutableListOf()
    override val children: MutableList<INode> = mutableListOf()
}