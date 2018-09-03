package net.corda.client.jfx.utils

import javafx.beans.Observable
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableListBase

/**
 * [ChosenList] manages an [ObservableList] that may be changed by the wrapping [ObservableValue]. Whenever the underlying
 * [ObservableValue] changes the exposed list changes to the new value. Changes to the list are simply propagated.
 *
 * Example:
 *   val filteredStates = ChosenList(EasyBind.map(filterCriteriaType) { type ->
 *     when (type) {
 *       is (ByCurrency) -> statesFilteredByCurrency
 *       is (ByIssuer) -> statesFilteredByIssuer
 *     }
 *   })
 *
 * The above will create a list that chooses and delegates to the appropriate filtered list based on the type of filter.
 */
class ChosenList<E>(
        private val chosenListObservable: ObservableValue<out ObservableList<out E>>,
        private val logicalName: String? = null
) : ObservableListBase<E>() {

    private var currentList = chosenListObservable.value

    private val listener = ListChangeListener<E> { change -> fireChange(change) }

    init {
        chosenListObservable.addListener { _: Observable -> rechoose() }
        currentList.addListener(listener)
        beginChange()
        nextAdd(0, currentList.size)
        endChange()
    }

    override fun get(index: Int): E = currentList[index]
    override val size: Int get() = currentList.size

    private fun rechoose() {
        val chosenList = chosenListObservable.value
        if (currentList != chosenList) {
            pick(chosenList)
        }
    }

    private fun pick(list: ObservableList<out E>) {
        currentList.removeListener(listener)
        list.addListener(listener)
        beginChange()
        nextRemove(0, currentList)
        currentList = list
        nextAdd(0, list.size)
        endChange()
    }

    override fun toString(): String {
        return "ChosenList: $logicalName"
    }
}