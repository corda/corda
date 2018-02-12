package net.corda.launcher

import java.net.URL
import java.net.URLClassLoader

class Loader(parent: ClassLoader?):
        URLClassLoader(Settings.CLASSPATH.toTypedArray(), parent) {

    fun augmentClasspath(urls: List<URL>) {
        urls.forEach { addURL(it) }
    }
}
