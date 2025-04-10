package com.github.xyzboom.bf.gen

import com.github.xyzboom.bf.def.Reference
import com.github.xyzboom.bf.def.Statement
import com.github.xyzboom.bf.gen.strategy.IGenerateStrategy
import com.github.xyzboom.bf.tree.Node

interface ILeafIllegalStrategy : IGenerateStrategy {
    override fun chooseLeaf(statement: Statement, context: Node?): Boolean {
        throw IllegalStateException("chooseLeaf should never be called!")
    }
}

interface IRefIllegalStrategy: IGenerateStrategy {
    override fun chooseReference(statement: Statement, context: Node, generatedNode: Map<String, List<Node>>): Node? {
        throw IllegalStateException("chooseReference should never be called!")
    }
}

interface IndexIllegalStrategy: IGenerateStrategy {
    override fun chooseIndex(statement: Statement, context: Node?): Int {
        throw IllegalStateException("chooseIndex should never be called!")
    }
}

interface ISizeIllegalStrategy: IGenerateStrategy {
    override fun chooseSize(reference: Reference, context: Node): Int {
        throw IllegalStateException("chooseSize should never be called!")
    }
}
