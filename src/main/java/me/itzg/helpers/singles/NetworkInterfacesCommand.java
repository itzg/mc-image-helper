package me.itzg.helpers.singles;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "network-interfaces",
    description = "Provides simple operations to list network interface names and check existence"
)
public class NetworkInterfacesCommand implements Callable<Integer> {

    @Option(names = "--check")
    String ifNameToCheck;

    @Option(names = "--include-loopback")
    boolean includeLoopback;

    @Override
    public Integer call() throws Exception {
        if (ifNameToCheck != null) {
            final NetworkInterface nic = NetworkInterface.getByName(ifNameToCheck);
            return allowedNic(nic) ? ExitCode.OK
                : ExitCode.SOFTWARE;
        }

        final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            final NetworkInterface nic = interfaces.nextElement();
            if (allowedNic(nic)) {
                System.out.println(nic.getName());
            }
        }

        return ExitCode.OK;
    }

    private boolean allowedNic(NetworkInterface nic) throws SocketException {
        return nic != null
            && (includeLoopback || !nic.isLoopback())
            && nic.isUp();
    }
}
