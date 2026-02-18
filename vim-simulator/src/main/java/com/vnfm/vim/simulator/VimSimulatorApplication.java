package com.vnfm.vim.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ FailureProperties.class, LatencyProperties.class, PoolProperties.class })
public class VimSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(VimSimulatorApplication.class, args);
    }
}
