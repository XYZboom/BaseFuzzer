package com.github.xyzboom.bf.ksp

import com.github.xyzboom.bf.def.DefinitionDecl
import com.github.xyzboom.bf.def.Parser
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.OutputStreamWriter

class DefinitionProcessor(
    private val codeGen: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) {
            return emptyList()
        }
        invoked = true
        val definitionDecls = resolver.getSymbolsWithAnnotation(DefinitionDecl.FULL_NAME)
        logger.info("found ${definitionDecls.count()} definitions")
        for (defDecl in definitionDecls) {
            handleDef(defDecl)
        }
        return emptyList()
    }

    @OptIn(KspExperimental::class)
    private fun handleDef(defDecl: KSAnnotated) {
        if (defDecl !is KSPropertyDeclaration) {
            logger.error("${DefinitionDecl.NAME} should only be used on a const top-level property!", defDecl)
            return
        }
        val containingFile = defDecl.containingFile ?: return
        val parent = defDecl.parent
        if (parent != null && parent !is KSFile) {
            logger.error("The property has ${DefinitionDecl.NAME} should be const top-level String!", defDecl)
            return
        }
        if (Modifier.CONST !in defDecl.modifiers) {
            logger.error("The property has ${DefinitionDecl.NAME} should be const top-level String!", defDecl)
            return
        }
        val type = defDecl.type.resolve().declaration.qualifiedName
        if (type?.asString() != "kotlin.String") {
            logger.error("The property has ${DefinitionDecl.NAME} should be const top-level String!", defDecl)
            return
        }
        val anno = defDecl.getAnnotationsByType(DefinitionDecl::class).single()
        val def = Parser().parseDefinition(anno.defValue)
        val packageName = defDecl.packageName.asString()
        val fileName = defDecl.simpleName.asString()
        val stream = codeGen.createNewFile(
            Dependencies(false, containingFile),
            packageName,
            fileName
        )
        OutputStreamWriter(stream).use { writer ->
            if (packageName.isNotEmpty()) {
                writer.write("package $packageName\n\n")
            }
            for ((name, stat) in def.statementsMap) {
                val className = name.replaceFirstChar { it.uppercaseChar() }
                writer.write("interface $className\n\n")
            }
        }
    }
}

