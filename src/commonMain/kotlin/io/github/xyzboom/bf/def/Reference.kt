package io.github.xyzboom.bf.def

import kotlinx.serialization.Serializable

@Serializable
data class Reference(
    val name: String,
    val type: RefType
)