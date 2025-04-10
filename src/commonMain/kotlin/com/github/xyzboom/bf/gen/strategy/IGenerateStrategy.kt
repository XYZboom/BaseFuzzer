package com.github.xyzboom.bf.gen.strategy

import com.github.xyzboom.bf.def.Reference
import com.github.xyzboom.bf.def.Statement
import com.github.xyzboom.bf.tree.Node

interface IGenerateStrategy {
    /**
     * This method is called when a [Statement] is both a leaf and a non-leaf.
     *
     * @return true if you want to generate a leaf node now.
     */
    fun chooseLeaf(statement: Statement, context: Node?): Boolean
    /**
     * @return null if you want to generate a new node
     */
    fun chooseReference(statement: Statement, context: Node, generatedNode: Map<String, List<Node>>): Node?
    fun chooseIndex(statement: Statement, context: Node?): Int
    fun chooseSize(reference: Reference, context: Node): Int
}