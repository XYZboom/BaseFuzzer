package com.github.xyzboom.bf.gen

import com.github.xyzboom.bf.def.Parser
import com.github.xyzboom.bf.def.Reference
import com.github.xyzboom.bf.def.Statement
import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.bf.tree.NamedTreeNode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class GeneratorTest {
    @Test
    fun generateWillOnlyCallChooseRef() {
        val strategy = object : ILeafIllegalStrategy, IndexIllegalStrategy {
            override fun chooseReference(
                statement: Statement,
                context: INode,
                generatedNode: Map<String, List<INode>>
            ): INode? {
                return null
            }

            private var calledChooseSize = false

            override fun chooseSize(reference: Reference, context: INode): Int {
                if (calledChooseSize) {
                    throw IllegalStateException("chooseSize should only be called once!")
                }
                calledChooseSize = true
                return 1
            }

        }
        val generator = Generator(Parser().parseDefinition("a: b*;b;"), strategy)
        val root = generator.generate("a") as NamedTreeNode
        root.name shouldBe "a"
        val nodeB = root.children.single() as NamedTreeNode
        nodeB.name shouldBe "b"
        nodeB.children.shouldBeEmpty()
    }
}