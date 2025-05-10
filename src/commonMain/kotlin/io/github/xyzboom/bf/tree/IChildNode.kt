package io.github.xyzboom.bf.tree

/**
 * Type for child property. Use [ITreeChild] in tree.
 */
sealed interface IChildNode<T>

class Nullable<T>(val value: T?) : IChildNode<T>

class NotNull<T : Any>(val value: T) : IChildNode<T>

class OneOrMore<T>(val values: MutableList<T> = mutableListOf()) : MutableList<T> by values, IChildNode<T>

class ZeroOrMore<T>(val values: MutableList<T> = mutableListOf()) : MutableList<T> by values, IChildNode<T>
