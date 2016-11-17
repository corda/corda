package net.corda.vega.api

/**
 * A small JSON DSL to create structures for APIs on the fly that mimic JSON in structure.
 * Use: json { obj("a" to 100, "b" to "hello", "c" to arr(1, 2, "c")) }
 */
class JsonBuilder {
    fun obj(vararg objs: Pair<String, Any>): Map<String, Any> {
        return objs.toMap()
    }

    fun arr(vararg objs: Any): List<Any> {
        return objs.toList()
    }
}

fun json(body: JsonBuilder.() -> Map<String, Any>): Map<String, Any> {
    val jsonWrapper = JsonBuilder()
    return jsonWrapper.body()
}
