package com.github.xyzboom.bf.def

import com.charleskorn.kaml.Yaml
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ParserKtTest {
    @Test
    fun parseDefinition0() {
        val definition = parseDefinition("a: b*;~leaf: b;")
        val definitionString = Yaml.default.encodeToString(Definition.serializer(), definition)
        definitionString shouldBe """statementsMap:
  "a":
    name: "a"
    contents:
    - - name: "b"
        type: "ZERO_OR_MORE"
leaves:
  "b":
    name: "b"
    contents: []"""
    }
}