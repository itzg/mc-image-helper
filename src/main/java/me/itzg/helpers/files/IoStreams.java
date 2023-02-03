package me.itzg.helpers.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Since Java 8 doesn't have InputStream.transferTo
 */
public class IoStreams {

    public static void transfer(InputStream in, OutputStream out) throws IOException {
        final byte[] buf = new byte[8192];
        int length;
        while ((length = in.read(buf)) != -1) {
           out.write(buf, 0, length);
        }

    }
}
