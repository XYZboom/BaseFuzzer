package com.github.xyzboom.bf.def

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class ReferenceList(
    val references: List<Reference>
): Iterable<Reference> by references