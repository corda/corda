// (C) 2016 R3 CEV Ltd
//
// Platform implementation for an Intel SGX enclave, which is similar to having no platform at all.

#include <string.h>
#include <map>
#include <dlfcn.h>
#include <sys/time.h>
#include <avian/arch.h>
#include <avian/append.h>

#include <avian/system/system.h>
#include <avian/system/signal.h>
#include <avian/system/memory.h>
#include <avian/util/math.h>

#include <sgx_thread_completion.h>

#define PATH_MAX 256
#define ACQUIRE(x) MutexResource MAKE_NAME(mutexResource_)(x)

using namespace vm;
using namespace avian::util;

// Weak link against the JAR functions that the Avian embedder must provide.
// This lets us look it up at runtime without having it be present in libavian.a
extern "C" __attribute__((weak)) const uint8_t* embedded_file_boot_jar(size_t* size);
extern "C" __attribute__((weak)) const uint8_t* embedded_file_app_jar(size_t* size);

extern "C" const uint8_t* javahomeJar(size_t* size);

typedef struct _thread_data_t thread_data_t;
extern "C" __attribute__((weak)) thread_data_t* start_thread(void (*routine)(void *), void *param, sgx_thread_completion *completion);
extern "C" __attribute__((weak)) thread_data_t* get_thread_data();

static void run(void* r)
{
  static_cast<System::Runnable*>(r)->run();
}

namespace {
    class MutexResource {
    public:
      MutexResource(sgx_thread_mutex_t& m) noexcept : _m(&m)
      {
        sgx_thread_mutex_lock(_m);
      }

      ~MutexResource() noexcept
      {
        sgx_thread_mutex_unlock(_m);
      }

    private:
      sgx_thread_mutex_t* _m;
    };

    void abort_with(const char *msg) {
        printf("%s\n", msg);
        while(true);
    }

    class MySystem;
    MySystem* globalSystem;
    const bool Verbose = false;
    const unsigned Notified = 1 << 0;

    class MySystem : public System {
    public:
        class Thread : public System::Thread {
        public:
            Thread(System* s, System::Runnable* r) : s(s), r(r), next(0), flags(0)
            {
                sgx_thread_mutex_init(&mutex, 0);
                sgx_thread_cond_init(&condition, 0);
            }

            virtual void interrupt()
            {
                ACQUIRE(mutex);

                r->setInterrupted(true);

                int rv UNUSED = sgx_thread_cond_signal(&condition);
                expect(s, rv == 0);
            }

            virtual bool getAndClearInterrupted()
            {
                ACQUIRE(mutex);

                bool interrupted = r->interrupted();

                r->setInterrupted(false);

                return interrupted;
            }

            virtual void join()
            {
                completion.wait();
            }

            virtual void dispose()
            {
                sgx_thread_mutex_destroy(&mutex);
                sgx_thread_cond_destroy(&condition);
                ::free(this);
            }

            thread_data_t* thread;
            sgx_thread_completion completion;

            /*
             * The mutex protects this thread object's internal
             * "state", and the condition wakes the thread when
             * it is waiting on a monitor lock.
             */
            sgx_thread_mutex_t mutex;
            sgx_thread_cond_t condition;

            System* s;
            System::Runnable* r;
            Thread* next;
            unsigned flags;
        };

        class Mutex : public System::Mutex {
        public:
            Mutex(System* s) : s(s)
            {
                sgx_thread_mutex_init(&mutex, 0);
            }

            virtual void acquire()
            {
                sgx_thread_mutex_lock(&mutex);
            }

            virtual void release()
            {
                sgx_thread_mutex_unlock(&mutex);
            }

            virtual void dispose()
            {
                sgx_thread_mutex_destroy(&mutex);
                ::free(this);
            }

        private:
            System* s;
            sgx_thread_mutex_t mutex;
        };

        class Monitor : public System::Monitor {
        public:
            Monitor(System* s) : s(s), owner_(0), first(0), last(0), depth(0)
            {
                sgx_thread_mutex_init(&mutex, 0);
            }

            virtual bool tryAcquire(System::Thread* context)
            {
                Thread* t = static_cast<Thread*>(context);

                if (owner_ == t) {
                    ++depth;
                    return true;
                } else {
                    switch (sgx_thread_mutex_trylock(&mutex)) {
                    case EBUSY:
                        return false;

                    case 0:
                        owner_ = t;
                        ++depth;
                        return true;

                    default:
                        sysAbort(s);
                    }
                }
            }

            virtual void acquire(System::Thread* context)
            {
                Thread* t = static_cast<Thread*>(context);

                if (owner_ != t) {
                    sgx_thread_mutex_lock(&mutex);
                    owner_ = t;
                }
                ++depth;
            }

            virtual void release(System::Thread* context)
            {
                Thread* t = static_cast<Thread*>(context);

                if (owner_ == t) {
                    if (--depth == 0) {
                        owner_ = 0;
                        sgx_thread_mutex_unlock(&mutex);
                    }
                } else {
                    sysAbort(s);
                }
            }

            void append(Thread* t)
            {
                for (Thread* x = first; x; x = x->next) {
                    expect(s, t != x);
                }

                if (last) {
                    expect(s, t != last);
                    last->next = t;
                    last = t;
                } else {
                    first = last = t;
                }
            }

            void remove(Thread* t)
            {
                Thread* previous = 0;
                for (Thread* current = first; current;) {
                    if (t == current) {
                        if (current == first) {
                            first = t->next;
                        } else {
                            expect(s, previous != t->next);
                            previous->next = t->next;
                        }

                        if (current == last) {
                            last = previous;
                        }

                        t->next = 0;

                        break;
                    } else {
                        previous = current;
                        current = current->next;
                    }
                }

                for (Thread* x = first; x; x = x->next) {
                    expect(s, t != x);
                }
            }

            virtual void wait(System::Thread* context, int64_t time)
            {
                wait(context, time, false);
            }

            virtual bool waitAndClearInterrupted(System::Thread* context, int64_t time)
            {
                return wait(context, time, true);
            }

            bool wait(System::Thread* context, int64_t time UNUSED, bool clearInterrupted)
            {
                Thread* t = static_cast<Thread*>(context);

                if (owner_ == t) {
                    // Initialized here to make gcc 4.2 a happy compiler
                    bool interrupted = false;
                    bool notified = false;
                    unsigned depth = 0;

                    {
                        ACQUIRE(t->mutex);

                        expect(s, (t->flags & Notified) == 0);

                        interrupted = t->r->interrupted();
                        if (interrupted and clearInterrupted) {
                            t->r->setInterrupted(false);
                        }

                        append(t);

                        depth = this->depth;
                        this->depth = 0;
                        owner_ = 0;
                        sgx_thread_mutex_unlock(&mutex);

                        if (not interrupted) {
                            int rv UNUSED = sgx_thread_cond_wait(&(t->condition), &(t->mutex));
                            expect(s, rv == 0 or rv == EINTR);

                            interrupted = t->r->interrupted();
                            if (interrupted and clearInterrupted) {
                                t->r->setInterrupted(false);
                            }
                        }

                        notified = ((t->flags & Notified) != 0);
                    }

                    sgx_thread_mutex_lock(&mutex);

                    {
                        ACQUIRE(t->mutex);
                        t->flags = 0;
                    }

                    if (not notified) {
                        remove(t);
                    } else {
#ifndef NDEBUG
                        for (Thread* x = first; x; x = x->next) {
                            expect(s, t != x);
                        }
#endif
                    }

                    t->next = 0;

                    owner_ = t;
                    this->depth = depth;

                    return interrupted;
                } else {
                    sysAbort(s);
                }
            }

            void doNotify(Thread* t)
            {
                ACQUIRE(t->mutex);

                t->flags |= Notified;
                int rv UNUSED = sgx_thread_cond_signal(&(t->condition));
                expect(s, rv == 0);
            }

            virtual void notify(System::Thread* context)
            {
                Thread* t = static_cast<Thread*>(context);

                if (owner_ == t) {
                    if (first) {
                        Thread* t = first;
                        first = first->next;
                        if (t == last) {
                            expect(s, first == 0);
                            last = 0;
                        }

                        doNotify(t);
                    }
                } else {
                    sysAbort(s);
                }
            }

            virtual void notifyAll(System::Thread* context)
            {
                Thread* t = static_cast<Thread*>(context);

                if (owner_ == t) {
                    for (Thread* t = first; t; t = t->next) {
                        doNotify(t);
                    }
                    first = last = 0;
                } else {
                    sysAbort(s);
                }
            }

            virtual System::Thread* owner()
            {
                return owner_;
            }

            virtual void dispose()
            {
                expect(s, owner_ == 0);
                sgx_thread_mutex_destroy(&mutex);
                ::free(this);
            }

        private:
            System* s;
            sgx_thread_mutex_t mutex;
            Thread* owner_;
            Thread* first;
            Thread* last;
            unsigned depth;
        };

        // This implementation of thread-local storage
        // for SGX only works because we only create
        // one instance of this class.
        class Local : public System::Local {
        public:
            Local(System* s) : s(s)
            {
            }

            virtual void* get()
            {
                return data;
            }

            virtual void set(void* p)
            {
                expect(s, data == NULL);
                data = p;
            }

            virtual void dispose()
            {
                ::free(this);
            }

        private:
            System* s;
            // Requires __get_tls_addr() in libsgx_trts
            static thread_local void *data;
        };

        class Region : public System::Region {
        public:
            Region(System* s, uint8_t* start, size_t length)
                    : s(s), start_(start), length_(length)
            {
            }

            virtual const uint8_t* start()
            {
                return start_;
            }

            virtual size_t length()
            {
                return length_;
            }

            virtual void dispose()
            {
                if (start_) {
                    printf("STUB: munmap\n");
                    //munmap(start_, length_);
                }
                ::free(this);
            }

        private:
            System* s;
            uint8_t* start_;
            size_t length_;
        };

        class Directory : public System::Directory {
        public:
            Directory(System* s, void* directory UNUSED) : s(s)
            {
            }

            virtual const char* next()
            {
                return 0;
            }

            virtual void dispose()
            {
                ::free(this);
            }

            System* s;
        };

        class Library : public System::Library {
        public:
            Library(System* s UNUSED) : next_(0) {}

            virtual void* resolve(const char* function)
            {
                const void *ptr = NULL;
                if (!strcmp(function, "embedded_file_boot_jar")) {
                    return (void *) &embedded_file_boot_jar;
                } if (!strcmp(function, "embedded_file_app_jar")) {
                    return (void *) &embedded_file_app_jar;
                } if (!strcmp(function, "javahomeJar")) {
                    return (void *) &javahomeJar;
                } else if ((ptr = dlsym(NULL, function))) {
                    return (void *) ptr;
                } else {
                    // If you seem to be hitting a JNI call you're sure should exist, try uncommenting this.
                    // It is expected that some resolutions won't work as multiple names are tried for each
                    // native call, which is why we don't spam them all to the logs here.
                    //
                    // printf("Could not resolve file/function %s, check dispatch tables\n", function);
                    return NULL;
                }
            }

            virtual const char* name()
            {
                return "main";
            }

            virtual System::Library* next()
            {
                return next_;
            }

            virtual void setNext(System::Library* lib)
            {
                next_ = lib;
            }

            virtual void disposeAll()
            {
                if (next_) {
                    next_->disposeAll();
                }

                ::free(this);
            }

        private:
            System* s;
            System::Library* next_;
        };

        MySystem(bool reentrant) : reentrant(reentrant), threadVisitor(0), visitTarget(0)
        {
            if (not reentrant) {
                expect(this, globalSystem == 0);
                globalSystem = this;
                expect(this, make(&visitLock) == 0);
            }
        }

        bool unregisterHandler(int index UNUSED)
        {
            return true;
        }

        // Returns true on success, false on failure
        bool registerHandler(int index UNUSED)
        {
            printf("System::registerHandler(%d)\n", index);
            return true;
        }

        virtual void* tryAllocate(size_t sizeInBytes)
        {
            return malloc(sizeInBytes);
        }

        virtual void free(const void* p)
        {
            if (p)
                ::free(const_cast<void*>(p));
        }

        virtual bool success(Status s) {
            return s == 0;
        }

        virtual Status attach(Runnable* r)
        {
            // This system thread will never be joined because it was not
            // created using startThread() and so does not have JoinFlag set.
            Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
            t->thread = get_thread_data();
            r->attach(t);
            return 0;
        }

        virtual Status start(Runnable* r)
        {
            Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
            r->attach(t);
            t->thread = start_thread(&run, r, &t->completion);
            return 0;
        }

        virtual Status make(System::Mutex** m)
        {
            *m = new (allocate(this, sizeof(Mutex))) Mutex(this);
            return 0;
        }

        virtual Status make(System::Monitor** m)
        {
            *m = new (allocate(this, sizeof(Monitor))) Monitor(this);
            return 0;
        }

        virtual Status make(System::Local** l)
        {
            *l = new (allocate(this, sizeof(Local))) Local(this);
            return 0;
        }

        virtual Status visit(System::Thread* st UNUSED,
                             System::Thread* sTarget UNUSED,
                             ThreadVisitor* visitor UNUSED)
        {
            printf("System::visit (threads)\n");
            return 0;
        }

        virtual Status map(System::Region** region UNUSED, const char* name)
        {
            printf("System::map(%s)\n", name);
            return 0;
        }

        virtual Status open(System::Directory** directory UNUSED, const char* name)
        {
            printf("System::open(%s)\n", name);
            return 1;
        }

        virtual FileType stat(const char* name, size_t* length)
        {
            // Avian does a stat on the current directory during startup but doesn't seem to care about the result,
            // so suppress stub logging of stat(".")
            if (strcmp(name, "."))
                printf("System::stat(%s)\n", name);
            *length = 0;
            return TypeDoesNotExist;
        }

        virtual const char* libraryPrefix()
        {
            return SO_PREFIX;
        }

        virtual const char* librarySuffix()
        {
            return SO_SUFFIX;
        }

        virtual const char* toAbsolutePath(AllocOnly* allocator, const char* name)
        {
            return copy(allocator, name);
        }

        virtual Status load(System::Library** lib, const char* name)
        {
            if (name != NULL) {
                printf("System::load(%s)", name);
                while(1);
            }

            // Request to get a System::Library for the main process.
            *lib = new (allocate(this, sizeof(Library))) Library(this);
            return 0;
        }

        virtual char pathSeparator()
        {
            return ':';
        }

        virtual char fileSeparator()
        {
            return '/';
        }

        virtual int64_t now()
        {
            timeval tv = {0, 0};
            gettimeofday(&tv, 0);
            return (static_cast<int64_t>(tv.tv_sec) * 1000) + (static_cast<int64_t>(tv.tv_usec) / 1000);
        }

        virtual void yield()
        {

        }

        virtual void exit(int code UNUSED)
        {
            abort_with("exit()");
        }

        virtual void abort()
        {
            abort_with("abort!");
        }

        virtual void dispose()
        {
            if (not reentrant) {
                visitLock->dispose();
                globalSystem = 0;
            }

            ::free(this);
        }

        bool reentrant;
        ThreadVisitor* threadVisitor;
        Thread* visitTarget;
        System::Monitor* visitLock;
    };

    thread_local void* MySystem::Local::data;
}  // namespace

namespace vm {
    AVIAN_EXPORT System* makeSystem(bool reentrant)
    {
        return new (malloc(sizeof(MySystem))) MySystem(reentrant);
    }
}  // namespace vm
