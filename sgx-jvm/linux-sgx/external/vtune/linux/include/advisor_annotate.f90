! ========================================================================
!
! SAMPLE SOURCE CODE - SUBJECT TO THE TERMS OF END-USER LICENSE AGREEMENT
! FOR INTEL(R) ADVISOR XE 2016.
!
! Copyright (c) 2012-2015 Intel Corporation. All rights reserved.
!
! THIS FILE IS PROVIDED "AS IS" WITH NO WARRANTIES, EXPRESS OR IMPLIED,
! INCLUDING BUT NOT LIMITED TO ANY IMPLIED WARRANTY OF MERCHANTABILITY,
! FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT OF INTELLECTUAL
! PROPERTY RIGHTS.
!
! ========================================================================
!

!--------
!
! This file defines functions used by the Intel(R) Advisor XE
! "Dependencies Modeling" and "Suitability Modeling" analysis, which 
! are described in the "Annotations" section of the help.
!
! Version of the annotations.
! The presence of this macro serves to idetify the annotation definition
! file and the form of annotations.
! INTEL_ADVISOR_ANNOTATION_VERSION 1.0
!--------

module advisor_annotate
    use, intrinsic :: iso_c_binding, only: C_PTR, C_FUNPTR, C_INT, C_CHAR, C_NULL_CHAR, C_F_PROCPOINTER, C_LOC, C_ASSOCIATED
    implicit none
    
    !--------
    !
    ! Public interface
    !
    !--------
    
    public :: annotate_site_begin
    public :: annotate_site_end
    public :: annotate_task_begin
    public :: annotate_task_end
    public :: annotate_iteration_task
    public :: annotate_lock_acquire
    public :: annotate_lock_release
    public :: annotate_disable_observation_push
    public :: annotate_disable_observation_pop
    public :: annotate_disable_collection_push
    public :: annotate_disable_collection_pop
    public :: annotate_induction_uses
    public :: annotate_reduction_uses
    public :: annotate_observe_uses
    public :: annotate_clear_uses
    public :: annotate_aggregate_task

    interface annotate_induction_uses
        module procedure annotate_induction_uses_i2
        module procedure annotate_induction_uses_i4
        module procedure annotate_induction_uses_i8
        module procedure annotate_induction_uses_r4
        module procedure annotate_induction_uses_r8
        module procedure annotate_induction_uses_c4
        module procedure annotate_induction_uses_c8
    end interface annotate_induction_uses
    
    interface annotate_reduction_uses
        module procedure annotate_reduction_uses_i2
        module procedure annotate_reduction_uses_i4
        module procedure annotate_reduction_uses_i8
        module procedure annotate_reduction_uses_r4
        module procedure annotate_reduction_uses_r8
        module procedure annotate_reduction_uses_c4
        module procedure annotate_reduction_uses_c8
    end interface annotate_reduction_uses

    interface annotate_observe_uses
        module procedure annotate_observe_uses_i2
        module procedure annotate_observe_uses_i4
        module procedure annotate_observe_uses_i8
        module procedure annotate_observe_uses_r4
        module procedure annotate_observe_uses_r8
        module procedure annotate_observe_uses_c4
        module procedure annotate_observe_uses_c8
    end interface annotate_observe_uses
    
    interface annotate_clear_uses
        module procedure annotate_clear_uses_i2
        module procedure annotate_clear_uses_i4
        module procedure annotate_clear_uses_i8
        module procedure annotate_clear_uses_r4
        module procedure annotate_clear_uses_r8
        module procedure annotate_clear_uses_c4
        module procedure annotate_clear_uses_c8
    end interface annotate_clear_uses
    
    private
    
    !--------
    !
    ! Interfaces to the itt_notify entry points
    !
    !--------
    
    enum, bind(C)
        enumerator :: disable_observation
        enumerator :: disable_collection
    end enum
    
    abstract interface
    
        subroutine itt_proc_noargs() bind(C)
        end subroutine itt_proc_noargs
        
        subroutine itt_proc_with_name(name, len) bind(C)
            import
            character, dimension(*), intent(in) :: name
            integer(kind=C_INT), intent(in), value :: len
        end subroutine itt_proc_with_name
            
        subroutine itt_proc_with_int(intval) bind(C)
            import
            integer(kind=C_INT), intent(in), value :: intval
        end subroutine itt_proc_with_int
        
        subroutine itt_proc_with_disable(disable_kind) bind(C)
            import
            integer(kind=C_INT), intent(in), value :: disable_kind
        end subroutine itt_proc_with_disable
    
        subroutine itt_proc_with_addr_size(addr, size) bind(C)
            import
            type(C_PTR), intent(in), value :: addr
            integer(kind=C_INT), intent(in), value :: size
        end subroutine itt_proc_with_addr_size
        
    end interface
    
    !--------
    !
    ! Subroutine pointer variables to access the itt_notify entry points
    !
    !--------

!dec$ if defined(use_initialized_proc_ptrs)    
    procedure(itt_proc_with_name),      pointer :: site_begin       => site_begin_load
    procedure(itt_proc_noargs),         pointer :: site_end_2       => site_end_2_load
    procedure(itt_proc_with_name),      pointer :: task_begin       => task_begin_load
    procedure(itt_proc_noargs),         pointer :: task_end_2       => task_end_2_load
    procedure(itt_proc_noargs),         pointer :: iteration_task   => iteration_task_load
    procedure(itt_proc_with_int),       pointer :: lock_acquire_2   => lock_acquire_2_load
    procedure(itt_proc_with_int),       pointer :: lock_release_2   => lock_release_2_load
    procedure(itt_proc_with_disable),   pointer :: disable_push     => disable_push_load
    procedure(itt_proc_noargs),         pointer :: disable_pop      => disable_pop_load
    procedure(itt_proc_with_addr_size), pointer :: induction_uses   => induction_uses_load
    procedure(itt_proc_with_addr_size), pointer :: reduction_uses   => reduction_uses_load
    procedure(itt_proc_with_addr_size), pointer :: observe_uses     => observe_uses_load
    procedure(itt_proc_with_addr_size), pointer :: clear_uses       => clear_uses_load
    procedure(itt_proc_with_int),       pointer :: aggregate_task   => aggregate_task_load
!dec$ else
    procedure(itt_proc_with_name),      pointer :: site_begin
    procedure(itt_proc_noargs),         pointer :: site_end_2
    procedure(itt_proc_with_name),      pointer :: task_begin
    procedure(itt_proc_noargs),         pointer :: task_end_2
    procedure(itt_proc_with_name),      pointer :: iteration_task
    procedure(itt_proc_with_int),       pointer :: lock_acquire_2
    procedure(itt_proc_with_int),       pointer :: lock_release_2
    procedure(itt_proc_with_disable),   pointer :: disable_push
    procedure(itt_proc_noargs),         pointer :: disable_pop
    procedure(itt_proc_with_addr_size), pointer :: induction_uses
    procedure(itt_proc_with_addr_size), pointer :: reduction_uses
    procedure(itt_proc_with_addr_size), pointer :: observe_uses
    procedure(itt_proc_with_addr_size), pointer :: clear_uses
    procedure(itt_proc_with_int),       pointer :: aggregate_task
    
    logical :: initialized = .false.
!dec$ endif
    

    !--------
    !
    ! Functions for loading dynamic libraries
    !
    !--------
    
    interface

    !dec$ if defined(_WIN32)

        !DEC$OBJCOMMENT LIB:"KERNEL32.LIB"
        !DEC$OBJCOMMENT LIB:"advisor.lib"
    
        function load_library(file)
            import
            !dec$ attributes default, stdcall, decorate, alias : 'LoadLibraryA' :: load_library
            type(C_PTR) :: load_library
            character(kind=C_CHAR), dimension(*), intent(in) :: file
        end function load_library

        function get_library_entry(library, proc_name)
            import
            !dec$ attributes default, stdcall, decorate, alias : 'GetProcAddress' :: get_library_entry
            type(C_FUNPTR) :: get_library_entry
            type(C_PTR), intent(in), value :: library
            character(kind=C_CHAR), dimension(*), intent(in) :: proc_name
        end function get_library_entry

    !dec$ else

        function load_library(file, mode) bind(C, name="dlopen")
            import
            type(C_PTR) :: load_library
            character(kind=C_CHAR), dimension(*), intent(in) :: file
            integer(kind=C_INT), intent(in), value :: mode
        end function load_library

        function get_library_entry(library, proc_name) bind(C, name="dlsym")
            import
            type(C_FUNPTR) :: get_library_entry
            type(C_PTR), intent(in), value :: library
            character(kind=C_CHAR), dimension(*), intent(in) :: proc_name
        end function get_library_entry

    !dec$ endif

    end interface
    
contains

    !--------
    !
    ! The public interface subroutines just make sure the module has been initialized,
    ! and then make an indirect call through the corresponding pointer variables. 
    ! Initializing the module tries to load the itt notify library. If the library
    ! is loaded successfully, the variables are set to point to the corresponding 
    ! entries in the library. If the library load fails, the variables are set to point 
    ! to stub routines.
    !
    !--------
    
    subroutine annotate_site_begin(site_name)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_site_begin
        character(len=*), intent(in) :: site_name
        if (.not. initialized) call load_itt_library
        call site_begin(site_name, len(site_name))
    end subroutine annotate_site_begin

    subroutine annotate_site_end
        !DEC$ ATTRIBUTES DEFAULT :: annotate_site_end
        if (.not. initialized) call load_itt_library
        call site_end_2
    end subroutine annotate_site_end

    subroutine annotate_task_begin(task_name)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_task_begin
        character(len=*), intent(in) :: task_name
        if (.not. initialized) call load_itt_library
        call task_begin(task_name, len(task_name))
    end subroutine annotate_task_begin

    subroutine annotate_task_end
        !DEC$ ATTRIBUTES DEFAULT :: annotate_task_end
        if (.not. initialized) call load_itt_library
        call task_end_2
    end subroutine annotate_task_end
    
    subroutine annotate_iteration_task(task_name)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_iteration_task
        character(len=*), intent(in) :: task_name
        if (.not. initialized) call load_itt_library
        call iteration_task(task_name, len(task_name))
    end subroutine annotate_iteration_task

    subroutine annotate_lock_acquire(lock_id)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_lock_acquire
        integer, intent(in) :: lock_id
        if (.not. initialized) call load_itt_library
        call lock_acquire_2(lock_id)
    end subroutine annotate_lock_acquire

    subroutine annotate_lock_release(lock_id)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_lock_release
        integer, intent(in) :: lock_id
        if (.not. initialized) call load_itt_library
        call lock_release_2(lock_id)
    end subroutine annotate_lock_release
    
    subroutine annotate_disable_observation_push
        !DEC$ ATTRIBUTES DEFAULT :: annotate_disable_observation_push
        if (.not. initialized) call load_itt_library
        call disable_push(disable_observation)
    end subroutine annotate_disable_observation_push
    
    subroutine annotate_disable_observation_pop
        !DEC$ ATTRIBUTES DEFAULT :: annotate_disable_observation_pop
        if (.not. initialized) call load_itt_library
        call disable_pop
    end subroutine annotate_disable_observation_pop    
    
    subroutine annotate_disable_collection_push
        !DEC$ ATTRIBUTES DEFAULT :: annotate_disable_collection_push
        if (.not. initialized) call load_itt_library
        call disable_push(disable_collection)
    end subroutine annotate_disable_collection_push
    
    subroutine annotate_disable_collection_pop
        !DEC$ ATTRIBUTES DEFAULT :: annotate_disable_collection_pop
        if (.not. initialized) call load_itt_library
        call disable_pop
    end subroutine annotate_disable_collection_pop    
    
    subroutine annotate_induction_uses_i2(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_induction_uses_i2
        integer(kind=2), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call induction_uses(C_LOC(x), 2)
    end subroutine annotate_induction_uses_i2

    subroutine annotate_induction_uses_i4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_induction_uses_i4
        integer(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call induction_uses(C_LOC(x), 4)
    end subroutine annotate_induction_uses_i4

    subroutine annotate_induction_uses_i8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_induction_uses_i8
        integer(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call induction_uses(C_LOC(x), 8)
    end subroutine annotate_induction_uses_i8

    subroutine annotate_induction_uses_r4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_induction_uses_r4
        real(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call induction_uses(C_LOC(x), 4)
    end subroutine annotate_induction_uses_r4

    subroutine annotate_induction_uses_r8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_induction_uses_r8
        real(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call induction_uses(C_LOC(x), 8)
    end subroutine annotate_induction_uses_r8

    subroutine annotate_induction_uses_c4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_induction_uses_c4
        complex(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call induction_uses(C_LOC(x), 8)
    end subroutine annotate_induction_uses_c4

    subroutine annotate_induction_uses_c8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_induction_uses_c8
        complex(kind=16), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 16)
    end subroutine annotate_induction_uses_c8

    subroutine annotate_reduction_uses_i2(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_reduction_uses_i2
        integer(kind=2), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 2)
    end subroutine annotate_reduction_uses_i2

    subroutine annotate_reduction_uses_i4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_reduction_uses_i4
        integer(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 4)
    end subroutine annotate_reduction_uses_i4

    subroutine annotate_reduction_uses_i8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_reduction_uses_i8
        integer(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 8)
    end subroutine annotate_reduction_uses_i8

    subroutine annotate_reduction_uses_r4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_reduction_uses_r4
        real(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 4)
    end subroutine annotate_reduction_uses_r4

    subroutine annotate_reduction_uses_r8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_reduction_uses_r8
        real(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 8)
    end subroutine annotate_reduction_uses_r8

    subroutine annotate_reduction_uses_c4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_reduction_uses_c4
        complex(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 8)
    end subroutine annotate_reduction_uses_c4

    subroutine annotate_reduction_uses_c8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_reduction_uses_c8
        complex(kind=16), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call reduction_uses(C_LOC(x), 16)
    end subroutine annotate_reduction_uses_c8

    subroutine annotate_observe_uses_i2(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_observe_uses_i2
        integer(kind=2), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call observe_uses(C_LOC(x), 2)
    end subroutine annotate_observe_uses_i2

    subroutine annotate_observe_uses_i4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_observe_uses_i4
        integer(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call observe_uses(C_LOC(x), 4)
    end subroutine annotate_observe_uses_i4

    subroutine annotate_observe_uses_i8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_observe_uses_i8
        integer(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call observe_uses(C_LOC(x), 8)
    end subroutine annotate_observe_uses_i8

    subroutine annotate_observe_uses_r4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_observe_uses_r4
        real(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call observe_uses(C_LOC(x), 4)
    end subroutine annotate_observe_uses_r4

    subroutine annotate_observe_uses_r8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_observe_uses_r8
        real(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call observe_uses(C_LOC(x), 8)
    end subroutine annotate_observe_uses_r8

    subroutine annotate_observe_uses_c4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_observe_uses_c4
        complex(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call observe_uses(C_LOC(x), 8)
    end subroutine annotate_observe_uses_c4

    subroutine annotate_observe_uses_c8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_observe_uses_c8
        complex(kind=16), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call observe_uses(C_LOC(x), 16)
    end subroutine annotate_observe_uses_c8

    subroutine annotate_clear_uses_i2(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_clear_uses_i2
        integer(kind=2), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call clear_uses(C_LOC(x), 2)
    end subroutine annotate_clear_uses_i2

    subroutine annotate_clear_uses_i4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_clear_uses_i4
        integer(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call clear_uses(C_LOC(x), 4)
    end subroutine annotate_clear_uses_i4

    subroutine annotate_clear_uses_i8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_clear_uses_i8
        integer(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call clear_uses(C_LOC(x), 8)
    end subroutine annotate_clear_uses_i8

    subroutine annotate_clear_uses_r4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_clear_uses_r4
        real(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call clear_uses(C_LOC(x), 4)
    end subroutine annotate_clear_uses_r4

    subroutine annotate_clear_uses_r8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_clear_uses_r8
        real(kind=8), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call clear_uses(C_LOC(x), 8)
    end subroutine annotate_clear_uses_r8

    subroutine annotate_clear_uses_c4(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_clear_uses_c4
        complex(kind=4), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call clear_uses(C_LOC(x), 8)
    end subroutine annotate_clear_uses_c4

    subroutine annotate_clear_uses_c8(x)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_clear_uses_c8
        complex(kind=16), intent(in) :: x
        if (.not. initialized) call load_itt_library
        call clear_uses(C_LOC(x), 16)
    end subroutine annotate_clear_uses_c8
    
    subroutine annotate_aggregate_task(count)
        !DEC$ ATTRIBUTES DEFAULT :: annotate_aggregate_task
        integer, intent(in) :: count
        if (.not. initialized) call load_itt_library
        call aggregate_task(count)
    end subroutine annotate_aggregate_task


    !--------
    !
    ! These are the load-library subroutines.
    !
    !--------

!dec$ if defined(use_initialized_proc_ptrs)

    subroutine site_begin_load(name, len) bind(C)
        character, dimension(*), intent(in) :: name
        integer(kind=C_INT), intent(in), value :: len
        call load_itt_library
        call site_begin(name, len)
    end subroutine site_begin_load

    subroutine site_end_2_load bind(C)
        call load_itt_library
        call site_end_2
    end subroutine site_end_2_load

    subroutine task_begin_load(name, len) bind(C)
        character, dimension(*), intent(in) :: name
        integer(kind=C_INT), intent(in), value :: len
        call load_itt_library
        call task_begin(name, len)
    end subroutine task_begin_load

    subroutine task_end_2_load bind(C)
        call load_itt_library
        call task_end_2
    end subroutine task_end_2_load

    subroutine iteration_task_load(name, len) bind(C)
        character, dimension(*), intent(in) :: name
        integer(kind=C_INT), intent(in), value :: len
        call load_itt_library
        call iteration_task(name, len)
    end subroutine iteration_task_load

    subroutine lock_acquire_2_load(lock_id) bind(C)
        integer(kind=C_INT), intent(in), value :: lock_id
        call load_itt_library
        call lock_acquire_2(lock_id)
    end subroutine lock_acquire_2_load

    subroutine lock_release_2_load(lock_id) bind(C)
        integer(kind=C_INT), intent(in), value :: lock_id
        call load_itt_library
        call lock_release_2(lock_id)
    end subroutine lock_release_2_load

    subroutine disable_push_load(disable_kind) bind(C)
        integer(kind=C_INT), intent(in), value :: disable_kind
        call load_itt_library
        call disable_push(disable_kind)
    end subroutine disable_push_load

    subroutine disable_pop_load bind(C)
        call load_itt_library
        call disable_pop
    end subroutine disable_pop_load
    
    subroutine induction_uses_load(addr, size) bind(C)
        type(C_PTR), intent(in), value :: addr
        integer(kind=C_INT), intent(in), value :: size
        call itt_load_library
        call induction_uses(addr, size)
    end subroutine induction_uses_load

    subroutine reduction_uses_load(addr, size) bind(C)
        type(C_PTR), intent(in), value :: addr
        integer(kind=C_INT), intent(in), value :: size
        call itt_load_library
        call reduction_uses(addr, size)
    end subroutine reduction_uses_load

    subroutine observe_uses_load(addr, size) bind(C)
        type(C_PTR), intent(in), value :: addr
        integer(kind=C_INT), intent(in), value :: size
        call itt_load_library
        call observe_uses(addr, size)
    end subroutine observe_uses_load

    subroutine clear_uses_load(addr, size) bind(C)
        type(C_PTR), intent(in), value :: addr
        integer(kind=C_INT), intent(in), value :: size
        call itt_load_library
        call clear_uses(addr, size)
    end subroutine clear_uses_load

    subroutine annotate_task_load(count) bind(C)
        integer(kind=C_INT), intent(in), value :: count
        call load_itt_library
        call annotate_task(count)
    end subroutine annotate_task_load

!dec$ endif

    !--------
    !
    ! These are the stub subroutines.
    !
    !--------
    
    subroutine itt_proc_stub() bind(C)
    end subroutine itt_proc_stub

    subroutine itt_proc_with_name_stub(name, len) bind(C)
        character, dimension(*), intent(in) :: name
        integer(kind=C_INT), intent(in), value :: len
    end subroutine itt_proc_with_name_stub

    subroutine itt_proc_with_int_stub(count) bind(C)
        integer(kind=C_INT), intent(in), value :: count
    end subroutine itt_proc_with_int_stub

    subroutine itt_proc_with_disable_stub(disable_kind) bind(C)
        integer(kind=C_INT), value :: disable_kind
    end subroutine itt_proc_with_disable_stub

    subroutine itt_proc_with_addr_size_stub(addr, size) bind(C)
        type(C_PTR), intent(in), value :: addr
        integer(kind=C_INT), intent(in), value :: size
    end subroutine itt_proc_with_addr_size_stub

    !--------
    !
    ! Internal support code to load the itt notify library and get pointers
    ! to its entry points.
    !
    !--------
    
    subroutine load_itt_library
        type(C_PTR) :: library
        character*1024 ittnotify_path

!dec$ if defined(_WIN32)
        library = load_library("libittnotify.dll"C)
!dec$ else if defined(__APPLE__)
        library = load_library("libittnotify.dylib"C, 0)
!dec$ else
!dec$   if defined(__X86_64) .or. defined(_M_X64)
          call getenv('INTEL_LIBITTNOTIFY64',ittnotify_path)
!dec$   else
          call getenv('INTEL_LIBITTNOTIFY32',ittnotify_path)
!dec$   endif
        if ( ittnotify_path /= '' ) then
          !  print *,' libpath: "'//trim(ittnotify_path)//'"'
          library = load_library(trim(ittnotify_path)//char(0), 1) ! 1 is RTLD_LAZY
        else
          !  print *,' libpath: "libittnotify.so"'
          library = load_library("libittnotify.so"C, 1) ! 1 is RTLD_LAZY
        endif
!dec$ endif

        if (C_ASSOCIATED(library)) then
            !  print *, "Library loaded"
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_site_beginAL"C),     site_begin)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_site_end_2"C),       site_end_2)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_task_beginAL"C),     task_begin)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_task_end_2"C),       task_end_2)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_iteration_taskAL"C), iteration_task)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_lock_acquire_2"C),   lock_acquire_2)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_lock_release_2"C),   lock_release_2)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_disable_push"C),     disable_push)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_disable_pop"C),      disable_pop)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_induction_uses"C),   induction_uses)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_reduction_uses"C),   reduction_uses)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_observe_uses"C),     observe_uses)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_clear_uses"C),       clear_uses)
            call C_F_PROCPOINTER(get_library_entry(library, "__itt_model_aggregate_task"C),   aggregate_task)
        else
            !  print *, "Library not found"
            site_begin       => itt_proc_with_name_stub
            site_end_2       => itt_proc_stub
            task_begin       => itt_proc_with_name_stub
            task_end_2       => itt_proc_stub
            iteration_task   => itt_proc_with_name_stub
            lock_acquire_2   => itt_proc_with_int_stub
            lock_release_2   => itt_proc_with_int_stub
            disable_push     => itt_proc_with_disable_stub
            disable_pop      => itt_proc_stub
            induction_uses   => itt_proc_with_addr_size_stub
            reduction_uses   => itt_proc_with_addr_size_stub
            observe_uses     => itt_proc_with_addr_size_stub
            clear_uses       => itt_proc_with_addr_size_stub
            aggregate_task   => itt_proc_with_int_stub
        end if

        initialized = .true.
    end subroutine
    
end module advisor_annotate
