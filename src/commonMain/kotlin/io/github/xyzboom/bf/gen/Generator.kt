package io.github.xyzboom.bf.gen

import io.github.xyzboom.bf.def.Definition
import io.github.xyzboom.bf.def.RefType.*
import io.github.xyzboom.bf.gen.strategy.IGenerateStrategy
import io.github.xyzboom.bf.tree.INode
import io.github.xyzboom.bf.tree.NamedTreeNode
import io.github.xyzboom.bf.tree.RefNode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.jvm.JvmOverloads

@Deprecated("Deprecated", replaceWith = ReplaceWith("AbstractGenerator"))
open class Generator(
    val def: Definition,
    val strategy: IGenerateStrategy
) {
    private val logger = KotlinLogging.logger {}
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
        return NamedTreeNode(name, mutableListOf()).apply {
            if (context != null) {
                parent = context
            }
        }
    }

    fun generate(name: String): INode {
        generatedNode.clear()
        return generate(name, null)
    }

    open fun generate(name: String, parent: INode?): INode {
        logger.trace { "generate name: $name, parentClass: ${if (parent != null) parent::class else null}" }
        val statement = def.statementsMap[name] ?: throw IllegalArgumentException("No statement named: $name")
        val chooseLeaf = strategy.chooseLeaf(statement, parent)
        val node = generateNode(name, parent)
        val contents = statement.contents
        val content = when {
            contents.isEmpty() || chooseLeaf -> return node // leaf handled here
            contents.size == 1 -> contents.single()
            else -> {
                val chooseIndex = strategy.chooseIndex(statement, parent)
                contents[chooseIndex]
            }
        }
        for (ref in content.references) {

            fun generateChild() {
                val refNode = strategy.chooseReference(ref, node, generatedNode)
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
        generatedNode.getOrPut(name) { mutableListOf() }.add(node)
        return node
    }
}