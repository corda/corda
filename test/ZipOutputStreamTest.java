import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.*;

public class ZipOutputStreamTest
{
        private static final String TEST1 = "test1.txt";
        private static final String TEST2 = "test2.txt";
        private static final String TEST3 = "test3.txt";
        private static final String TEST4 = "test4.txt";

        private static final String TEST1_CONTENTS = "\"this is a test\"";
        private static final String TEST2_CONTENTS = "this is a\nmulti-line test";
        private static final String TEST3_CONTENTS = "74 68 69 73 20 69 73 20 61 20 74 65 73 74";
        private static final String TEST4_CONTENTS = "01110100 01101000 01101001 01110011 00100000 01101001 01110011 00100000 01100001 00100000 01110100 01100101 01110011 01110100";

        private static final String BYTE_ZIP_PREFIX = "zosByte";
        private static final String ARRAY_ZIP_PREFIX = "zosArray";
        private static final String ARRAY_OFFSET_LENGTH_ZIP_PREFIX = "zosArrayOffsetLength";
        private static final String ZIP_SUFFIX = ".zip";

        private static final Map<String, String> FILES_CONTENTS;
        static
        {
                Map<String, String> m = new HashMap<String, String>();
                m.put(TEST1, TEST1_CONTENTS);
                m.put(TEST2, TEST2_CONTENTS);
                m.put(TEST3, TEST3_CONTENTS);
                m.put(TEST4, TEST4_CONTENTS);
                FILES_CONTENTS = Collections.unmodifiableMap(m);
        }

        private static enum WriteStyle {
          Byte(ARRAY_ZIP_PREFIX),
          Array(ARRAY_ZIP_PREFIX),
          ArrayOffsetLength(ARRAY_OFFSET_LENGTH_ZIP_PREFIX);

          public final String prefix;

          private WriteStyle(String prefix) {
            this.prefix = prefix;
          }
        }
        private static byte[] buffer = new byte[1024];

        private static void expect(boolean v) {
          if (! v) throw new RuntimeException();
        }

        public static void main(String[] args)
          throws Exception
        {
                List<File> zipFiles = new ArrayList<File>(2);

                try {
                  // Test byte-at-a-time write function
                  File f1 = createZip(WriteStyle.Byte);
                  zipFiles.add(f1);
                  verifyContents(f1.getAbsolutePath());
                  // Test arraw write function
                  File f2 = createZip(WriteStyle.Array);
                  zipFiles.add(f2);
                  verifyContents(f2.getAbsolutePath());
                  // Test arraw write function
                  File f3 = createZip(WriteStyle.ArrayOffsetLength);
                  zipFiles.add(f3);
                  verifyContents(f3.getAbsolutePath());
                } finally {
                  // Remove the created zip files
                  cleanUp(zipFiles);
                }
        }

        private static File createZip(WriteStyle writeStyle)
          throws Exception
        {
                FileOutputStream outputStream = null;
                ZipOutputStream zipContents = null;

                try
                {
                        // Create a temporary zip file for this test
                        String prefix = writeStyle.prefix;
                        File outputZip = File.createTempFile(prefix, ZIP_SUFFIX);

                        System.out.println("Created " + outputZip.getAbsolutePath());

                        // Prepare the streams
                        outputStream = new FileOutputStream(outputZip);
                        zipContents = new ZipOutputStream(outputStream);

                        // Zip the file contents (convert directly from string to bytes)
                        long startTime = System.currentTimeMillis();
                        for (Map.Entry<String, String> f : FILES_CONTENTS.entrySet())
                        {
                                String name = f.getKey();
                                String contents = f.getValue();

                                System.out.println("Zipping " + name + "...");
                                ZipEntry entry = new ZipEntry(name);
                                zipContents.putNextEntry(entry);

                                byte[] bytesToWrite = contents.getBytes();

                                switch (writeStyle) {
                                case Byte: {
                                  // Use the 1-parameter write method; takes a single byte
                                  for (int i = 0; i < bytesToWrite.length; i++)
                                  {
                                    zipContents.write(bytesToWrite[i]);
                                  }
                                } break;

                                case Array: {
                                  // Use 3-parameter write method; takes a buffer, offset, and length
                                  zipContents.write(bytesToWrite);
                                } break;

                                case ArrayOffsetLength: {
                                  // Use 3-parameter write method; takes a buffer, offset, and length
                                  zipContents.write(bytesToWrite, 0 , bytesToWrite.length);
                                } break;

                                  default: throw new RuntimeException("unexpected write style: " + writeStyle);
                                }

                                // Done with this file
                                zipContents.closeEntry();
                                System.out.println("Done");
                        }

                        // All files have been written
                        long endTime = System.currentTimeMillis();
                        System.out.println("Finished " + outputZip.getName() + " in " + ((endTime - startTime) / 1000.0) + " seconds");
                        return outputZip;
                }
                finally
                {
                  if (zipContents != null)
                    zipContents.close();
                  if (outputStream != null)
                    outputStream.close();
                }
        }

        private static void verifyContents(String zipName)
          throws Exception
        {
                System.out.println("Verify " + zipName);
                ZipFile zf = null;
                BufferedReader reader = null;
                int numFilesInZip = 0;

                try
                {
                        String line;
                        String contents;

                        // Get the contents of each file in the zip
                        zf = new ZipFile(zipName);
                        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();)
                        {
                                ZipEntry entry = e.nextElement();
                                reader = new BufferedReader(new InputStreamReader(zf.getInputStream(entry)));
                                contents = "";
                                numFilesInZip += 1;

                                while ((line = reader.readLine()) != null)
                                {
                                        if (contents.length() > 0)
                                        {
                                                contents += "\n";
                                        }
                                        contents += line;
                                }
                                reader.close();

                                // Assert that this file's contents are correct
                                expect(contents.equals(FILES_CONTENTS.get(entry.getName())));
                        }
                        zf.close();

                        // Assert that the zip contained the correct number of files
                        expect(numFilesInZip == FILES_CONTENTS.size());
                }
                finally
                {
                                if (zf != null)
                                        zf.close();
                                if (reader != null)
                                        reader.close();
                }
        }

        private static void cleanUp(List<File> zipFiles)
          throws Exception
        {
                        for (File f : zipFiles)
                        {
                                if (f.exists())
                                {
                                        f.delete();
                                }
                        }
        }
}
