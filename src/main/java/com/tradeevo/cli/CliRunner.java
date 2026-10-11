package com.tradeevo.cli;

import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

/** Runs {@link BacktestCommand} when the application is started with {@code backtest ...}. */
@Component
public class CliRunner implements CommandLineRunner, ExitCodeGenerator {

    public static final String COMMAND = "backtest";

    private final ObjectProvider<BacktestCommand> commands;
    private int exitCode;

    public CliRunner(ObjectProvider<BacktestCommand> commands) {
        this.commands = commands;
    }

    @Override
    public void run(String... args) {
        if (args.length > 0 && COMMAND.equals(args[0])) {
            exitCode = new CommandLine(commands.getObject()).execute(Arrays.copyOfRange(args, 1, args.length));
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
