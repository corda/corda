#include "types.h"

.text
   
#ifdef __APPLE__
.globl _cdeclCall
_cdeclCall: 
#else
.globl cdeclCall
cdeclCall:
#endif
   pushl  %ebp
   movl   %esp,%ebp

   //  8(%ebp): function
   // 12(%ebp): stack
   // 16(%ebp): stackSize
   // 20(%ebp): returnType
   
   // reserve space for arguments
   movl   16(%ebp),%ecx

   subl   %ecx,%esp

#ifdef __APPLE__
   // align to a 16 byte boundary on Darwin
   andl   $0xFFFFFFF0,%esp
#endif
   

   // copy arguments into place
   movl   $0,%ecx
   jmp    test

loop:
   movl   %ecx,%eax
   movl   %ecx,%edx
   addl   %esp,%edx
   addl   12(%ebp),%eax
	 movl	  (%eax),%eax
   movl   %eax,(%edx)
   addl   $4,%ecx

test:
   cmpl   16(%ebp),%ecx
   jb     loop

   // call function
   call   *8(%ebp)

   // handle return value based on expected type
   movl   20(%ebp),%ecx
   
void:   
   cmpl   $VOID_TYPE,%ecx
   jne    int64
   jmp    exit

int64: 
   cmpl   $INT64_TYPE,%ecx
   jne    float
   jmp    exit
   
float:   
   cmpl   $FLOAT_TYPE,%ecx
   jne    double
   fstps  8(%ebp)
   movl   8(%ebp),%eax
   jmp    int32

double:
   cmpl   $DOUBLE_TYPE,%ecx
   jne    int32
   fstpl  8(%ebp)
   movl   8(%ebp),%eax
   movl   12(%ebp),%edx
   jmp    exit
   
int32:
   movl   $0,%edx

exit:
   movl   %ebp,%esp
   popl   %ebp
   ret
