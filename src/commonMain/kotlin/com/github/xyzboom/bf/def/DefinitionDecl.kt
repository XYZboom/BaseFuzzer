package com.github.xyzboom.bf.def

@Target(AnnotationTarget.PROPERTY)
annotation class DefinitionDecl(val defValue: String) {
    companion object {
        const val FULL_NAME = "com.github.xyzboom.bf.def.DefinitionDecl"
        const val NAME = "DefinitionDecl"
    }
}
