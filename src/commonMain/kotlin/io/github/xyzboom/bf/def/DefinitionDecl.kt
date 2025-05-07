package io.github.xyzboom.bf.def

import io.github.xyzboom.bf.tree.INode
import kotlin.reflect.KClass

/**
 * The annotation to guide KSP generation of definition tree node classes.
 * ```kotlin
 * // todo examples todo here
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
annotation class DefinitionDecl(
    val defValue: String,
    val extra: DefExtra = DefExtra()
)

annotation class DefExtra(
    val noParentNames: Array<String> = [],
    val noCacheNames: Array<String> = [],
    val implNames: Array<DefImplPair> = [],
)

typealias DefImplPair = AnnoStringClassPair<INode>

annotation class AnnoStringClassPair<T : Any>(
    val str: String,
    val clazz: KClass<out T>,
)

operator fun <T : Any> AnnoStringClassPair<T>.component1(): String = str
operator fun <T : Any> AnnoStringClassPair<T>.component2(): KClass<out T> = clazz
