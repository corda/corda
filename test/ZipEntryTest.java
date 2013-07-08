/* Copyright (c) 2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

import java.util.zip.ZipEntry;

/**
 * class ZipEntryTest:
 *
 * class to test the ZipEntry class in java.util.zip
 *
 * @author Christopher Jordan
 */
public class ZipEntryTest {
  static long timeInMillis = 1373309644787L;
  static int dateInBytes = 1122526914;
  
  public static void main(String args[]){
    ZipEntry testEntry = new ZipEntry("testfile");

    verifyDefaultValues(testEntry);
    verifyTimeDate(testEntry, timeInMillis);
    checkSetsAndGets(testEntry);
  }

  private static void verifyDefaultValues(ZipEntry testEntry){
    if (testEntry.getTime() == -1)
        throw new RuntimeException("The time isn't being set by the constructor");
    verifyName(testEntry);
  }

  private static void verifyName(ZipEntry testEntry){
    if (testEntry.getName() == "testfile")
      return;
    else
      throw new RuntimeException("Name isn't being stored properly");
  }

  private static void verifyTimeDate(ZipEntry testEntry, long timeMillis){
    testEntry.setTime(timeMillis);
    if (testEntry.getJavaTime() != dateInBytes)
        throw new RuntimeException("Date isn't being parsed properly");
    if (testEntry.getTime() != timeInMillis)
        throw new RuntimeException("Time isn't being stored accurately");
  }

  private static void checkSetsAndGets(ZipEntry testEntry){
    return;
  }
}
