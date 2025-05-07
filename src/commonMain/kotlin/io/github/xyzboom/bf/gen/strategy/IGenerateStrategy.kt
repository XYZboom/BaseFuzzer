package io.github.xyzboom.bf.gen.strategy

import io.github.xyzboom.bf.def.Reference
import io.github.xyzboom.bf.def.Statement
import io.github.xyzboom.bf.tree.INode
import kotlin.jvm.JvmWildcard

interface IGenerateStrategy {
    /**
     * This method is called when a [Statement] is both a leaf and a non-leaf.
     *
     * @return true if you want to generate a leaf node now.
     */
    fun chooseLeaf(statement: Statement, context: INode?): Boolean
    /**
     * @return null if you want to generate a new node
     */
    fun chooseReference(reference: Reference, context: INode, generatedNode: Map<String, List<@JvmWildcard INode>>): INode?
    fun chooseIndex(statement: Statement, context: INode?): Int
    fun chooseSize(reference: Reference, context: INode): Int
}