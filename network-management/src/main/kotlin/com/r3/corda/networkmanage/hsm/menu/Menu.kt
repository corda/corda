/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.menu

/**
 * Single menu item described by the selection [key], label associated with this item, action to be executed on response
 * to the item selection and termination flag used to decide whether to loop after action completion.
 */
data class MenuItem(val key: String, val label: String, val action: () -> Unit, val isTerminating: Boolean = false)

/**
 * A generic menu class for console based interactions with user.
 * Inspired by https://github.com/bryandh/genericmenu, but adjusted to our the particular needs of this project.
 */
class Menu {
    private val items = mutableMapOf<String, MenuItem>()
    private var quit: Pair<String, String>? = null
    private var exceptionHandler: (exception: Exception) -> Unit = { exception -> println(exception.message) }

    /**
     * Adds a menu item to the list of the menu items. The order in which the items are added matters.
     * If the exit option is set (@see [setExitOption] then it will be displayed always as the last element in the menu.
     *
     * @param key itemization label. E.g. If you pass "1", it will be displayed as [1].
     * @param label menu item label
     * @param action lambda that is executed when user selects this option.
     * @param isTerminating flag that specifies whether the completion of the action should exit this menu.
     */
    fun addItem(key: String, label: String, action: () -> Unit, isTerminating: Boolean = false): Menu {
        items[key.toLowerCase()] = MenuItem(key, label, action, isTerminating)
        return this
    }

    /**
     * Assigns the exception handler for this menu. The exception handler is invoked every time
     * any of the menu item selection handler function throws an exception.
     *
     */
    fun withExceptionHandler(handler: (exception: Exception) -> Unit): Menu {
        exceptionHandler = handler
        return this
    }

    /**
     * Sets the exit option with given key and label.
     */
    fun setExitOption(key: String, label: String): Menu {
        quit = Pair(key, label)
        return this
    }

    /**
     * Removes exit option from the list of the menu items.
     */
    fun unsetExitOption(): Menu {
        quit = null
        return this
    }

    private fun printItems() {
        items.forEach { _, (key, label, _) -> println("[$key] $label") }
        if (quit != null) println("[${quit?.first}] ${quit?.second}")
    }

    private fun readInput(): String? {
        print("> ")
        return readLine()
    }

    private fun run(key: String): Boolean {
        val selected = items[key.toLowerCase()]
        if (selected == null) {
            throw IllegalArgumentException("No valid option for $key found, try again.")
        } else {
            selected.action()
            return selected.isTerminating
        }
    }

    /**
     * Shows the menu built out of the given menu items and (if present) exit option.
     */
    fun showMenu() {
        while (true) {
            printItems()
            val choice = readInput()
            if (choice != null) {
                if ((quit != null) && choice.toLowerCase() == quit!!.first.toLowerCase()) {
                    break
                } else {
                    try {
                        if (run(choice)) {
                            break
                        }
                    } catch (exception: Exception) {
                        exceptionHandler(exception)
                    }
                }
            } else {
                // No more input
                break
            }
        }
    }
}