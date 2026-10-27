package org.example.docker_aug2026;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello/{arg}")
    public String sayHello(@PathVariable String arg) {
        return "Hello "+arg;
    }
}
