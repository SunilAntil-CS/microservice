package com.vnfm.vimmanager;

import com.vnfm.vimmanager.simulator.VimSimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(VimSimulatorProperties.class)
public class VimManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VimManagerApplication.class, args);
    }
}
