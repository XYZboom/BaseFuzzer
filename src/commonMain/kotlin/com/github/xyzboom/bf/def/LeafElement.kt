package com.github.xyzboom.bf.def

open class LeafElement(name: String): Element(name, emptyList()) {
    companion object {
        const val BUILT_IN_LEAF_NAME = "~leaf"
    }
}