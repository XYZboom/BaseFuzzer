package com.github.xyzboom.bf.def

import org.intellij.lang.annotations.Language

/**
 * The annotation to guide KSP generation of definition tree node classes.
 * ```kotlin
 * // todo examples todo here
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
annotation class DefinitionDecl(
    val defValue: String,
    @Language("YAML")
    val extraValue: String = ""
)
