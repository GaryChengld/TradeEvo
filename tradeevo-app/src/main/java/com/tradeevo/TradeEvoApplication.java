package com.tradeevo;

import java.util.Map;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the REST server, or runs a single CLI command when the first argument is a command name
 * (e.g. {@code java -jar tradeevo.jar backtest --config ...}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TradeEvoApplication {

    public static void main(String[] args) {
        if (args.length > 0 && com.tradeevo.cli.CliRunner.COMMAND.equals(args[0])) {
            SpringApplication app = new SpringApplication(TradeEvoApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(Banner.Mode.OFF);
            app.setDefaultProperties(Map.of("logging.level.root", "WARN"));
            System.exit(SpringApplication.exit(app.run(args)));
        }
        SpringApplication.run(TradeEvoApplication.class, args);
    }
}
