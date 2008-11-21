namespace vm {

class BootImage {
 public:
  static const unsigned Magic = 0x22377322;

  unsigned magic;

  unsigned heapSize;
  unsigned codeSize;

  unsigned codeTable;

  unsigned loader;
  unsigned bootstrapClassMap;
  unsigned stringMap;
  unsigned types;
  unsigned jniMethodTable;
  unsigned finalizers;
  unsigned tenuredFinalizers;
  unsigned finalizeQueue;
  unsigned weakReferences;
  unsigned tenuredWeakReferences;

  unsigned defaultThunk;
  unsigned nativeThunk;
  unsigned aioobThunk;
  
#define THUNK(s) unsigned s##Thunk;
#include "thunks.cpp"
#undef THUNK
};

} // namespace vm
