/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <windows.h>
#include <tchar.h>
#include <stdio.h>
#include <stdint.h>
#include <vector>
#include <string>

#include "avian/embed.h"

#ifdef __x86_64__
#define BINARY_LOADER(x) _binary_loader_##x
#else
#define BINARY_LOADER(x) binary_loader_##x
#endif

extern "C" const uint8_t BINARY_LOADER(start)[];
extern "C" const uint8_t BINARY_LOADER(end)[];

__declspec(noreturn) void printUsage(const wchar_t* executableName)
{
  wprintf(L"Usage: %s destination.exe classes.jar package.Main\n",
          executableName);
  exit(0);
}

void writeDestinationFile(const wchar_t* filename)
{
  if (FILE* file = _wfopen(filename, L"wb")) {
    size_t count = BINARY_LOADER(end) - BINARY_LOADER(start);
    if (count == fwrite(BINARY_LOADER(start),
                        sizeof(BINARY_LOADER(start)[0]),
                        count,
                        file)) {
      fclose(file);
      return;
    }
  }

  fprintf(stderr, "Unable to write to destination file\n");
  exit(EXIT_FAILURE);
}

void readFile(std::vector<char>* jarFile, const wchar_t* fileName)
{
  if (FILE* file = _wfopen(fileName, L"rb")) {
    fseek(file, 0, SEEK_END);
    jarFile->resize(ftell(file));
    fseek(file, 0, SEEK_SET);
    fread(&jarFile->at(0), 1, jarFile->size(), file);
    fclose(file);
  }
}

bool mkStringSection(std::vector<wchar_t>* stringSection,
                     const std::vector<std::wstring>& strings,
                     int first,
                     int last)
{
  stringSection->clear();
  for (int i = first; i <= last; ++i) {
    const std::wstring& s = strings.at(i);
    stringSection->push_back(s.size());
    stringSection->insert(stringSection->end(), s.begin(), s.end());
  }

  // pad to 16 entries
  for (int i = last - first; i < 15; ++i)
    stringSection->push_back(0);

  return stringSection->size() > 16;
}

void writeStringResources(HANDLE hDest,
                          const std::vector<std::wstring>& strings)
{
  for (unsigned i = 0; i < strings.size(); i += 16) {
    std::vector<wchar_t> stringSection;

    if (mkStringSection(&stringSection,
                        strings,
                        i,
                        std::min<int>(i + 15, strings.size() - 1)))
      UpdateResourceW(hDest,
                      reinterpret_cast<LPCWSTR>(RT_STRING),
                      reinterpret_cast<LPCWSTR>(MAKEINTRESOURCE((i >> 4) + 1)),
                      LANG_NEUTRAL,
                      &stringSection.at(0),
                      sizeof(wchar_t) * stringSection.size());
  }
}

int wmain(int argc, wchar_t* argv[])
{
  if (argc != 4)
    printUsage(argv[0]);

  const wchar_t* destinationName = argv[1];
  const wchar_t* classesName = argv[2];
  const wchar_t* mainClassName = argv[3];

  writeDestinationFile(destinationName);

  if (HANDLE hDest = BeginUpdateResourceW(destinationName, TRUE)) {
    std::vector<std::wstring> strings;
    strings.resize(RESID_MAIN_CLASS + 1);
    strings.at(RESID_MAIN_CLASS) = mainClassName;

    writeStringResources(hDest, strings);

    std::vector<char> jarFile;
    readFile(&jarFile, classesName);
    UpdateResourceW(hDest,
                    reinterpret_cast<LPCWSTR>(RT_RCDATA),
                    RESID_BOOT_JAR,
                    LANG_NEUTRAL,
                    &jarFile.at(0),
                    jarFile.size());

    EndUpdateResource(hDest, FALSE);
  }

  return 0;
}

#ifndef _MSC_VER
extern "C" int _CRT_glob;
extern "C" void __wgetmainargs(int*, wchar_t***, wchar_t***, int, int*);

int main()
{
  wchar_t** enpv, **argv;
  int argc, si = 0;
  __wgetmainargs(&argc, &argv, &enpv, _CRT_glob, &si);
  return wmain(argc, argv);
}
#endif
