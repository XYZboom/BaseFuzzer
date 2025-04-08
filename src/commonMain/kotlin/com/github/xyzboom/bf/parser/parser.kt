package com.github.xyzboom.bf.parser

import com.charleskorn.kaml.*
import com.github.xyzboom.bf.def.Element
import com.github.xyzboom.bf.def.LeafElement.Companion.BUILT_IN_LEAF_NAME
import com.github.xyzboom.bf.def.RefType
import com.github.xyzboom.bf.def.Reference

private val build_in_names = listOf(
    BUILT_IN_LEAF_NAME
)

/**
 * Parse [String]-[String] map into [String]-[Element] map.
 * ```yaml
 * # the top names are the names of declarations
 * prog:
 *   topDecl+ # + represents a prog can contains zero or more topDecl.
 *   # * represents zero or more.
 * topDecl:
 *   - class! # ! represents a topDecl must contain a class
 *   # ? represents zero or one
 *   - lang # by default, ! can be omitted
 * class:
 *   - className~ # ~ represents className is a leaf element
 *   - superClass: type # The inlined declaration of superClass
 *   - superIntf: type* # the inlined decoration of superIntf
 *   - func+
 * func:
 *   - funcName
 *   - param*
 *   - overrides: func*
 * ```
 * @param definitions the map of definitions whose key is name and value is raw element reference.
 * @return
 */
fun parse(definitions: Map<String, Any?>): Map<String, Element> {
    val result = mutableMapOf<String, Element>()
    for ((name, value) in definitions) {
        if (name == BUILT_IN_LEAF_NAME) {
            val leafElement = when (value) {
                is String -> parseLeaf(listOf(value))
                is List<*> -> parseLeaf(value)
                else -> throw IllegalStateException("Reference in the built-in $BUILT_IN_LEAF_NAME should be String or List")
            }
            result[leafElement.name] = leafElement
            continue
        }
        if (result.containsKey(name)) {
            throw IllegalStateException("Definition with name $name already exists")
        }
        if (value == null) {
            throw NullPointerException()
        }
        val (references, newElements) = Reference.createFrom(value)
        result[name] = Element(name, references)
        for (newElement in newElements) {
            if (result.containsKey(newElement.name)) {
                throw IllegalStateException("Definition with name ${newElement.name} already exists")
            }
            result[newElement.name] = newElement
        }
    }
    checkReference(result)
    return result
}

private fun parseLeaf(leaves: List<*>): Element {
    val references = mutableListOf<Reference>()
    val leaf = Element(BUILT_IN_LEAF_NAME, references)
    for (leafName in leaves) {
        if (leafName == null) continue
        if (leafName !is String) {
            throw IllegalStateException("Reference in the built-in $BUILT_IN_LEAF_NAME should be a string")
        }
        val ref = Reference.createFrom(leafName)
        require(ref.type == RefType.NON_NULL) {
            "Reference in the built-in $BUILT_IN_LEAF_NAME should be non-null"
        }
        references.add(ref)
    }
    return leaf
}

private inline fun checkReference(result: MutableMap<String, Element>) {
    val leafElement = result[BUILT_IN_LEAF_NAME]
    require(leafElement != null) {
        "No leaf found!"
    }
    for ((_, element) in result) {
        for (reference in element.references) {
            require(result.containsKey(reference.name) || leafElement.references.any { it.name == reference.name }) {
                "Reference in ${element.name} with name ${reference.name} does not exist!"
            }
            require(reference.name !in build_in_names) {
                "Could not refer to built-in name: ${reference.name}"
            }
        }
    }
}

fun YamlNode?.toKtObject(): Any? {
    return when (this) {
        null -> null
        is YamlList -> toList()
        is YamlMap -> toMap()
        is YamlNull -> null
        is YamlScalar -> content
        is YamlTaggedNode -> tag
    }
}

fun YamlMap.toMap(): Map<String, Any?> = entries.map { (k, v) -> k.content to v.toKtObject() }.toMap()

fun YamlList.toList(): List<Any?> = items.map { it.toKtObject() }

fun parseYamlElement(content: String): Map<String, Element> {
    return parse(Yaml.default.parseToYamlNode(content).yamlMap.toMap())
}