package foo.bar.sandbox

class StrictFloat : Callable {
    override fun call() {
        val d = java.lang.Double.MIN_VALUE
        val x = d / 2 * 2
        assert(x.toString() == "0.0")
    }
}

