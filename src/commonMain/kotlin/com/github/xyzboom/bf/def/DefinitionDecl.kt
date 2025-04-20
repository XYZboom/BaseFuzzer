package com.github.xyzboom.bf.def

import org.intellij.lang.annotations.Language

@Target(AnnotationTarget.PROPERTY)
annotation class DefinitionDecl(
    val defValue: String,
    @Language("YAML")
    val extraValue: String = ""
) {
    companion object {
        const val NAME = "DefinitionDecl"
    }
}
