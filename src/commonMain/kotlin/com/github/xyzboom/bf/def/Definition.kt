package com.github.xyzboom.bf.def

import kotlinx.serialization.Serializable

@Serializable
class Definition(
    val statementsMap: Map<String, Statement>
) {
    val statements get() = statementsMap.values

    /**
     * the context names of all statements.
     *
     * key: statement name.
     * value: available context names.
     */
    val parentMap: Map<String, Set<String>> by lazy { collectContext() }

    private fun collectContext(): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        for ((name, stat) in statementsMap) {
            for (refList in stat.contents) {
                for (ref in refList) {
                    result.getOrPut(ref.name) { mutableSetOf() }.add(name)
                }
            }
        }
        return result
    }
}