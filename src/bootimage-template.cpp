const unsigned NAME(BootMask) = (~static_cast<unsigned>(0))
  / NAME(BytesPerWord);

const unsigned NAME(BootShift) = 32 - avian::util::log(NAME(BytesPerWord));

const unsigned NAME(BootFlatConstant) = 1 << NAME(BootShift);
const unsigned NAME(BootHeapOffset) = 1 << (NAME(BootShift) + 1);

inline unsigned
LABEL(codeMapSize)(unsigned codeSize)
{
  return avian::util::ceilingDivide(codeSize, TargetBitsPerWord) * TargetBytesPerWord;
}

inline unsigned
LABEL(heapMapSize)(unsigned heapSize)
{
  return avian::util::ceilingDivide(heapSize, TargetBitsPerWord * TargetBytesPerWord)
    * TargetBytesPerWord;
}

inline object
LABEL(bootObject)(LABEL(uintptr_t)* heap, unsigned offset)
{
  if (offset) {
    return reinterpret_cast<object>(heap + offset - 1);
  } else {
    return 0;
  }
}
