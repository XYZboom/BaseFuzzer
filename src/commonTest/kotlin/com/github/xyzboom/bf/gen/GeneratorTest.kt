package com.github.xyzboom.bf.gen

import com.github.xyzboom.bf.def.Reference
import com.github.xyzboom.bf.def.Statement
import com.github.xyzboom.bf.def.parseDefinition
import com.github.xyzboom.bf.tree.Node
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class GeneratorTest {
    @Test
    fun generateWillOnlyCallChooseRef() {
        val strategy = object : ILeafIllegalStrategy, IndexIllegalStrategy {
            override fun chooseReference(
                statement: Statement,
                context: Node,
                generatedNode: Map<String, List<Node>>
            ): Node? {
                return null
            }

            private var calledChooseSize = false

            override fun chooseSize(reference: Reference, context: Node): Int {
                if (calledChooseSize) {
                    throw IllegalStateException("chooseSize should only be called once!")
                }
                calledChooseSize = true
                return 1
            }

        }
        val generator = Generator(parseDefinition("a: b*;~leaf: b;"), strategy)
        val root = generator.generate("a")
        root.name shouldBe "a"
        root.ref shouldBe null
        val nodeB = root.children.single()
        nodeB.name shouldBe "b"
        nodeB.ref shouldBe null
        nodeB.children.shouldBeEmpty()
    }
}