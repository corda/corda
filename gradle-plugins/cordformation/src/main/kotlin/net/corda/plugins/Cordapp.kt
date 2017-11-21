package net.corda.plugins

import org.gradle.api.tasks.Input
import javax.inject.Inject

open class Cordapp @Inject constructor() {
    var coordinates: String? = null
    var config: String? = null
}