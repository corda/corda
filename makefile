#MAKEFLAGS = -s

bld = build
src = src
inp = input

cxx = g++
cc = gcc
vg = nice valgrind --leak-check=full --num-callers=32 --db-attach=yes \
	--freelist-vol=100000000
javac = javac

warnings = -Wall -Wextra -Werror -Wold-style-cast -Wunused-parameter \
	-Winit-self -Wconversion

slow = -O0 -g3
fast = -Os -DNDEBUG

#thread-cflags = -DNO_THREADS
thread-cflags = -pthread
thread-lflags = -lpthread

cflags = $(warnings) -fPIC -fno-rtti -fno-exceptions -fvisibility=hidden \
	-I$(src) -I$(bld) $(thread-cflags)
lflags = $(thread-lflags)
test-cflags = -DDEBUG_MEMORY
stress-cflags = -DDEBUG_MEMORY -DDEBUG_MEMORY_MAJOR

cpp-objects = $(foreach x,$(1),$(patsubst $(2)/%.cpp,$(bld)/%.o,$(x)))

stdcpp-sources = $(src)/stdc++.cpp
stdcpp-objects = $(call cpp-objects,$(stdcpp-sources),$(src))
stdcpp-cflags = $(fast) $(cflags)

generated-code = \
	$(bld)/type-enums.cpp \
	$(bld)/type-declarations.cpp \
	$(bld)/type-constructors.cpp \
	$(bld)/type-initializations.cpp
interpreter-depends = \
	$(generated-code) \
	$(src)/common.h \
	$(src)/system.h \
	$(src)/heap.h \
	$(src)/class_finder.h \
	$(src)/stream.h \
	$(src)/constants.h \
	$(src)/vm.h
interpreter-sources = \
	$(src)/vm.cpp \
	$(src)/heap.cpp \
	$(src)/main.cpp
interpreter-objects = $(call cpp-objects,$(interpreter-sources),$(src))
interpreter-cflags = $(slow) $(cflags)

generator-headers = \
	$(src)/input.h \
	$(src)/output.h
generator-sources = \
	$(src)/type-generator.cpp
generator-objects = $(call cpp-objects,$(generator-sources),$(src))
generator-executable = $(bld)/generator
generator-cflags = $(slow) $(cflags)

executable = $(bld)/vm

test-objects = $(patsubst $(bld)/%,$(bld)/test-%,$(interpreter-objects))
test-executable = $(bld)/test-vm

stress-objects = $(patsubst $(bld)/%,$(bld)/stress-%,$(interpreter-objects))
stress-executable = $(bld)/stress-vm

fast-objects = $(patsubst $(bld)/%,$(bld)/fast-%,$(interpreter-objects))
fast-executable = $(bld)/fast-vm
fast-cflags = $(fast) $(cflags)

input = $(bld)/classes/Test.class
input-depends = \
	$(bld)/classes/java/lang/Object.class \
	$(bld)/classes/java/lang/Class.class \
	$(bld)/classes/vm/VM.class

gen-run-arg = $(shell echo $(1) | sed -e 's:$(bld)/classes/\(.*\)\.class:\1:')
args = -cp $(bld)/classes -hs 67108864 $(call gen-run-arg,$(input))

.PHONY: build
build: $(executable)

$(input): $(input-depends)

.PHONY: run
run: $(executable) $(input)
	$(<) $(args)

.PHONY: debug
debug: $(executable) $(input)
	gdb --args $(<) $(args)

.PHONY: fast
fast: $(fast-executable)
	ls -lh $(<)

.PHONY: vg
vg: $(executable) $(input)
	$(vg) $(<) $(args)

.PHONY: test
test: $(test-executable) $(input)
	$(vg) $(<) $(args)

.PHONY: stress
stress: $(stress-executable) $(input)
	$(vg) $(<) $(args)

.PHONY: run-all
run-all: $(executable)
	set -e; for x in $(all-input); do echo "$$x:"; $(<) $$x; echo; done

.PHONY: vg-all
vg-all: $(executable)
	set -e; for x in $(all-input); do echo "$$x:"; $(vg) -q $(<) $$x; done

.PHONY: test-all
test-all: $(test-executable)
	set -e; for x in $(all-input); do echo "$$x:"; $(vg) -q $(<) $$x; done

.PHONY: stress-all
stress-all: $(stress-executable)
	set -e; for x in $(all-input); do echo "$$x:"; $(vg) -q $(<) $$x; done

.PHONY: clean
clean:
	@echo "removing $(bld)"
	rm -r $(bld)

gen-arg = $(shell echo $(1) | sed -e 's:$(bld)/type-\(.*\)\.cpp:\1:')
$(generated-code): %.cpp: $(src)/types.def $(generator-executable)
	@echo "generating $(@)"
	$(generator-executable) $(call gen-arg,$(@)) < $(<) > $(@)

$(bld)/type-generator.o: \
	$(generator-headers)

$(bld)/classes/%.class: $(inp)/%.java
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(javac) -bootclasspath $(inp) -classpath $(inp) -d $(bld)/classes $(<)

$(stdcpp-objects): $(bld)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(stdcpp-cflags) -c $(<) -o $(@)

$(interpreter-objects): $(bld)/%.o: $(src)/%.cpp $(interpreter-depends)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(interpreter-cflags) -c $(<) -o $(@)

$(test-objects): $(bld)/test-%.o: $(src)/%.cpp $(interpreter-depends)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(interpreter-cflags) $(test-cflags) -c $(<) -o $(@)

$(stress-objects): $(bld)/stress-%.o: $(src)/%.cpp $(interpreter-depends)
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(interpreter-cflags) $(stress-cflags) -c $(<) -o $(@)

$(generator-objects): $(bld)/%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(generator-cflags) -c $(<) -o $(@)

$(fast-objects): $(bld)/fast-%.o: $(src)/%.cpp
	@echo "compiling $(@)"
	@mkdir -p $(dir $(@))
	$(cxx) $(fast-cflags) -c $(<) -o $(@)

$(executable): $(interpreter-objects) $(stdcpp-objects)
	@echo "linking $(@)"
	$(cc) $(lflags) $(^) -o $(@)

$(test-executable): $(test-objects) $(stdcpp-objects)
	@echo "linking $(@)"
	$(cc) $(lflags) $(^) -o $(@)

$(stress-executable): $(stress-objects) $(stdcpp-objects)
	@echo "linking $(@)"
	$(cc) $(lflags) $(^) -o $(@)

$(fast-executable): $(fast-objects) $(stdcpp-objects)
	@echo "linking $(@)"
	$(cc) $(lflags) $(^) -o $(@)
	strip --strip-all $(@)

.PHONY: generator
generator: $(generator-executable)

.PHONY: run-generator
run-generator: $(generator-executable)
	$(<) < $(src)/types.def

.PHONY: vg-generator
vg-generator: $(generator-executable)
	$(vg) $(<) < $(src)/types.def

$(generator-executable): $(generator-objects) $(stdcpp-objects)
	@echo "linking $(@)"
	$(cc) $(lflags) $(^) -o $(@)
