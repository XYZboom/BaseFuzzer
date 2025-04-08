package com.github.xyzboom.bf.def

import kotlin.jvm.JvmStatic

enum class RefType(val typeString: String) {
    NON_NULL("!"),
    NULLABLE("?"),
    ONE_OR_MORE("+"),
    ZERO_OR_MORE("*");
    companion object {
        @JvmStatic
        val DEFAULT = NON_NULL

        @JvmStatic
        fun fromTypeString(typeString: String): RefType {
            for (refType in entries) {
                if (refType.typeString == typeString) {
                    return refType
                }
            }
            throw IllegalStateException()
        }
    }
}