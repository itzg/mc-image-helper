package me.itzg.helpers.singles;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

import java.util.concurrent.Callable;

@Command(name = "test-logging-levels")
@Slf4j
public class TestLoggingCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        log.error("This is an error");
        log.warn("This is a warning");
        log.info("This is an info");
        log.debug("This is a debug");
        return ExitCode.OK;
    }
}
