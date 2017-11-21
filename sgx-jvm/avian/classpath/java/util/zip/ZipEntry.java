/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.zip;

/**
 * Class ZipEntry:
 *
 * Class to store and retrieve information for entries in a zip file
 * Contains variables for all standard zip format field as well as
 * setter and accessor methods
 *
 * "name" is used to store the string name of the entrys
 * "reqVersion" stores a byte encoded minimum required version to open the zip
 * "compressionMethod" stores the method used to compress the zip
 * "modTimeDate" stores an MSDOS time field "millisTime" stores time in long format
 * "crc" stores crc data for the zip entry
 * "compSize" and "uncompSize" store compressed and uncompressed sizes
 * "offset" stores data regarding the offset from the start of the zip file
 *
 * @author Christopher Jordan
 * @author David Chau
 * @author Aaron Davis
 * @author Riley Moses
 */

import java.util.Calendar;
import java.util.Date;


public class ZipEntry {
  String name;
  short reqVersion = -1;
  short compressionMethod = -1;
  int modTimeDate = -1;
  long millisTime = -1;
  int crc = -1;
  long compSize = 0;
  long uncompSize = 0;
  int offset = -1;
  
  public ZipEntry(String name) {
    this.name = name;
  }

  //Method to return name of the file
  public String getName() {
    return name;
  }
  
  //Method to check if file is a directory
  public boolean isDirectory() {
    return getName().endsWith("/");
  }

  /**
   * Method setRequiredVersion:
   *
   * Method to set the minimum version required to open the zip file
   * Valid values for the compression method are the numbers 1.0 to 10.0
   *
   * @author Christopher Jordan
   */
  private boolean setRequiredVersion(float versionFloat){
    //Check for valid version numbers
    if (versionFloat < 1 || versionFloat > 100){
      return false;
    }
    
    //Convert to short value for storage
    versionFloat = versionFloat * 10;
    short versionShort = (short)versionFloat;
    
    //Set value of version
    reqVersion = versionShort;
    return true;
  }
  
  //Method to set the compression method for the file
  //Valid methods are "stored" = 0 or "deflated" = 8
  public void setMethod(short compMethod){
    if (compMethod == 0 || compMethod == 8){
      this.compressionMethod = compMethod;
    }
  }

  public int getMethod(){
    return this.compressionMethod;
  }

  //Methods to set and get the crc for the entry
  public void setCrc(int crc){
    if (crc < 0 || crc > 0xffffffff){
      return;
    }
    else
      this.crc = crc;
  }

  public int getCrc(){
    return this.crc;
  }

  //Methods to set and get the time and date
  public void setTime(long currentTime){
    modTimeDate = computeDOSDateTime(currentTime);
    millisTime = currentTime;
  }

  public long getTime(){
    return millisTime;
  }

  /**
   * Method computeDOSDateTime():
   * 
   * Takes the time from a long and converts to a Calendar object
   * 
   * Time is stored in the MSDOS format 5 bits for hour, 6 bits for min, 5 bits for seconds
   * Hours are in military time and must be adjusted to time zone
   * Seconds are divided by two before storing and must be multiplied by two to retrieve
   *
   * Date is stored in the MSDOS format 7 bit for year, 4 bit for month, 5 bits for day of month
   * Year is the number of years since 1980 per, the month is stored starting at 0
   * for January. Day of month is stored with nature numbering
   * 
   * Bit masks and shifting are used to build the time and date bytes
   * 
   * @author Christopher Jordan
   * @author Aaron Davis
   **/
  private static int computeDOSDateTime(long currentTime){
	  final int DAY_OF_MONTH = 5;
	  final int HOUR_OF_DAY = 11;
	  final int MINUTE = 12;
	  final int MONTH = 2;
	  final int SECOND = 13;
	  final int YEAR = 1;

    //Create calendar object, then set time to value passed in
	  Calendar modCalendar = Calendar.getInstance();
    modCalendar.setTime(new Date(currentTime));

	  //Hour
	  int timeBits = modCalendar.get(HOUR_OF_DAY);
    timeBits = timeBits - 6;
	  timeBits = timeBits << 6;
	  
	  //Minutes
	  int minBits = 0x3f & (modCalendar.get(MINUTE));;
	  timeBits = timeBits ^ minBits;
	  timeBits = timeBits << 5;
	  
	  //Seconds
	  int secBits = 0x1f & (modCalendar.get(SECOND));
	  secBits = secBits >> 1;                  //Divide by 2
	  timeBits = timeBits ^ secBits;
	  
	  //Year
	  int dateBits = (modCalendar.get(YEAR) -1980);
	  dateBits = dateBits << 4;

	  //Month
    int month = 0xf & ((modCalendar.get(MONTH)) + 1);
	  dateBits = dateBits ^ month;
	  dateBits = dateBits << 5;
	  
	  //Day of month
	  int dayBits = 0x1f & modCalendar.get(DAY_OF_MONTH);
	  dateBits = dateBits ^ dayBits;
	  
	  //Store Date
    int storeDate = ((dateBits << 16) ^ (timeBits));
    return storeDate;
  }

  //Methods to set and get the uncompressed size of the entry
  public void setSize(int size){
    if (size < 0){
      return;
    }
    else
      uncompSize = size;
  }

  public long getSize() {
    return uncompSize;
  }

  //Methods to set and get the compressed size of the entry
  public void setCompressedSize(long size){
    if (size < 0){
      return;
    }
    else
      compSize = size;
  }

  public long getCompressedSize() {
    return compSize;
  }
}
