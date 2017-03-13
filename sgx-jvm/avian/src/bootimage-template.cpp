const unsigned NAME(BootMask) = (~static_cast<unsigned>(0))
                                / NAME(BytesPerWord);

const unsigned NAME(BootShift) UNUSED = 32 - avian::util::log(NAME(BytesPerWord));

inline unsigned LABEL(codeMapSize)(unsigned codeSize)
{
  return avian::util::ceilingDivide(codeSize, TargetBitsPerWord)
         * TargetBytesPerWord;
}

inline unsigned LABEL(heapMapSize)(unsigned heapSize)
{
  return avian::util::ceilingDivide(heapSize,
                                    TargetBitsPerWord * TargetBytesPerWord)
         * TargetBytesPerWord;
}

inline object LABEL(bootObject)(LABEL(uintptr_t) * heap, unsigned offset)
{
  if (offset) {
    return reinterpret_cast<object>(heap + offset - 1);
  } else {
    return 0;
  }
}
