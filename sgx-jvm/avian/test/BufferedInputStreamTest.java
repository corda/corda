
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.BufferedInputStream;

/**
 * Checks that BufferedInputStream does not block if data is available in it's internal buffer.
 */
public class BufferedInputStreamTest
{
   public static void main(String[] args) throws IOException
   {
      MyByteArrayStream in = new MyByteArrayStream(new byte[100]);
      
      BufferedInputStream bin = new BufferedInputStream(in);
      //read a single byte to fill the buffer
      int b = bin.read();
      byte[] buf = new byte[10];
      //now try to read 10 bytes. this should a least return the content of the buffer. On OpenJDK this are 
      //4 bytes (the rest of the buffer returned by MyByteArrayStream in the first call).
      //It should definately NOT block.
      int count = bin.read(buf);
      System.out.println("Read bytes: " + count);
   }
   
   /**
    * Internal Stream used to show the BufferedInputStream behaviour.
    */
   static class MyByteArrayStream extends ByteArrayInputStream
   {
      boolean stopReading = false;
      
      /**
       * @param buf
       */
      public MyByteArrayStream(byte[] buf)
      {
         super(buf);
      }
      
      /* (non-Javadoc)
       * @see java.io.ByteArrayInputStream#read(byte[], int, int)
       */
      @Override
      public synchronized int read(byte[] b, int off, int len)
      {
         if(stopReading == false)
         {  //On the first call 5 bytes are returned.
            stopReading = true;
            return super.read(b, off, 5);
         }
         //on all following calls block. The spec says, that a least one byte is returned, if the
         //stream is not at EOF.
         return 0;
      }
      
       /* (non-Javadoc)
       * @see java.io.ByteArrayInputStream#available()
       */
      @Override
      public synchronized int available()
      {
         if(stopReading)
         {
            return 0;
         }
         return super.available();
      }
   }
}
