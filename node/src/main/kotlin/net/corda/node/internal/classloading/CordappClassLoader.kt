package net.corda.node.internal.classloading

import java.net.URL

class CordappClassLoader(val version: Int, urls: Array<URL>): ParentLastClassLoader(urls)