package com.r3corda.client.fxutils

import javafx.beans.Observable
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableListBase

/**
 * [ChosenList] is essentially a monadic join of an [ObservableValue] of an [ObservableList] into an [ObservableList].
 * Whenever the underlying [ObservableValue] changes the exposed list changes to the new value. Changes to the list are
 * simply propagated.
 */
class ChosenList<E>(
        private val chosenListObservable: ObservableValue<ObservableList<E>>
): ObservableListBase<E>() {

    private var currentList = chosenListObservable.value

    private val listener = object : ListChangeListener<E> {
        override fun onChanged(change: ListChangeListener.Change<out E>) = fireChange(change)
    }

    init {
        chosenListObservable.addListener { observable: Observable -> rechoose() }
        currentList.addListener(listener)
        beginChange()
        nextAdd(0, currentList.size)
        endChange()
    }

    override fun get(index: Int) = currentList.get(index)
    override val size: Int get() = currentList.size

    private fun rechoose() {
        val chosenList = chosenListObservable.value
        if (currentList != chosenList) {
            pick(chosenList)
        }
    }

    private fun pick(list: ObservableList<E>) {
        currentList.removeListener(listener)
        list.addListener(listener)
        beginChange()
        nextRemove(0, currentList)
        currentList = list
        nextAdd(0, list.size)
        endChange()
    }

}
