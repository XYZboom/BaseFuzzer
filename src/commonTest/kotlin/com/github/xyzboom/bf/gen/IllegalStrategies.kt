package com.github.xyzboom.bf.gen

import com.github.xyzboom.bf.def.Reference
import com.github.xyzboom.bf.def.Statement
import com.github.xyzboom.bf.gen.strategy.IGenerateStrategy
import com.github.xyzboom.bf.tree.INode

interface ILeafIllegalStrategy : IGenerateStrategy {
    override fun chooseLeaf(statement: Statement, context: INode?): Boolean {
        throw IllegalStateException("chooseLeaf should never be called!")
    }
}

interface IRefIllegalStrategy: IGenerateStrategy {
    override fun chooseReference(statement: Statement, context: INode, generatedNode: Map<String, List<INode>>): INode? {
        throw IllegalStateException("chooseReference should never be called!")
    }
}

interface IndexIllegalStrategy: IGenerateStrategy {
    override fun chooseIndex(statement: Statement, context: INode?): Int {
        throw IllegalStateException("chooseIndex should never be called!")
    }
}

interface ISizeIllegalStrategy: IGenerateStrategy {
    override fun chooseSize(reference: Reference, context: INode): Int {
        throw IllegalStateException("chooseSize should never be called!")
    }
}
