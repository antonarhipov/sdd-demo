package org.example.sdd;

import org.springframework.boot.SpringApplication;

public class TestSddDemoApplication {

    public static void main(String[] args) {
        SpringApplication.from(Application::main).with(TestcontainersConfiguration.class).run(args);
    }

}
