package com.github.xyzboom.bf.def

import kotlinx.serialization.Serializable

@Serializable
class Statement(
    val name: String,
    val contents: List<ReferenceList>
)