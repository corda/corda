void __cxa_finalize(void *d ) { (void)d; };

extern void* __dso_handle;

__attribute((destructor))
static void cleanup(void) {
  __cxa_finalize(&__dso_handle);
}
