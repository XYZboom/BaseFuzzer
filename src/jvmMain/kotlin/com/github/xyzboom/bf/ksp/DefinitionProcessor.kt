package com.github.xyzboom.bf.ksp

import com.github.xyzboom.bf.def.*
import com.github.xyzboom.bf.def.RefType.*
import com.github.xyzboom.bf.gen.AbstractGenerator
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
    private val uppercaseNameCache = mutableMapOf<String, String>()
    private val lowercaseNameCache = mutableMapOf<String, String>()

    private fun String.uppercaseFirstChar(): String {
        return uppercaseNameCache.getOrPut(this) { replaceFirstChar { it.uppercaseChar() } }
    }

    private fun String.lowercaseFirstChar(): String {
        return lowercaseNameCache.getOrPut(this) { replaceFirstChar { it.lowercaseChar() } }
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

    //<editor-fold desc="Names">
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

    private val String.nameForDefaultNode
        get(): String {
            return "Default${uppercaseFirstChar()}Node"
        }

    private val String.nameForNewNodeFunction
        get() : String {
            return "new${uppercaseFirstChar()}"
        }

    private val String.nameForGenFunction
        get(): String {
            return "generate${uppercaseFirstChar()}"
        }

    private fun String.nameForChildProperty(multi: Boolean): String {
        return "${lowercaseFirstChar()}Child${if (multi) "ren" else ""}"
    }

    private val String.nameForChooseIndexFunction
        get(): String {
            return "choose${uppercaseFirstChar()}Index"
        }

    private val String.nameForChooseReferenceFunction
        get(): String {
            return "choose${uppercaseFirstChar()}Reference"
        }

    private val String.nameForGeneratedNodesProperty
        get(): String {
            return "generated${uppercaseFirstChar()}Nodes"
        }

    private val String.nameForVisitFunction
        get(): String {
            return "visit${uppercaseFirstChar()}Node"
        }

    private fun nameForChooseSizeFunction(parentName: String, childName: String): String {
        return "choose${childName.uppercaseFirstChar()}SizeWhenParentIs${parentName.uppercaseFirstChar()}"
    }

    private val String.nameForChoiceEnum
        get(): String {
            return uppercaseFirstChar()
        }

    @JvmInline
    private value class DeclValName(val value: String)

    private val DeclValName.visitorClassName
        get(): String {
            return "I${value.uppercaseFirstChar()}Visitor"
        }

    private val DeclValName.topDownVisitorClassName
        get(): String {
            return "I${value.uppercaseFirstChar()}TopDownVisitor"
        }

    private val DeclValName.generatorClassName
        get(): String {
            return "${value.uppercaseFirstChar()}Generator"
        }

    //</editor-fold>

    @OptIn(KspExperimental::class)
    private fun handleDef(defDecl: KSAnnotated) {
        if (defDecl !is KSPropertyDeclaration) {
            logger.error(
                "${DefinitionDecl::class.simpleName} should only be used on a const top-level property!",
                defDecl
            )
            return
        }
        val containingFile = defDecl.containingFile ?: return
        val parent = defDecl.parent
        if (parent != null && parent !is KSFile) {
            logger.error(
                "The property has ${DefinitionDecl::class.simpleName} should be const top-level String!",
                defDecl
            )
            return
        }
        if (Modifier.CONST !in defDecl.modifiers) {
            logger.error(
                "The property has ${DefinitionDecl::class.simpleName} should be const top-level String!",
                defDecl
            )
            return
        }
        val type = defDecl.type.resolve().declaration.qualifiedName
        if (type?.asString() != "kotlin.String") {
            logger.error(
                "The property has ${DefinitionDecl::class.simpleName} should be const top-level String!",
                defDecl
            )
            return
        }
        val anno = defDecl.getAnnotationsByType(DefinitionDecl::class).single()
        val def = Parser().parseDefinition(anno.defValue)
        val packagePrefix = defDecl.packageName.asString()
        val packageName = if (packagePrefix.isNotEmpty()) "${packagePrefix}.${NAME_GENERATED}" else NAME_GENERATED
        val declValName = DeclValName(defDecl.simpleName.asString())
        newFile(containingFile, packageName, "${declValName.value}Nodes") {
            writeClassHead(packageName)
            for ((name, stat) in def.statementsMap) {
                newClassForStatement(declValName, name, stat.contents, def.parentMap[name] ?: emptySet())
            }
        }
        createGeneratorClass(containingFile, packageName, declValName, def)
        createVisitorClass(containingFile, packageName, declValName, def)
    }

    private fun createVisitorClass(
        containingFile: KSFile,
        packageName: String,
        declValName: DeclValName,
        def: Definition
    ) {
        val visitorName = declValName.visitorClassName
        newFile(containingFile, packageName, visitorName) {
            writeClassHead(packageName)
            +!"interface $visitorName<D, R> : ${IVisitor::class.simpleName!!}<D, R> {"
            indentCount++
            for ((name, _) in def.statementsMap) {
                +!"fun ${name.nameForVisitFunction}(node: ${name.nameForNode}, data: D): R {"
                +!"    return ${IVisitor<*, *>::visitNode.name}(node, data)"
                +!"}"
            }
            indentCount--
            +!"}"
        }
        val topDownVisitorName = declValName.topDownVisitorClassName
        newFile(containingFile, packageName, topDownVisitorName) {
            writeClassHead(packageName)
            +!"interface $topDownVisitorName<D> : $visitorName<D, Unit> {"
            indentCount++
            +!"override fun visitNode(node: ${INode::class.simpleName!!}, data: D) {"
            indentCount++
            +!"if (node is ${ITreeParent::class.simpleName!!}) node.acceptChildren(this, data)"
            indentCount--
            +!"}"
            indentCount--
            +!"}"
        }
    }

    private fun createGeneratorClass(
        containingFile: KSFile,
        packageName: String,
        declValName: DeclValName,
        def: Definition
    ) {
        val generatorName = declValName.generatorClassName
        newFile(containingFile, packageName, generatorName) {
            writeClassHead(packageName)
            +!"open class $generatorName : ${AbstractGenerator::class.simpleName!!}() {"
            indentCount++
            editorFoldOf("generated nodes") {
                for ((name, _) in def.statementsMap) {
                    +!"val ${name.nameForGeneratedNodesProperty}: MutableList<${name.nameForNode}> = mutableListOf()"
                }
                +!"open fun clearGeneratedNodes() {"
                indentCount++
                for ((name, _) in def.statementsMap) {
                    +!"${name.nameForGeneratedNodesProperty}.clear()"
                }
                indentCount--
                +!"}"
            }
            editorFoldOf("choose reference") {
                for ((name, _) in def.statementsMap) {
                    +"open fun ${name.nameForChooseReferenceFunction}("
                    if (def.parentMap[name] != null) {
                        +"parent: ${name.nameForParent}"
                    }
                    +!"): ${IRef::class.simpleName!!}? {"
                    indentCount++
                    +!"if (random.nextBoolean()) return null"
                    +!"return ${RefNode::class.simpleName!!}(${name.nameForGeneratedNodesProperty}.random(random))"
                    indentCount--
                    +!"}"
                }
            }
            editorFoldOf("choose index functions") {
                for ((name, stat) in def.statementsMap) {
                    if (stat.contents.size > 1) {
                        +"open fun ${name.nameForChooseIndexFunction}("
                        if (def.parentMap[name] != null) {
                            +"context: ${name.nameForParent}"
                        }
                        +!"): Int {"
                        indentCount++
                        +!"return ${AbstractGenerator::random.name}.nextInt(${stat.contents.size})"
                        indentCount--
                        +!"}"
                    }
                }
            }
            editorFoldOf("choose size functions") {
                for ((name, stat) in def.statementsMap) {
                    for ((i, refList) in stat.contents.withIndex()) {
                        for (ref in refList) {
                            // NON_NULL always has size 1
                            val refType = ref.type
                            if (refType == NON_NULL) continue
                            +"open fun ${
                                nameForChooseSizeFunction(
                                    "$name${if (stat.contents.size > 1) i else ""}",
                                    ref.name
                                )
                            }"
                            +!"(parent: ${name.nameForNode}): Int {"
                            indentCount++
                            +"return "
                            +!when (refType) {
                                NULLABLE -> "random.nextInt(2)"
                                ONE_OR_MORE -> "random.nextInt(${AbstractGenerator::DEFAULT_MAX_SIZE.name}) + 1"

                                ZERO_OR_MORE -> "random.nextInt(-1, ${AbstractGenerator::DEFAULT_MAX_SIZE.name}) + 1"

                                else -> throw NoWhenBranchMatchedException()
                            }
                            indentCount--
                            +!"}"
                        }
                    }
                }
            }
            editorFoldOf("new node functions") {
                for ((name, _) in def.statementsMap) {
                    +!"open fun ${name.nameForNewNodeFunction}(): ${name.nameForNode} {"
                    +!"    return ${name.nameForDefaultNode}()"
                    +!"}"
                }
            }
            editorFoldOf("generate functions") {
                for ((name, stat) in def.statementsMap) {
                    +"open fun ${name.nameForGenFunction}("
                    val hasParent = def.parentMap[name] != null
                    if (hasParent) {
                        +"parent: ${name.nameForParent}"
                    }
                    +!"): ${INode::class.simpleName} {"
                    indentCount++
                    +"val chooseRef = ${name.nameForChooseReferenceFunction}("
                    if (hasParent) {
                        +"parent"
                    }
                    +!")"
                    +!"if (chooseRef != null) return chooseRef"
                    +!"val result = ${name.nameForNewNodeFunction}()"
                    +!"${name.nameForGeneratedNodesProperty}.add(result)"
                    if (hasParent) {
                        +!"result.${ITreeChild::parent.name} = parent"
                    }

                    fun writeGenChildrenCode(refList: ReferenceList) {
                        for (ref in refList) {
                            when (ref.type) {
                                NON_NULL -> +!"result.addChild(${ref.name.nameForGenFunction}(result))"
                                else -> {
                                    +!"repeat(${nameForChooseSizeFunction(name, ref.name)}(result)) {"
                                    +!"    result.addChild(${ref.name.nameForGenFunction}(result))"
                                    +!"}"
                                }
                            }
                        }
                    }

                    if (stat.contents.size > 1) {
                        +"when (val index = ${name.nameForChooseIndexFunction}("
                        if (hasParent) {
                            +"parent"
                        }
                        +!")) {"
                        indentCount++

                        for ((i, refList) in stat.contents.withIndex()) {
                            +!"$i -> {"
                            indentCount++
                            writeGenChildrenCode(refList)
                            indentCount--
                            +!"}"
                        }
                        +"else -> throw ${IllegalArgumentException::class.qualifiedName!!}(\""
                        +"Index: ${'$'}index returned from ${name.nameForChooseIndexFunction} is illegal."
                        +!"\")"
                        indentCount--
                        +!"}"
                    } else if (stat.contents.size == 1) {
                        writeGenChildrenCode(stat.contents.single())
                    }
                    +!"return result"
                    indentCount--
                    +!"}"
                }
            }
            indentCount--
            +!"}"
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
        declValName: DeclValName,
        name: String,
        reference: List<ReferenceList>,
        parents: Set<String>
    ) {

        fun genContentPropertiesAndAddChildFunction(refList: ReferenceList, impl: Boolean = false) {
            for (ref in refList) {
                if (impl) {
                    +"override "
                    if (ref.type != NULLABLE) {
                        +"lateinit "
                    }
                }
                +"var ${ref.name.nameForChildProperty(ref.type.canBeMulti())}: "
                val nodeName = ref.name.nameForNode
                +when (ref.type) {
                    NON_NULL -> nodeName
                    NULLABLE -> "${nodeName}?${if (impl) " = null" else ""}"
                    ONE_OR_MORE, ZERO_OR_MORE -> "MutableList<${nodeName}>"
                }
                +!""
            }
            +!"override fun ${ITreeParent::addChild.name}(node: INode) {"
            indentCount++
            +!"when (node) {"
            indentCount++
            for (ref in refList) {
                +"is ${ref.name.nameForNode} -> "
                +!when (ref.type) {
                    NON_NULL, NULLABLE -> "${ref.name.nameForChildProperty(false)} = node"
                    ONE_OR_MORE, ZERO_OR_MORE -> "${ref.name.nameForChildProperty(true)}.add(node)"
                }
            }
            +!"else -> {} // currently do nothing"
            indentCount--
            +!"}"
            //
            +!"${ITreeParent::children.name}.add(node)"
            indentCount--
            +!"}"
        }

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
        indentCount++
        if (reference.size == 1) {
            genContentPropertiesAndAddChildFunction(reference.single())
        }
        +!"override fun <D, R> accept(visitor: IVisitor<D, R>, data: D): R {"
        indentCount++
        +!"if (visitor is ${declValName.visitorClassName}<D, R>) {"
        +!"    return visitor.${name.nameForVisitFunction}(this, data)"
        +!"}"
        +!"return super<INode>.accept(visitor, data)"
        indentCount--
        +!"}"
        indentCount--
        +!"}\n"

        if (reference.isNotEmpty()) {
            // child node will extend I{current node name}Child
            +!"sealed interface ${name.nameForChild} : ${INode::class.simpleName}\n"
        }

        if (parents.isNotEmpty()) {
            +!"sealed interface ${name.nameForParent} : ${INode::class.simpleName}"
        }

        if (reference.isEmpty()) {
            +!"open class ${name.nameForDefaultNode} : ${name.nameForNode} {"
            if (parents.isNotEmpty()) {
                +!"    override lateinit var ${ITreeChild::parent.name}: ${INode::class.simpleName}"
            }
            if (reference.isNotEmpty()) {
                +!"    override val ${ITreeParent::children.name}: MutableList<INode> = mutableListOf()"
            }
            +!"}"
        } else if (reference.size > 1) {
            +!"open class ${name.nameForDefaultNode} : ${name.nameForDefaultNode}0()"
        }
        for ((i, refList) in reference.withIndex()) {
            +"open class ${name.nameForDefaultNode}"
            if (reference.size > 1) {
                +"$i"
            }
            +" : ${name.nameForNode}"
            if (reference.size > 1) {
                +"$i"
            }
            +!" {"
            indentCount++
            if (parents.isNotEmpty()) {
                +!"override lateinit var ${ITreeChild::parent.name}: ${INode::class.simpleName}"
            }
            if (reference.isNotEmpty()) {
                +!"override val ${ITreeParent::children.name}: MutableList<INode> = mutableListOf()"
            }
            genContentPropertiesAndAddChildFunction(refList, true)
            indentCount--
            +!"}"
            if (reference.size > 1) {
                +!"interface ${name.nameForNode}${i} : ${name.nameForNode} {"
                indentCount++
                genContentPropertiesAndAddChildFunction(refList)
                indentCount--
                +!"}"
            }
        }
    }

    private fun PrintWriterWrapper.writeClassHead(packageName: String) {
        +!"@file:Suppress(\"unused\", \"ClassName\", \"RemoveEmptyClassBody\", \"PropertyName\")"
        +!""
        +!"package $packageName"
        +!""
        +!"import ${INode::class.qualifiedName!!}"
        +!"import ${IRef::class.qualifiedName!!}"
        +!"import ${ITreeParent::class.qualifiedName!!}"
        +!"import ${ITreeChild::class.qualifiedName!!}"
        +!"import ${IVisitor::class.qualifiedName!!}"
        +!"import ${RefNode::class.qualifiedName!!}"
        +!"import ${AbstractGenerator::class.qualifiedName!!}"
        +!""
    }

    companion object {
        const val NAME_GENERATED = "generated"
        const val NAME_CHOICE = "Choice"
    }

    private class PrintWriterWrapper(private val printer: PrintWriter) : Closeable by printer {
        var indentCount = 0

        /**
         * true if the printer just ready to start a new line.
         */
        var newLine = true

        @JvmInline
        value class StringAndNewLine(val str: String)

        inline fun editorFoldOf(foldName: String, block: PrintWriterWrapper.() -> Unit) {
            +!"//<editor-fold desc=\"${foldName}\">"
            block()
            +!"//</editor-fold>"
        }

        operator fun String.not(): StringAndNewLine {
            return StringAndNewLine(this)
        }

        operator fun String.unaryPlus() {
            if (newLine) {
                printer.print("    ".repeat(indentCount))
                newLine = false
            }
            printer.print(this)
        }

        operator fun StringAndNewLine.unaryPlus() {
            if (newLine) {
                printer.print("    ".repeat(indentCount))
                newLine = false
            }
            printer.println(this.str)
            newLine = true
        }

        infix fun <T> String.join(iter: Iterable<T>): String {
            return iter.joinToString(this)
        }
    }
}

