package com.github.xyzboom.bf.def

class Definition(
    val statementsMap: MutableMap<String, Statement>
) {
    val statement get() = statementsMap.values
}