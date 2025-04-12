package com.github.xyzboom.bf.def

import kotlinx.serialization.Serializable

@Serializable
class Definition(
    val statementsMap: MutableMap<String, Statement>
) {
    val statements get() = statementsMap.values
}