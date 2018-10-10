package foo.bar.sandbox

class B : Callable {
    override fun call() {
        var x = 0
        for (i in 1..30) {
            x += 1
        }
    }
}
