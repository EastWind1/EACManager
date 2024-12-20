package com.eastwind.OrderManager.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {
    @PreAuthorize("hasAnyAuthority('ADMIN', 'USER')")
	@GetMapping("/test")
    public String test(@RequestParam(defaultValue = "world") String name) {
        return "Hello " + name ;
    }
}
