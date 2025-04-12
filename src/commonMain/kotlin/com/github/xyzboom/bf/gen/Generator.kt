package com.github.xyzboom.bf.gen

import com.github.xyzboom.bf.def.Definition
import com.github.xyzboom.bf.def.RefType.*
import com.github.xyzboom.bf.gen.strategy.IGenerateStrategy
import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.bf.tree.NamedTreeNode
import com.github.xyzboom.bf.tree.RefNode
import kotlin.jvm.JvmOverloads

open class Generator(
    val def: Definition,
    val strategy: IGenerateStrategy
) {
    val generatedNode = mutableMapOf<String, MutableList<INode>>()

    open fun INode.addChild(child: INode) {
        (this as NamedTreeNode).children.add(child)
    }

    @JvmOverloads
    open fun generateNode(name: String, context: INode?, ref: INode? = null): INode {
        if (ref != null) {
            return RefNode(ref)
        }
        // NamedTreeNode by default, unsafe cast is actually safe
        return NamedTreeNode(name, mutableListOf(), context as NamedTreeNode?)
    }

    fun generate(name: String): INode {
        generatedNode.clear()
        return generate(name, null)
    }

    open fun generate(name: String, parent: INode?): INode {
        val statement = def.statementsMap[name] ?: throw IllegalArgumentException("No statement named: $name")
        val node = generateNode(statement.name, parent)
        val contents = statement.contents
        val content = when (contents.size) {
            0 -> return node // leaf handled here
            1 -> contents.single()
            else -> {
                val chooseIndex = strategy.chooseIndex(statement, parent)
                contents[chooseIndex]
            }
        }
        for (ref in content.references) {

            fun generateChild() {
                val refNode = strategy.chooseReference(statement, node, generatedNode)
                val child = if (refNode != null) {
                    generateNode(ref.name, null, refNode)
                } else {
                    generate(ref.name, node)
                }
                node.addChild(child)
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
        generatedNode.getOrPut(statement.name) { mutableListOf() }.add(node)
        return node
    }
}