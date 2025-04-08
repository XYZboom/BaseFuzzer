package com.github.xyzboom.bf.def

import kotlinx.serialization.Serializable

@Serializable
class Definition(
    val statementsMap: MutableMap<String, Statement>
) {
    val statement get() = statementsMap.values
    val leaves: MutableMap<String, Statement> = mutableMapOf()

    object BuiltIn {
        const val LEAF_NAME = "~leaf"
    }
}