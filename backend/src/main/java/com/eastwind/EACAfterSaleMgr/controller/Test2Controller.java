package com.eastwind.EACAfterSaleMgr.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class Test2Controller {
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/test2")
    public String test(@RequestParam(defaultValue = "world") String name) {
        return "good bye  " + name ;
    }
}
