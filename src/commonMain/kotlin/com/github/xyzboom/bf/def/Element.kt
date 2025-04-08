package com.github.xyzboom.bf.def

import kotlin.jvm.JvmStatic

open class Element(
    val name: String,
    val references: List<Reference>,
) {
    companion object {
        @JvmStatic
        fun createFrom(name: String, rawRef: Any): Element {
//            return Element(name, Reference.createFrom(rawRef))
            return Element(name, emptyList())
        }
    }
}