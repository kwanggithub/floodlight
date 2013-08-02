package net.bigdb.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class StringUtils {

    public static String readStringFromInputStream(InputStream inputStream)
            throws Exception {
        final char[] buffer = new char[0x10000];
        StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(inputStream, "UTF-8");
        try {
          int read;
          do {
            read = in.read(buffer, 0, buffer.length);
            if (read>0) {
              out.append(buffer, 0, read);
            }
          } while (read>=0);
        } finally {
          in.close();
        }
        String result = out.toString();
        return result;
    }


   /** if s is longer than maxLength, truncate it to maxLength - len(truncationIndicator) and return substr + truncationIndicator.
    *  else return s
    *
    * @param s
    * @param maxLength
    * @param truncationIndicator
    * @return s, truncated if longer than maxLength
    */
   public static String truncate(String s, int maxLength, String truncationIndicator) {
       if(s == null)
           return s;
       if(s.length() <= maxLength)
           return s;
       else
           return s.substring(0, maxLength - truncationIndicator.length()) + truncationIndicator;
   }

}
