package com.github.xyzboom.bf.def

import kotlin.jvm.JvmStatic

data class Reference(
    val name: String,
    val type: RefType
) {
    companion object {
        /**
         * Create unchecked [Reference]s from the given [value].
         *
         * Supported types:
         * * [String]
         * * [List]<*>
         * @param value the value used to create [Reference]s
         * @return the created [Reference]s together with a list of inlined element in reference declaration。
         */
        @JvmStatic
        inline fun createFrom(value: Any): Pair<List<Reference>, List<Element>> {
            if (value is String) {
                return listOf(createFrom(value)) to emptyList()
            }
            if (value is List<*>) {
                return createFrom(value)
            }
            throw IllegalArgumentException("Unexpected value type: ${value::class}")
        }

        @JvmStatic
        fun createFrom(name: String): Reference {
            for (type in RefType.entries) {
                if (name.endsWith(type.typeString)) {
                    return Reference(name.removeSuffix(type.typeString), type)
                }
            }
            return Reference(name, RefType.DEFAULT)
        }

        @JvmStatic
        fun createFrom(values: List<*>): Pair<List<Reference>, List<Element>> {
            val references = mutableListOf<Reference>()
            val elements = mutableListOf<Element>()
            for (value in values) {
                if (value == null) continue
                when (value) {
                    is String -> {
                        val reference = createFrom(value)
                        references.add(reference)
                    }

                    is Map<*, *> -> {
                        TODO()
                    }
                }
            }
            return Pair(references, elements)
        }
    }
}