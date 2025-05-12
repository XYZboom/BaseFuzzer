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
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
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
            return "IParentOf${uppercaseFirstChar()}"
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
            return "IChildOf${uppercaseFirstChar()}"
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

    private val String.nameForParentProperty: String
        get(): String {
            return "${lowercaseFirstChar()}Parent"
        }

    private val String.nameForChildProperty: String
        get(): String {
            return "${lowercaseFirstChar()}Child"
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
                newClassForStatement(declValName, name, stat.contents, def.parentMap[name] ?: emptyMap())
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

    @JvmInline
    private value class FuncName(val value: String)

    context(name: FuncName)
    private fun FunSpec.Builder.genLogExit() {
        addStatement("logger.trace { %S }", "exit ${name.value}")
    }

    context(wrapper: FuncName)
    private fun FunSpec.Builder.genLogTrace(content: String) {
        addStatement("logger.trace { %S }", content)
    }

    context(wrapper: FuncName)
    private fun FunSpec.Builder.genLogTrace(block: context(FuncName) FunSpec.Builder.() -> Unit) {
        beginControlFlow("logger.trace")
        block()
        endControlFlow()
    }

    private fun TypeSpec.Builder.funcWithLog(
        name: String,
        block: context(FuncName) FunSpec.Builder.() -> Unit
    ) {
        val funcBuilder = FunSpec.builder(name)
        val funcName = FuncName(name)
        val func = with(funcName) {
            funcBuilder.apply {
                addStatement("logger.trace { %S }", "enter $name")
                block()
            }.build()
        }
        addFunction(func)
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
                addProperty(
                    PropertySpec.builder("logger", KLogger::class, KModifier.PRIVATE)
                        .initializer(
                            "%T.logger {}",
                            KotlinLogging::class,
                        ).build()
                )
                addFunction(
                    FunSpec.builder("render")
                        .addModifiers(KModifier.OPEN)
                        .returns(String::class)
                        .addParameter("node", INode::class)
                        .addStatement("return node.toString()")
                        .build()
                )
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
                    funcWithLog("clearGeneratedNodes") {
                        addModifiers(KModifier.OPEN)
                        for ((name, _) in def.statementsMap) {
                            if (name !in noCacheNodeNames) {
                                addStatement("${name.nameForGeneratedNodesProperty}.clear()")
                            }
                        }
                        genLogExit()
                    }
                }
                editorFoldOf("choose reference") {
                    for ((name, _) in def.statementsMap) {
                        funcWithLog(name.nameForChooseReferenceFunction) {
                            addModifiers(KModifier.OPEN)
                            if (def.parentMap[name] != null && name !in noParentNodeNames) {
                                addParameter("parent", ClassName(packageName, name.nameForParent))
                            }
                            val returnType = IRef::class.asClassName().copy(nullable = true)
                            returns(returnType)
                            if (name !in noCacheNodeNames) {
                                beginControlFlow("if (random.nextBoolean())")
                                genLogTrace("no reference was chosen for $name")
                                genLogExit()
                                addStatement("return null")
                                nextControlFlow("else")
                                addStatement(
                                    "val result = %T(${name.nameForGeneratedNodesProperty}.random(random))",
                                    RefNode::class
                                )
                                genLogTrace {
                                    addStatement("%P", "Chosen reference for $name: ${'$'}{render(result)}")
                                }
                                genLogExit()
                                addStatement("return result")
                                endControlFlow()
                            } else {
                                genLogExit()
                                addStatement("return null")
                            }
                        }
                    }
                }
                editorFoldOf("choose index functions") {
                    for ((name, stat) in def.statementsMap) {
                        if (stat.contents.size > 1) {
                            funcWithLog(name.nameForChooseIndexFunction) {
                                addModifiers(KModifier.OPEN)
                                if (def.parentMap[name] != null && name !in noParentNodeNames) {
                                    addParameter("context", ClassName(packageName, name.nameForParent))
                                }
                                returns(Int::class)
                                genLogExit()
                                addStatement("return ${AbstractGenerator::random.name}.nextInt(${stat.contents.size})")
                            }
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
                                funcWithLog(funcName) {
                                    addModifiers(KModifier.OPEN)
                                    addParameter("parent", ClassName(packageName, name.nameForNode))
                                    returns(Int::class)
                                    genLogExit()
                                    addStatement(
                                        "return %L",
                                        when (refType) {
                                            NULLABLE -> "random.nextInt(2)"
                                            ONE_OR_MORE -> "random.nextInt(${AbstractGenerator::DEFAULT_MAX_SIZE.name}) + 1"
                                            ZERO_OR_MORE -> "random.nextInt(-1, ${AbstractGenerator::DEFAULT_MAX_SIZE.name}) + 1"
                                            else -> throw NoWhenBranchMatchedException()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                editorFoldOf("new node functions") {
                    for ((name, _) in def.statementsMap) {
                        // no logging need for new node functions
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

                        fun FunSpec.Builder.writeGenChildrenCode(
                            refList: ReferenceList,
                            cast: Boolean = false,
                            index: Int = 0
                        ) {
                            for (ref in refList) {
                                val refName = ref.name
                                val argument = if (refName in noParentNodeNames) {
                                    ""
                                } else if (cast) {
                                    "result as ${name.nameForNode}$index"
                                } else "result"
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

                        funcWithLog(name.nameForGenFunction) {
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
                            genLogExit()
                            addStatement("return chooseRef")
                            endControlFlow()
                            addStatement("val result = ${name.nameForNewNodeFunction}()")
                            if (name !in noCacheNodeNames) {
                                addStatement("${name.nameForGeneratedNodesProperty}.add(result)")
                            }
                            if (hasParent && name !in noParentNodeNames) {
                                addStatement("result.${ITreeChild::parent.name} = parent")
                            }

                            if (stat.contents.size > 1) {
                                beginControlFlow(
                                    "when (val index = ${name.nameForChooseIndexFunction}(%L))",
                                    if (hasParent) {
                                        "parent"
                                    } else ""
                                )

                                for ((i, refList) in stat.contents.withIndex()) {
                                    beginControlFlow("$i ->")
                                    writeGenChildrenCode(refList, cast = true, index = i)
                                    endControlFlow()
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
                            genLogExit()
                            addStatement("return result")
                        }
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
        parents: Map<String, RefType>
    ) {

        fun TypeSpec.Builder.genChildPropertiesAndAddChildFunction(
            refList: ReferenceList,
            impl: Boolean = false
        ) {
            for (ref in refList) {
                val refName = ref.name
                val nodeName = refName.nameForNode
                val nodeClassName = ClassName(packageName, nodeName)
                val childPropertyType = when (ref.type) {
                    NON_NULL -> NotNull::class.asClassName()
                    NULLABLE -> Nullable::class.asClassName()
                    ONE_OR_MORE -> OneOrMore::class.asClassName()
                    ZERO_OR_MORE -> ZeroOrMore::class.asClassName()
                }.parameterizedBy(nodeClassName)
                addProperty(
                    PropertySpec.builder(refName.nameForChildProperty, childPropertyType)
                        .apply propertySpec@{
                            when (ref.type) {
                                NON_NULL, NULLABLE -> mutable()
                                else -> {} // do nothing
                            }
                            addModifiers(KModifier.OVERRIDE)
                            mutable()
                            if (impl) {
                                if (ref.type == NON_NULL || ref.type == NULLABLE) {
                                    addModifiers(KModifier.LATEINIT)
                                }
                                val initCode = when (ref.type) {
                                    ONE_OR_MORE -> "${OneOrMore::class.simpleName}()"
                                    ZERO_OR_MORE -> "${ZeroOrMore::class.simpleName}()"
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
                                    NON_NULL -> "${ref.name.nameForChildProperty} = ${NotNull::class.simpleName}(node)"
                                    NULLABLE -> "${ref.name.nameForChildProperty} = ${Nullable::class.simpleName}(node)"
                                    ONE_OR_MORE, ZERO_OR_MORE -> "${ref.name.nameForChildProperty}.add(node)"
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
                if (reference.size == 1) {
                    val refList = reference.single()
                    for (ref in refList) {
                        val refName = ref.name
                        addSuperinterface(ClassName(packageName, refName.nameForParent))
                    }
                }
                if (parents.isNotEmpty() && name !in noParentNodeNames) {
                    addSuperinterface(ITreeChild::class)
                    for ((parent, _) in parents) {
                        addSuperinterface(ClassName(packageName, parent.nameForChild))
                    }
                }
                if (reference.size == 1) {
                    genChildPropertiesAndAddChildFunction(reference.single())
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
            val parentPropertyType = ClassName(packageName, name.nameForNode)
            addType(
                TypeSpec.interfaceBuilder(name.nameForChild)
                    .addSuperinterface(ITreeChild::class)
                    .addProperty(
                        PropertySpec.builder(
                            name.nameForParentProperty, parentPropertyType
                        ).getter(
                            FunSpec.getterBuilder().addStatement("return parent as %T", parentPropertyType).build()
                        ).build()
                    ).build()
            )
        }

        if (parents.isNotEmpty()) {
            addType(
                TypeSpec.interfaceBuilder(name.nameForParent).apply {
                    addSuperinterface(INode::class)
                    val valueSet = parents.values.toSet()
                    val typeClass = if (valueSet.size == 1) {
                        when (valueSet.single()) {
                            NON_NULL -> NotNull::class
                            NULLABLE -> Nullable::class
                            ONE_OR_MORE -> OneOrMore::class
                            ZERO_OR_MORE -> ZeroOrMore::class
                        }
                    } else {
                        IChildNode::class
                    }
                    addProperty(
                        PropertySpec.builder(
                            name.nameForChildProperty,
                            typeClass.asClassName().parameterizedBy(ClassName(packageName, name.nameForNode))
                        ).build()
                    )
                }.build()
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
                    // if the `for` is entered, that means reference must not be empty.
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
                    genChildPropertiesAndAddChildFunction(refList, true)
                }.build()
            )
            if (reference.size > 1) {
                addType(
                    TypeSpec.interfaceBuilder("${name.nameForNode}${i}").apply {
                        addSuperinterface(ClassName(packageName, name.nameForNode))
                        for (ref in refList) {
                            val refName = ref.name
                            addSuperinterface(ClassName(packageName, refName.nameForParent))
                        }
                        genChildPropertiesAndAddChildFunction(refList)
                    }.build()
                )
            }
        }
    }

    companion object {
        const val NAME_GENERATED = "generated"
    }

    fun FileSpec.Builder.editorFoldOf(foldName: String, block: FileSpec.Builder.() -> Unit) {
        // This does not work, see https://github.com/square/kotlinpoet/issues/1862 for more information
        addFileComment("<editor-fold desc=\"${foldName}\">")
        block()
        addFileComment("</editor-fold>")
    }

}

