package java.util.zip;

import java.util.Calendar;
import java.util.Date;

/**
 * class ZipEntry:
 *
 * Class to store and retrieve information for entries in a zip file
 * Contains variables for all standard zip format field as well as
 * setter and accessor methods
 *
 * @author ReadyTalk Summer 2013 Intern Team
 **/
public class ZipEntry {
  String name;
  //Minimum version needed to extract the file(s) from a compressed state
  short reqVersion;
  
  //Method used to compress file
  short compressionMethod;
  
  //Format of date and time are both 2 byte fields
  short modTime;
  short modDate;
  
  //CRC-32
  int crc;
  
  //Sizes of file
  int compSize;
  int uncompSize;

  int offset;
  
  public ZipEntry(String name) {
    this.name = name;
    setTimeDate();
    compSize = 0;
    uncompSize = 0;
    crc = 0;
    offset = 0;
  }

  //Method to return name of the file
  public String getName() {
    return name;
  }
  
  //Method to check if file is a directory
  public boolean isDirectory() {
    return getName().endsWith("/");
  }

  //Method to return the compressed size of the file
  public int getCompressedSize() {
    return compSize;
  }

  //Method to return the uncompressed size of the file
  public int getSize() {
    return uncompSize;
  }
  
  /**
   * Method setTime Date():
   * Creates a calendar object to retrieve date and time information
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
   * @author cjordan
   **/
  public void setTimeDate(){
	  final int DAY_OF_MONTH = 5;
	  final int HOUR_OF_DAY = 11;
	  final int MINUTE = 12;
	  final int MONTH = 2;
	  final int SECOND = 13;
	  final int YEAR = 1;

	  Calendar modCalendar = Calendar.getInstance();

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
	  secBits = secBits >> 1;
	  timeBits = timeBits ^ secBits;
	  
	  //Store Time
	  modTime = (short)timeBits;
	  
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
	  modDate = (short)dateBits;  
    
  }
  
  //Method to set the minimum version required to open the zip file
  //Valid values for the compression method are the numbers 1.0 to 10.0
  public boolean setRequiredVersion(float versionFloat){
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
  //Valid values for the compression method are the numbers 0 to 19 and 99
  public boolean setCompressionMethod(short compMethod){
	  if (compMethod == 99){
		  compressionMethod = compMethod;
		  return true;
	  }
	  else if (compMethod < 0 || compMethod > 19){
		  return false;
	  }
	  else{
		  compressionMethod = compMethod;
		  return true;
	  }
  }
  
}