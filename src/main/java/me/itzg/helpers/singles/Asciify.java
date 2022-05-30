package me.itzg.helpers.singles;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "asciify",
        description = "Converts UTF-8 on stdin to ASCII by escaping Unicode characters")
public class Asciify implements Callable<Integer> {

    private static final int MAX_ASCII = 0x7f;

    @Override
    public Integer call() throws Exception {
        // buffer will auto-grow if there happens to be more input
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
        final byte[] readBuffer = new byte[1024];
        int len;
        while ((len = System.in.read(readBuffer)) >= 0) {
            if (len > 0) {
                buffer.write(readBuffer, 0, len);
            }
        }

        final String asUtf8 = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

        for (int i = 0; i < asUtf8.length(); ++i) {
            final char c = asUtf8.charAt(i);
            if (c > MAX_ASCII) {
                //noinspection RedundantStringFormatCall
                System.out.print(String.format("\\u%04x", (int) c));
            } else {
                System.out.print(c);
            }
        }
        System.out.flush();

        return 0;
    }
}
