package io.github.xyzboom.bf.def

import com.github.xyzboom.bf.generated.BaseFuzzerGrammar
import com.github.xyzboom.bf.generated.BaseFuzzerLexer
import org.antlr.v4.kotlinruntime.BailErrorStrategy
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.IntStream

class Parser {
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
        val refAndCtx = mutableListOf<Pair<Reference, BaseFuzzerGrammar.RefContext>>()
        for (statementCtx in statementsCtx) {
            val statementContentsCtx = statementCtx.statementContentList()?.statementContent()
            val statementName = statementCtx.id().text
            val referenceLists = mutableListOf<ReferenceList>()
            if (statementContentsCtx != null) {
                for (statementContentCtx in statementContentsCtx) {
                    val references = statementContentCtx.ref().map {
                        val ref = it.toRef()
                        refAndCtx.add(ref to it)
                        ref
                    }
                    referenceLists.add(ReferenceList(references))
                }
            }
            resultMap[statementName] = Statement(statementName, referenceLists)
        }
        checkReference(result, refAndCtx)
        refAndCtx.clear()
        return result
    }

    private fun checkReference(def: Definition, refAndCtx: List<Pair<Reference, BaseFuzzerGrammar.RefContext>>) {
        for ((ref, refCtx) in refAndCtx) {
            require(ref.name in def.statementsMap) {
                val startLine = refCtx.start?.line ?: -1
                val startCol = refCtx.start?.charPositionInLine ?: -1
                "Undefined Reference ${ref.name} at $startLine:$startCol"
            }
        }
    }

    private fun BaseFuzzerGrammar.RefContext.toRef(): Reference {
        val name = id().text
        val refType = RefType.fromTypeString(refType().text)
        return Reference(name, refType)
    }
}

