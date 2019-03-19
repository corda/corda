package foo.bar.sandbox

class A : Callable {
    override fun call() {
        synchronized(this) {
            return
        }
    }
}
