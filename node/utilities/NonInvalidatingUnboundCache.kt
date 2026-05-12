    constructor(name: String,
                cacheFactory: NamedCacheFactory,
                loadFunction: (K) -> Optional<V>,
                removalListener: RemovalListener<K, Any> = RemovalListener { _, _, _ -> },
                keysToPreload: () -> Iterable<K> = { emptyList() }) :
            this(buildCache(name, cacheFactory, loadFunction, removalListener, keysToPreload))

        private fun <K : Any, V : Any> buildCache(name: String,
                                                  cacheFactory: NamedCacheFactory,
                                                  loadFunction: (K) -> Optional<V>,
                                                  removalListener: RemovalListener<K, Any>,
                                                  keysToPreload: () -> Iterable<K>): LoadingCache<K, Optional<V>> {
            val builder = Caffeine.newBuilder().removalListener(removalListener).executor(SameThreadExecutor.getExecutor())
            return cacheFactory.buildNamed(builder, name, NonInvalidatingCacheLoader(loadFunction)).apply {
                getAll(keysToPreload())
            }
        }
