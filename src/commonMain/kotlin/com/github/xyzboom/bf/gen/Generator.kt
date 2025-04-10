package com.github.xyzboom.bf.gen

import com.github.xyzboom.bf.def.Definition
import com.github.xyzboom.bf.def.RefType.*
import com.github.xyzboom.bf.gen.strategy.IGenerateStrategy
import com.github.xyzboom.bf.tree.Node

open class Generator(
    val def: Definition,
    val strategy: IGenerateStrategy
) {
    val generatedNode = mutableMapOf<String, MutableList<Node>>()

    fun generate(name: String): Node {
        generatedNode.clear()
        return generate(name, null)
    }

    open fun generate(name: String, parent: Node?): Node {
        val leaf = def.leaves[name]
        val statement = def.statementsMap[name]
        val usingStatement = if (leaf != null && (statement == null || strategy.chooseLeaf(statement, parent))) {
            leaf
        } else {
            statement ?: throw IllegalArgumentException("No such statement: $name in definition")
        }
        val node = Node(usingStatement.name, mutableListOf(), parent)
        val contents = usingStatement.contents
        val content = when (contents.size) {
            0 -> return node // leaf handled here
            1 -> contents.single()
            else -> {
                val chooseIndex = strategy.chooseIndex(usingStatement, parent)
                contents[chooseIndex]
            }
        }
        for (ref in content.references) {

            fun generateChild() {
                val refNode = strategy.chooseReference(usingStatement, node, generatedNode)
                val child = if (refNode != null) {
                    Node(ref.name, ref = refNode)
                } else {
                    generate(ref.name, node)
                }
                node.children.add(child)
            }

            when (ref.type) {
                NON_NULL -> generateChild()

                else -> {
                    val size = strategy.chooseSize(ref, node)
                    repeat(size) {
                        generateChild()
                    }
                }
            }
        }
        generatedNode.getOrPut(usingStatement.name) { mutableListOf() }.add(node)
        return node
    }
}