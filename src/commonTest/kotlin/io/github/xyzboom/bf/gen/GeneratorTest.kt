package io.github.xyzboom.bf.gen

import io.github.xyzboom.bf.def.Parser
import io.github.xyzboom.bf.def.Reference
import io.github.xyzboom.bf.tree.INode
import io.github.xyzboom.bf.tree.NamedTreeNode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class GeneratorTest {
    @Test
    fun generateWillOnlyCallChooseRef() {
        val strategy = object : ILeafIllegalStrategy, IndexIllegalStrategy {
            override fun chooseReference(
                reference: Reference,
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