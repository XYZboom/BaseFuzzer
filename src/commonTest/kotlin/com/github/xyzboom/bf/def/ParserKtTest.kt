package com.github.xyzboom.bf.def

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ParserKtTest {
    @Test
    fun parseDefinition0() {
        val parser = Parser()
        val definition = parser.parseDefinition("a: b*;b;")
        val definitionString = Yaml.default.encodeToString(Definition.serializer(), definition)
        definitionString shouldBe """statementsMap:
  "a":
    name: "a"
    contents:
    - - name: "b"
        type: "ZERO_OR_MORE"
  "b":
    name: "b"
    contents: []"""
    }

    @Test
    fun parseDefinitionShouldFailOnUndefinedRef() {
        val e = shouldThrow<IllegalArgumentException> {
            val parser = Parser()
            parser.parseDefinition("a: b*;")
        }
        e.message shouldBe "Undefined Reference b at 1:3"
    }
}