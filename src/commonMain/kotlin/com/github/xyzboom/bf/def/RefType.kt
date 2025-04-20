package com.github.xyzboom.bf.def

import kotlin.jvm.JvmStatic

enum class RefType(val typeString: String) {
    NON_NULL("!"),
    NULLABLE("?"),
    ONE_OR_MORE("+"),
    ZERO_OR_MORE("*");

    fun canBeMulti(): Boolean {
        return when (this) {
            NON_NULL, NULLABLE -> false
            ONE_OR_MORE, ZERO_OR_MORE -> true
        }
    }

    companion object {
        @JvmStatic
        val DEFAULT = NON_NULL

        @JvmStatic
        fun fromTypeString(typeString: String): RefType {
            if (typeString.isEmpty()) {
                return DEFAULT
            }
            for (refType in entries) {
                if (refType.typeString == typeString) {
                    return refType
                }
            }
            throw IllegalStateException("Cannot find a RefType with \"$typeString\"")
        }
    }
}