package io.github.xyzboom.bf.ksp

import io.github.xyzboom.bf.ksp.generated.IProgNode
import io.github.xyzboom.bf.ksp.generated.ITopDeclNode
import io.github.xyzboom.bf.tree.INode
import io.github.xyzboom.bf.tree.OneOrMore

class IrProgram : IProgNode {
    override var topDeclChild: OneOrMore<ITopDeclNode> = OneOrMore()
    override val children: MutableList<INode> = mutableListOf()
}