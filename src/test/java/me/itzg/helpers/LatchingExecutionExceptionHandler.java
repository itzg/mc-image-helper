package me.itzg.helpers;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Getter
@Slf4j
public class LatchingExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    Exception executionException;

    @Override
    public int handleExecutionException(Exception e, CommandLine commandLine, CommandLine.ParseResult parseResult) {
        this.executionException = e;
        log.error("Exception during command execution", e);
        return CommandLine.ExitCode.SOFTWARE;
    }
}
