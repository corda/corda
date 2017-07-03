package net.corda.node.internal.classloading

import java.net.URL
import java.net.URLClassLoader

open class ParentLastClassLoader(urls: Array<URL>) : URLClassLoader(urls) {
    @Synchronized
    override fun loadClass(className: String, resolve: Boolean): Class<*> {
        return try {
            findLoadedClass(className) ?: super.findClass(className)
        } catch (e: ClassNotFoundException) {
            super.loadClass(className, resolve)
        }
    }
}