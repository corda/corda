/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Handler extends URLStreamHandler
{
    public URLConnection openConnection(URL url) throws IOException
    {
        return new HttpURLConnection(url);
    }
    
    class HttpURLConnection extends URLConnection
    {
        private static final String HKEY_CONTENT_LENGTH = "content-length";

        Socket socket;
        private BufferedWriter writer;
        private InputStream bin;
        private Map<String, List<String>> header = new HashMap<String, List<String>>();
        private int status;
        
        protected HttpURLConnection(URL url)
        {
            super(url);
        }

        @Override
        public void connect() throws IOException
        {
            if(socket == null)
            {
                URLConnection con = null;
                String host = url.getHost();
                int port =url.getPort();
                if(port < 0) port = 80;
                socket = new Socket(host, port);
                OutputStream out = socket.getOutputStream();
                writer = new BufferedWriter(new OutputStreamWriter(out));
                writer.write("GET " + url.getPath() + " HTTP/1.1");
                writer.write("\r\nHost: " + host);
                writer.write("\r\n\r\n");
                writer.flush();
                bin = new BufferedInputStream(socket.getInputStream());
                readHeader();
//                System.out.println("Status: " + status);
//                System.out.println("Headers: " + header);
            }
        }

        private void readHeader() throws IOException
        {
            byte[] buf = new byte[8192];
            int b = 0;
            int index = 0;
            while(b >= 0)
            {
                if(index >= 4 && buf[index-4] == '\r' && buf[index-3] == '\n' && buf[index-2] == '\r' && buf[index-1] == '\n')
                {
                    break;
                }
                b = bin.read();
                buf[index] = (byte) b;
                index++;
                if(index >= buf.length)
                {
                    throw new IOException("Header exceeded maximum size of 8k.");
                }
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf, 0, index)));
            String line = reader.readLine();
            int x = line.indexOf(' ');
            status = Integer.parseInt(line.substring(x + 1 , line.indexOf(' ', x+1)));
            while(line != null)
            {
                int i = line.indexOf(':');
                if(i > 0)
                {
                    String key = line.substring(0, i).toLowerCase();
                    String value = line.substring(i + 1).trim();

                    List<String> valueList = new ArrayList<String>();
                    valueList.add(value);
                    header.put(key, Collections.unmodifiableList(valueList));
                }
                line = reader.readLine();
            }
            reader.close();
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            connect();
            return bin;
        }
        
        @Override
        public OutputStream getOutputStream() throws IOException
        {
            throw new UnsupportedOperationException("Can' write to HTTP Connection");
        }

        @Override
        public int getContentLength()
        {
            return getHeaderFieldInt(HKEY_CONTENT_LENGTH, -1);
        }

        @Override
        public long getContentLengthLong()
        {
            return getHeaderFieldLong(HKEY_CONTENT_LENGTH, -1l);
        }

        @Override
        public Map<String,List<String>> getHeaderFields()
        {
            return Collections.unmodifiableMap(header);
        }
    }
}
