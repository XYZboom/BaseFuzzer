package io.github.xyzboom.bf.ksp

import com.google.devtools.ksp.KSTypeNotPresentException
import io.github.xyzboom.bf.def.*
import io.github.xyzboom.bf.def.RefType.*
import io.github.xyzboom.bf.gen.AbstractGenerator
import io.github.xyzboom.bf.tree.*
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

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
            handleDef(resolver, defDecl)
        }
        return emptyList()
    }

    //<editor-fold desc="Names">
    private val String.nameForParent
        get(): String {
            require(this !in noParentNodeNames)
            return "I${uppercaseFirstChar()}Parent"
        }

    private val String.nameForNode
        get(): String {
            return "I${uppercaseFirstChar()}Node"
        }

    /**
     * Name of user defined implement node class,
     * or [nameForNode] if no implement defined.
     */
    private val String.nameForImpl
        get(): String {
            return implNodeMap[this] ?: nameForNode
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

    private val noParentNodeNames = mutableSetOf<String>()
    private val noCacheNodeNames = mutableSetOf<String>()
    private val implNodeMap = mutableMapOf<String, String>()

    @OptIn(KspExperimental::class)
    private fun handleDef(resolver: Resolver, defDecl: KSAnnotated) {
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
        parseExtra(resolver, defDecl, anno.extra)
        val packagePrefix = defDecl.packageName.asString()
        val packageName = if (packagePrefix.isNotEmpty()) "${packagePrefix}.${NAME_GENERATED}" else NAME_GENERATED
        val declValName = DeclValName(defDecl.simpleName.asString())
        newFile(containingFile, packageName, "${declValName.value}Nodes") {
            for ((name, stat) in def.statementsMap) {
                newClassForStatement(declValName, name, stat.contents, def.parentMap[name] ?: emptySet())
            }
        }
        createGeneratorClass(containingFile, packageName, declValName, def)
        createVisitorClass(containingFile, packageName, declValName, def)
    }

    @OptIn(KspExperimental::class)
    fun parseAnnotationClassParameter(block: () -> KClass<*>): String? {
        return try { // KSTypeNotPresentException will be thrown
            block.invoke().qualifiedName
        } catch (e: KSTypeNotPresentException) {
            var res: String? = null
            val declaration = e.ksType.declaration
            if (declaration is KSClassDeclaration) {
                declaration.qualifiedName?.asString()?.let {
                    res = it
                }
            }
            res
        }
    }

    private fun parseExtra(resolver: Resolver, defDecl: KSNode, extraValue: DefExtra) {
        fun parseNameSet(target: MutableSet<String>, from: Array<String>) {
            target.addAll(from)
        }

        parseNameSet(noParentNodeNames, extraValue.noParentNames)
        parseNameSet(noCacheNodeNames, extraValue.noCacheNames)

        run implNode@{
            for (pair in extraValue.implNames) {
                implNodeMap[pair.str] = parseAnnotationClassParameter { pair.clazz }!!
            }
        }
    }

    private fun createVisitorClass(
        containingFile: KSFile,
        packageName: String,
        declValName: DeclValName,
        def: Definition
    ) {
        val visitorName = declValName.visitorClassName
        newFile(containingFile, packageName, visitorName) {
            addType(
                TypeSpec.interfaceBuilder(visitorName).apply {
                    val d = TypeVariableName("D")
                    val r = TypeVariableName("R")
                    addTypeVariable(d)
                    addTypeVariable(r)
                    addSuperinterface(IVisitor::class.asClassName().parameterizedBy(d, r))
                    for ((name, _) in def.statementsMap) {
                        addFunction(
                            FunSpec.builder(name.nameForVisitFunction).apply {
                                addParameter("node", ClassName(packageName, name.nameForImpl))
                                addParameter("data", d)
                                returns(r)
                                addStatement("return ${IVisitor<*, *>::visitNode.name}(node, data)")
                            }.build()
                        )
                    }
                }.build()
            )
        }
        val topDownVisitorName = declValName.topDownVisitorClassName
        newFile(containingFile, packageName, topDownVisitorName) {
            val d = TypeVariableName("D")
            addType(
                TypeSpec.interfaceBuilder(topDownVisitorName).apply {
                    addTypeVariable(d)
                    addSuperinterface(
                        ClassName(packageName, visitorName).parameterizedBy(d, Unit::class.asClassName())
                    )
                    addFunction(
                        FunSpec.builder("visitNode").apply {
                            addModifiers(KModifier.OVERRIDE)
                            addParameter("node", INode::class)
                            addParameter("data", d)
                            beginControlFlow("if (node is %T)", ITreeParent::class)
                            addStatement("node.acceptChildren(this, data)")
                            endControlFlow()
                        }.build()
                    )
                }.build()
            )
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
            val type = TypeSpec.classBuilder(generatorName).apply type@{
                addModifiers(KModifier.OPEN)
                superclass(AbstractGenerator::class)
                editorFoldOf("generated nodes") {
                    for ((name, _) in def.statementsMap) {
                        if (name !in noCacheNodeNames) {
                            this@type.addProperty(
                                PropertySpec.builder(
                                    name.nameForGeneratedNodesProperty,
                                    ClassName("kotlin.collections", "MutableList")
                                        .parameterizedBy(ClassName(packageName, name.nameForNode))
                                ).initializer("mutableListOf()").build()
                            )
                        }
                    }
                    this@type.addFunction(
                        FunSpec.builder("clearGeneratedNodes").apply {
                            addModifiers(KModifier.OPEN)
                            for ((name, _) in def.statementsMap) {
                                if (name !in noCacheNodeNames) {
                                    addStatement("${name.nameForGeneratedNodesProperty}.clear()")
                                }
                            }
                        }.build()
                    )
                }
                editorFoldOf("choose reference") {
                    for ((name, _) in def.statementsMap) {
                        this@type.addFunction(
                            FunSpec.builder(name.nameForChooseReferenceFunction).apply {
                                addModifiers(KModifier.OPEN)
                                if (def.parentMap[name] != null && name !in noParentNodeNames) {
                                    addParameter("parent", ClassName(packageName, name.nameForParent))
                                }
                                returns(IRef::class.asClassName().copy(nullable = true))
                                if (name !in noCacheNodeNames) {
                                    beginControlFlow("if (random.nextBoolean())")
                                    addStatement("return null")
                                    nextControlFlow("else")
                                    addStatement(
                                        "return %T(${name.nameForGeneratedNodesProperty}.random(random))",
                                        RefNode::class
                                    )
                                    endControlFlow()
                                } else {
                                    addStatement("return null")
                                }
                            }.build()
                        )
                    }
                }
                editorFoldOf("choose index functions") {
                    for ((name, stat) in def.statementsMap) {
                        if (stat.contents.size > 1) {
                            this@type.addFunction(
                                FunSpec.builder(name.nameForChooseIndexFunction).apply {
                                    addModifiers(KModifier.OPEN)
                                    if (def.parentMap[name] != null && name !in noParentNodeNames) {
                                        addParameter("context", ClassName(packageName, name.nameForParent))
                                    }
                                    returns(Int::class)
                                    addStatement("return ${AbstractGenerator::random.name}.nextInt(${stat.contents.size})")
                                }.build()
                            )
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
                                val funcName = nameForChooseSizeFunction(
                                    "$name${if (stat.contents.size > 1) i else ""}",
                                    ref.name
                                )
                                this@type.addFunction(
                                    FunSpec.builder(funcName).apply {
                                        addModifiers(KModifier.OPEN)
                                        addParameter("parent", ClassName(packageName, name.nameForNode))
                                        returns(Int::class)
                                        addStatement(
                                            "return %L",
                                            when (refType) {
                                                NULLABLE -> "random.nextInt(2)"
                                                ONE_OR_MORE -> "random.nextInt(${AbstractGenerator::DEFAULT_MAX_SIZE.name}) + 1"
                                                ZERO_OR_MORE -> "random.nextInt(-1, ${AbstractGenerator::DEFAULT_MAX_SIZE.name}) + 1"
                                                else -> throw NoWhenBranchMatchedException()
                                            }
                                        )
                                    }.build()
                                )
                            }
                        }
                    }
                }
                editorFoldOf("new node functions") {
                    for ((name, _) in def.statementsMap) {
                        this@type.addFunction(
                            FunSpec.builder(name.nameForNewNodeFunction).apply {
                                addModifiers(KModifier.OPEN)
                                returns(ClassName(packageName, name.nameForNode))
                                addStatement("return ${name.nameForDefaultNode}()")
                            }.build()
                        )
                    }
                }
                editorFoldOf("generate functions") {
                    for ((name, stat) in def.statementsMap) {
                        this@type.addFunction(
                            FunSpec.builder(name.nameForGenFunction).apply {
                                addModifiers(KModifier.OPEN)
                                val hasParent = def.parentMap[name] != null
                                if (hasParent && name !in noParentNodeNames) {
                                    addParameter("parent", ClassName(packageName, name.nameForParent))
                                }
                                returns(INode::class)
                                addStatement(
                                    "val chooseRef = %L(%L)",
                                    name.nameForChooseReferenceFunction,
                                    if (hasParent && name !in noParentNodeNames) {
                                        "parent"
                                    } else ""
                                )
                                beginControlFlow("if (chooseRef != null)")
                                addStatement("return chooseRef")
                                endControlFlow()
                                addStatement("val result = ${name.nameForNewNodeFunction}()")
                                if (name !in noCacheNodeNames) {
                                    addStatement("${name.nameForGeneratedNodesProperty}.add(result)")
                                }
                                if (hasParent && name !in noParentNodeNames) {
                                    addStatement("result.${ITreeChild::parent.name} = parent")
                                }

                                fun writeGenChildrenCode(refList: ReferenceList) {
                                    for (ref in refList) {
                                        val refName = ref.name
                                        val argument = if (refName in noParentNodeNames) "" else "result"
                                        when (ref.type) {
                                            NON_NULL -> addStatement("result.addChild(${refName.nameForGenFunction}($argument))")
                                            else -> {
                                                addStatement(
                                                    "repeat(${
                                                        nameForChooseSizeFunction(
                                                            name,
                                                            refName
                                                        )
                                                    }(result)) {"
                                                )
                                                addStatement("    result.addChild(${refName.nameForGenFunction}($argument))")
                                                addStatement("}")
                                            }
                                        }
                                    }
                                }


                                if (stat.contents.size > 1) {
                                    beginControlFlow(
                                        "when (val index = ${name.nameForChooseIndexFunction}(%L))",
                                        if (hasParent) {
                                            "parent"
                                        } else ""
                                    )

                                    for ((i, refList) in stat.contents.withIndex()) {
                                        addStatement("$i -> {")
                                        writeGenChildrenCode(refList)
                                        addStatement("}")
                                    }
                                    addStatement(
                                        "else -> throw ${IllegalArgumentException::class.qualifiedName!!}(\"" +
                                                "Index: ${'$'}index returned from ${name.nameForChooseIndexFunction} is illegal." +
                                                "\")"
                                    )
                                    endControlFlow()
                                } else if (stat.contents.size == 1) {
                                    writeGenChildrenCode(stat.contents.single())
                                }
                                addStatement("return result")
                            }.build()
                        )
                    }
                }
            }.build()
            addType(type)
        }
    }

    private inline fun newFile(
        containingFile: KSFile,
        packageName: String,
        name: String,
        block: FileSpec.Builder.() -> Unit
    ) {
        FileSpec.builder(packageName, name).apply(block)
            .build().writeTo(codeGen, Dependencies(false, containingFile))
    }

    private fun FileSpec.Builder.newClassForStatement(
        declValName: DeclValName,
        name: String,
        reference: List<ReferenceList>,
        parents: Set<String>
    ) {

        fun TypeSpec.Builder.genContentPropertiesAndAddChildFunction(refList: ReferenceList, impl: Boolean = false) {
            for (ref in refList) {
                val nodeName = ref.name.nameForNode
                val nodeClassName = ClassName(packageName, nodeName)
                val propertyType = when (ref.type) {
                    NON_NULL -> nodeClassName
                    NULLABLE -> nodeClassName.copy(nullable = true)
                    ONE_OR_MORE, ZERO_OR_MORE -> ClassName("kotlin.collections", "MutableList").parameterizedBy(
                        nodeClassName
                    )
                }
                addProperty(
                    PropertySpec.builder(ref.name.nameForChildProperty(ref.type.canBeMulti()), propertyType)
                        .apply propertySpec@{
                            when (ref.type) {
                                NON_NULL, NULLABLE -> mutable()
                                else -> {} // do nothing
                            }
                            if (impl) {
                                addModifiers(KModifier.OVERRIDE)
                                if (ref.type == NON_NULL) {
                                    addModifiers(KModifier.LATEINIT)
                                }
                                val initCode = when (ref.type) {
                                    NULLABLE -> "null"
                                    ONE_OR_MORE, ZERO_OR_MORE -> "mutableListOf()"
                                    else -> return@propertySpec
                                }
                                initializer(CodeBlock.builder().apply {
                                    addStatement(initCode)
                                }.build())
                            }
                        }.build()
                )
            }
            addFunction(
                FunSpec.builder(ITreeParent::addChild.name).apply {
                    addModifiers(KModifier.OVERRIDE)
                    addParameter("node", INode::class)
                    addCode(CodeBlock.builder().apply {
                        beginControlFlow("when (node)")
                        for (ref in refList) {
                            addStatement(
                                "is ${ref.name.nameForNode} -> %L",
                                when (ref.type) {
                                    NON_NULL, NULLABLE -> "${ref.name.nameForChildProperty(false)} = node"
                                    ONE_OR_MORE, ZERO_OR_MORE -> "${ref.name.nameForChildProperty(true)}.add(node)"
                                }
                            )
                        }
                        addStatement("else -> {} // currently do nothing")
                        endControlFlow()
                        addStatement("${ITreeParent::children.name}.add(node)")
                    }.build())
                }.build()
            )
        }

        addType(
            TypeSpec.interfaceBuilder(name.nameForNode).apply {
                addSuperinterface(INode::class)
                if (reference.isNotEmpty()) {
                    addSuperinterface(ITreeParent::class)
                }
                for (refList in reference) {
                    for (ref in refList) {
                        val refName = ref.name
                        if (refName !in noParentNodeNames) {
                            addSuperinterface(ClassName(packageName, refName.nameForParent))
                        }
                    }
                }
                if (parents.isNotEmpty() && name !in noParentNodeNames) {
                    addSuperinterface(ITreeChild::class)
                    for (parent in parents) {
                        addSuperinterface(ClassName(packageName, parent.nameForChild))
                    }
                }
                if (reference.size == 1) {
                    genContentPropertiesAndAddChildFunction(reference.single())
                }
                addFunction(FunSpec.builder("accept").apply {
                    addModifiers(KModifier.OVERRIDE)
                    val d = TypeVariableName("D")
                    val r = TypeVariableName("R")
                    addTypeVariables(listOf(d, r))
                    addParameter(
                        "visitor",
                        IVisitor::class.asClassName().parameterizedBy(d, r)
                    )
                    addParameter("data", d)
                    returns(r)
                    val castForUserDefinedImplNode = if (name in implNodeMap) {
                        " && this is ${name.nameForImpl}"
                    } else ""
                    beginControlFlow("if (visitor is ${declValName.visitorClassName}<D, R>$castForUserDefinedImplNode)")
                    addStatement("return visitor.${name.nameForVisitFunction}(this, data)")
                    endControlFlow()
                    addStatement("return super<INode>.accept(visitor, data)")
                }.build())
            }.build()
        )

        if (reference.isNotEmpty()) {
            // child node will extend I{current node name}Child
            addType(
                TypeSpec.interfaceBuilder(name.nameForChild)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(INode::class)
                    .build()
            )
        }

        if (parents.isNotEmpty() && name !in noParentNodeNames) {
            addType(
                TypeSpec.interfaceBuilder(name.nameForParent)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(INode::class)
                    .build()
            )
        }

        if (reference.isEmpty()) {
            addType(
                TypeSpec.classBuilder(name.nameForDefaultNode).apply {
                    addModifiers(KModifier.OPEN)
                    addSuperinterface(ClassName(packageName, name.nameForNode))
                    if (parents.isNotEmpty() && name !in noParentNodeNames) {
                        addProperty(
                            PropertySpec.builder(ITreeChild::parent.name, INode::class)
                                .mutable()
                                .addModifiers(KModifier.OVERRIDE)
                                .addModifiers(KModifier.LATEINIT)
                                .build()
                        )
                    }
                    if (reference.isNotEmpty()) {
                        addProperty(
                            PropertySpec.builder(
                                ITreeParent::children.name,
                                ClassName(
                                    "kotlin.collections",
                                    "MutableList"
                                ).parameterizedBy(INode::class.asClassName())
                            ).apply {
                                addModifiers(KModifier.OVERRIDE)
                                addModifiers(KModifier.LATEINIT)
                                initializer("mutableListOf()")
                            }.build()
                        )
                    }
                }.build()
            )
        } else if (reference.size > 1) {
            addType(
                TypeSpec.classBuilder(name.nameForDefaultNode).apply {
                    addModifiers(KModifier.OPEN)
                    superclass(ClassName(packageName, "${name.nameForDefaultNode}0"))
                }.build()
            )
        }
        for ((i, refList) in reference.withIndex()) {
            val numberStr = if (reference.size > 1) {
                "$i"
            } else ""
            addType(
                TypeSpec.classBuilder("""${name.nameForDefaultNode}$numberStr""").apply {
                    addModifiers(KModifier.OPEN)
                    addSuperinterface(ClassName(packageName, "${name.nameForNode}$numberStr"))
                    if (parents.isNotEmpty() && name !in noParentNodeNames) {
                        addProperty(
                            PropertySpec.builder(ITreeChild::parent.name, INode::class)
                                .mutable()
                                .addModifiers(KModifier.OVERRIDE)
                                .addModifiers(KModifier.LATEINIT)
                                .build()
                        )
                    }
                    if (reference.isNotEmpty()) {
                        addProperty(
                            PropertySpec.builder(
                                ITreeParent::children.name,
                                ClassName(
                                    "kotlin.collections",
                                    "MutableList"
                                ).parameterizedBy(INode::class.asClassName())
                            ).addModifiers(KModifier.OVERRIDE)
                                .initializer("mutableListOf()")
                                .build()
                        )
                    }
                    genContentPropertiesAndAddChildFunction(refList, true)
                }.build()
            )
            if (reference.size > 1) {
                addType(
                    TypeSpec.interfaceBuilder("${name.nameForNode}${i}").apply {
                        addSuperinterface(ClassName(packageName, name.nameForNode))
                        genContentPropertiesAndAddChildFunction(refList)
                    }.build()
                )
            }
        }
    }

    companion object {
        const val NAME_GENERATED = "generated"
    }

    fun FileSpec.Builder.editorFoldOf(foldName: String, block: FileSpec.Builder.() -> Unit) {
        addFileComment("<editor-fold desc=\"${foldName}\">")
        block()
        addFileComment("</editor-fold>")
    }

}

