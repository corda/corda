// (C) 2016 R3 CEV Ltd
//
// Platform implementation for an Intel SGX enclave, which is similar to having no platform at all.

#include <string.h>
#include <map>
#include <sys/time.h>
#include <avian/arch.h>
#include <avian/append.h>

#include <avian/system/system.h>
#include <avian/system/signal.h>
#include <avian/system/memory.h>
#include <avian/util/math.h>

#define PATH_MAX 256

extern std::map<std::string, void*> dispatch_table;
extern void init_sgx_dispatch_table();

using namespace vm;
using namespace avian::util;

// Weak link against the JAR functions that the Avian embedder must provide.
// This lets us look it up at runtime without having it be present in libavian.a
extern "C" __attribute__((weak)) const uint8_t* embedded_file_boot_jar(size_t* size);
extern "C" __attribute__((weak)) const uint8_t* embedded_file_app_jar(size_t* size);

extern "C" const uint8_t* javahomeJar(size_t* size);

extern "C" const void *find_in_dispatch_table(const char *name);

namespace {
    void abort_with(const char *msg) {
        printf("%s\n", msg);
        while(true);
    }

    class MySystem;
    MySystem* globalSystem;
    const bool Verbose = false;

    class MySystem : public System {
    public:
        class Thread : public System::Thread {
        public:
            Thread* next;

            Thread(System* s UNUSED, System::Runnable* r UNUSED) : next(0)
            {
            }

            virtual void interrupt()
            {
                printf("Thread::Interrupt()\n");
            }

            virtual bool getAndClearInterrupted()
            {
                printf("Thread::getAndClearInterrupted()\n");
                return false;
            }

            virtual void join()
            {
                printf("Thread::Join()\n");
            }

            virtual void dispose()
            {
            }
        };

        class Mutex : public System::Mutex {
        public:
            Mutex(System* s) : s(s)
            {

            }

            virtual void acquire()
            {

            }

            virtual void release()
            {

            }

            virtual void dispose()
            {

            }

            System* s;
        };

        class Monitor : public System::Monitor {
        public:
            Monitor(System* s) : s(s), owner_(0), first(0), last(0), depth(0)
            {

            }

            virtual bool tryAcquire(System::Thread* context UNUSED)
            {
                return true;
            }

            virtual void acquire(System::Thread* context UNUSED)
            {
            }

            virtual void release(System::Thread* context UNUSED)
            {
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

            virtual void wait(System::Thread* context UNUSED, int64_t time UNUSED)
            {
                wait(context, time, false);
            }

            virtual bool waitAndClearInterrupted(System::Thread* context UNUSED, int64_t time UNUSED)
            {
                return wait(context, time, true);
            }

            bool wait(System::Thread* context UNUSED, int64_t time UNUSED, bool clearInterrupted UNUSED)
            {
                abort_with("STUB: Thread::Wait()");
                return true;
            }

            void doNotify(Thread* t UNUSED)
            {
                printf("STUB: Thread::Notify()\n");
            }

            virtual void notify(System::Thread* context UNUSED)
            {
            }

            virtual void notifyAll(System::Thread* context UNUSED)
            {
            }

            virtual System::Thread* owner()
            {
                return owner_;
            }

            virtual void dispose()
            {
                expect(s, owner_ == 0);
                ::free(this);
            }

            System* s;
            Thread* owner_;
            Thread* first;
            Thread* last;
            unsigned depth;
        };

        class Local : public System::Local {
        public:
            void *value;

            Local(System* s) : s(s)
            {
            }

            virtual void* get()
            {
                return value;
            }

            virtual void set(void* p)
            {
                value = p;
            }

            virtual void dispose()
            {
                ::free(this);
            }

            System* s;
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
                } else if ((ptr = find_in_dispatch_table(function))) {
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
            Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
            r->attach(t);
            return 0;
        }

        virtual Status start(Runnable* r)
        {
            Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
            r->attach(t);
            printf("System::start (thread!!)\n");
            // We implement threads as blocking calls! This is of course totally wrong, but with extra threads
            // patched out in a few places, it's hopefully sufficient.
            r->run();
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
}  // namespace

namespace vm {
    AVIAN_EXPORT System* makeSystem(bool reentrant)
    {
        return new (malloc(sizeof(MySystem))) MySystem(reentrant);
    }
}  // namespace vm
