package com.github.xyzboom.bf.ksp

import com.github.xyzboom.bf.def.*
import com.github.xyzboom.bf.tree.*
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.Closeable
import java.io.OutputStreamWriter
import java.io.PrintWriter

class DefinitionProcessor(
    private val codeGen: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    private var invoked = false

    /**
     * cache for uppercase first char of all names.
     */
    private val nameCache = mutableMapOf<String, String>()

    private fun String.uppercaseFirstChar(): String {
        return nameCache.getOrPut(this) { replaceFirstChar { it.uppercaseChar() } }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) {
            return emptyList()
        }
        invoked = true
        val definitionDecls = resolver.getSymbolsWithAnnotation(DefinitionDecl::class.qualifiedName!!)
        logger.info("found ${definitionDecls.count()} definitions")
        for (defDecl in definitionDecls) {
            handleDef(defDecl)
        }
        return emptyList()
    }

    private val String.nameForParent
        get(): String {
            return "I${uppercaseFirstChar()}Parent"
        }

    private val String.nameForNode
        get(): String {
            return "I${uppercaseFirstChar()}Node"
        }

    private val String.nameForChild
        get(): String {
            return "I${uppercaseFirstChar()}Child"
        }

    @OptIn(KspExperimental::class)
    private fun handleDef(defDecl: KSAnnotated) {
        if (defDecl !is KSPropertyDeclaration) {
            logger.error("${DefinitionDecl::class.simpleName} should only be used on a const top-level property!", defDecl)
            return
        }
        val containingFile = defDecl.containingFile ?: return
        val parent = defDecl.parent
        if (parent != null && parent !is KSFile) {
            logger.error("The property has ${DefinitionDecl::class.simpleName} should be const top-level String!", defDecl)
            return
        }
        if (Modifier.CONST !in defDecl.modifiers) {
            logger.error("The property has ${DefinitionDecl::class.simpleName} should be const top-level String!", defDecl)
            return
        }
        val type = defDecl.type.resolve().declaration.qualifiedName
        if (type?.asString() != "kotlin.String") {
            logger.error("The property has ${DefinitionDecl::class.simpleName} should be const top-level String!", defDecl)
            return
        }
        val anno = defDecl.getAnnotationsByType(DefinitionDecl::class).single()
        val def = Parser().parseDefinition(anno.defValue)
        val packagePrefix = defDecl.packageName.asString()
        val packageName = if (packagePrefix.isNotEmpty()) "${packagePrefix}.${NAME_GENERATED}" else NAME_GENERATED
        val declValName = defDecl.simpleName.asString()
        newFile(containingFile, packageName, "${declValName}Nodes") {
            writeClassHead(packageName)
            for ((name, stat) in def.statementsMap) {
                newClassForStatement(name, stat.contents, def.parentMap[name] ?: emptySet())
            }
        }
    }

    private inline fun newFile(
        containingFile: KSFile,
        packageName: String,
        name: String,
        block: PrintWriterWrapper.() -> Unit
    ) {
        val stream = codeGen.createNewFile(
            Dependencies(false, containingFile),
            packageName,
            name
        )
        PrintWriterWrapper(PrintWriter(OutputStreamWriter(stream))).use { writer ->
            writer.block()
        }
    }

    private fun PrintWriterWrapper.newClassForStatement(
        name: String,
        reference: List<ReferenceList>,
        parents: Set<String>
    ) {
        +"interface ${name.nameForNode} : ${INode::class.simpleName}"
        if (reference.isNotEmpty()) {
            // extends Parent interfaces
            +", ${ITreeParent::class.simpleName}, "
            +(", " join reference.flatMap {
                it.references.map { it1 -> it1.name.nameForParent }
            }.toSet())
        }
        if (parents.isNotEmpty()) {
            +", ${ITreeChild::class.simpleName}, "
            // current node is a child of parent
            +(", " join parents.map { it.nameForChild })
        }
        +!" {"
        +!"}\n"

        if (reference.isNotEmpty()) {
            // child node will extend I{current node name}Child
            +!"sealed interface ${name.nameForChild} : ${INode::class.simpleName}\n"
        }

        if (parents.isNotEmpty()) {
            +!"sealed interface ${name.nameForParent} : ${INode::class.simpleName}"
        }
    }

    private fun PrintWriterWrapper.writeClassHead(packageName: String) {
        +!"@file:Suppress(\"unused\", \"ClassName\")"
        +!""
        +!"package $packageName"
        +!""
        +!"import ${INode::class.qualifiedName}"
        +!"import ${ITreeParent::class.qualifiedName}"
        +!"import ${ITreeChild::class.qualifiedName}"
        +!""
    }

    companion object {
        const val NAME_GENERATED = "generated"
    }

    @JvmInline
    @Suppress("NOTHING_TO_INLINE")
    private value class PrintWriterWrapper(val printer: PrintWriter) : Closeable by printer {
        @JvmInline
        value class StringAndNewLine(val str: String)

        inline operator fun String.not(): StringAndNewLine {
            return StringAndNewLine(this)
        }

        inline operator fun String.unaryPlus() {
            printer.print(this)
        }

        inline operator fun StringAndNewLine.unaryPlus() {
            printer.println(this.str)
        }

        inline infix fun <T> String.join(iter: Iterable<T>): String {
            return iter.joinToString(this)
        }
    }
}

