package com.github.xyzboom.bf.def

import com.github.xyzboom.bf.def.Definition.BuiltIn.LEAF_NAME
import com.github.xyzboom.bf.generated.BaseFuzzerGrammar
import com.github.xyzboom.bf.generated.BaseFuzzerLexer
import org.antlr.v4.kotlinruntime.BailErrorStrategy
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.IntStream

fun parseDefinition(
    definitionString: String,
    sourceName: String = IntStream.UNKNOWN_SOURCE_NAME
): Definition {
    val charStream = CharStreams.fromString(definitionString, sourceName)
    val lexer = BaseFuzzerLexer(charStream)
    val grammar = BaseFuzzerGrammar(CommonTokenStream(lexer))
    grammar.errorHandler = BailErrorStrategy()
    val definitionCtx = grammar.definition()
    val statementsCtx = definitionCtx.statement()
    val resultMap = mutableMapOf<String, Statement>()
    val result = Definition(resultMap)
    val leaves = result.leaves
    for (statementCtx in statementsCtx) {
        val statementContentsCtx = statementCtx.statementContentList().statementContent()
        // TODO handle built-in names here
        val statementName = statementCtx.id().text
        if (statementName == LEAF_NAME) {
            for (statementContentCtx in statementContentsCtx) {
                val referencesCtx = statementContentCtx.ref()
                require(referencesCtx.size == 1) {
                    "Each leaf element should have only one reference!"
                }
                val refCtx = referencesCtx.single()
                require(RefType.fromTypeString(refCtx.refType().text) == RefType.NON_NULL) {
                    "Reference of a leaf should be non-null"
                }
                val refName = refCtx.normalId().text
                leaves[refName] = Statement(refName, emptyList())
            }
            continue
        }
        val referenceLists = mutableListOf<ReferenceList>()
        for (statementContentCtx in statementContentsCtx) {
            val references = statementContentCtx.ref().map { it.toRef() }
            referenceLists.add(ReferenceList(references))
        }
        resultMap[statementName] = Statement(statementName, referenceLists)
    }
    // TODO check reference here
    return result
}

fun BaseFuzzerGrammar.RefContext.toRef(): Reference {
    val name = normalId().ID().text
    val refType = RefType.fromTypeString(refType().text)
    return Reference(name, refType)
}