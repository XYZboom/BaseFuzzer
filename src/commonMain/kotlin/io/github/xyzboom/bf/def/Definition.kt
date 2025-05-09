package io.github.xyzboom.bf.def

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
    val parentMap: Map<String, Map<String, RefType>> by lazy { collectContext() }

    private fun collectContext(): Map<String, Map<String, RefType>> {
        val result = mutableMapOf<String, MutableMap<String, RefType>>()
        for ((name, stat) in statementsMap) {
            for (refList in stat.contents) {
                for (ref in refList) {
                    result.getOrPut(ref.name) { mutableMapOf() }.put(name, ref.type)
                }
            }
        }
        return result
    }
}