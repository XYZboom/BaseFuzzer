package com.github.xyzboom.bf.parser

import com.github.xyzboom.bf.def.LeafElement.Companion.BUILT_IN_LEAF_NAME
import com.github.xyzboom.bf.def.RefType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ParserKtTest: FunSpec({
    test("parseYamlElement0") {
        val content = """a:
  - b*
~leaf:
  b"""
        val elementMap = parseYamlElement(content)
        elementMap.size shouldBe 2
        elementMap.keys shouldContainExactly setOf("a", BUILT_IN_LEAF_NAME)
        val elementA = elementMap["a"]!!
        elementA.name shouldBe "a"
        elementA.references.size shouldBe 1
        elementA.references.single().name shouldBe "b"
        elementA.references.single().type shouldBe RefType.ZERO_OR_MORE
        val leaf = elementMap[BUILT_IN_LEAF_NAME]!!
        leaf.name shouldBe BUILT_IN_LEAF_NAME
        leaf.references.size shouldBe 1
        leaf.references.single().name shouldBe "b"
        leaf.references.single().type shouldBe RefType.NON_NULL
    }
})
