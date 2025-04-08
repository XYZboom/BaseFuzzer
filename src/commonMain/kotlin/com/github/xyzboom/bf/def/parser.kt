package com.github.xyzboom.bf.def

import com.github.xyzboom.bf.generated.BaseFuzzerGrammar
import com.github.xyzboom.bf.generated.BaseFuzzerLexer
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
    val definitionCtx = grammar.definition()
    val statementsCtx = definitionCtx.statement()
    val result = mutableMapOf<String, Statement>()
    for (statementCtx in statementsCtx) {
        val statementContentsCtx = statementCtx.statementContentList().statementContent()
        // TODO handle built-in names here
        val statementName = statementCtx.id().text
        val referenceLists = mutableListOf<ReferenceList>()
        for (statementContentCtx in statementContentsCtx) {
            val references = statementContentCtx.ref().map { it.toRef() }
            referenceLists.add(ReferenceList(references))
        }
        result[statementName] = Statement(statementName, referenceLists)
    }
    // TODO check reference here
    return Definition(result)
}

fun BaseFuzzerGrammar.RefContext.toRef(): Reference {
    val name = normalId().ID().text
    val refType = RefType.fromTypeString(refType().text)
    return Reference(name, refType)
}