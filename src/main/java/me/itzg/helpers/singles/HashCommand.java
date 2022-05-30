package me.itzg.helpers.singles;

import java.security.MessageDigest;
import java.util.concurrent.Callable;
import org.apache.commons.codec.binary.Hex;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

@Command(name = "hash",
  description = "Outputs an MD5 hash of the standard input"
)
public class HashCommand implements Callable<Integer> {

  @Override
  public Integer call() throws Exception {
    final MessageDigest messageDigest = MessageDigest.getInstance("MD5");

    final byte[] buffer = new byte[1000];
    while (true) {
      final int len = System.in.read(buffer);
      if (len < 0) {
        break;
      }
      messageDigest.update(buffer, 0, len);
    }

    System.out.println(Hex.encodeHexString(messageDigest.digest()));

    return ExitCode.OK;
  }
}
