package com.github.xyzboom.bf.def

enum class RefType(val typeString: String) {
    NON_NULL("!"),
    NULLABLE("?"),
    ONE_OR_MORE("+"),
    ZERO_OR_MORE("*");
    companion object {
        val DEFAULT = NON_NULL
    }
}