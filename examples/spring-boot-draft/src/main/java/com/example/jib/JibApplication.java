package com.example.jib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class JibApplication {

    public static void main(String[] args) {
        SpringApplication.run(JibApplication.class, args);
    }

    @RequestMapping("/")
    public String helloJib() {
        return "Hello Jib";
    }
}
