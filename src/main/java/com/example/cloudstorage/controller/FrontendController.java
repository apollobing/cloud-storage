package com.example.cloudstorage.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping(value = {"/", "/login", "/registration", "/files/**"})
    public String forwardReactRoutes() {
        return "forward:/index.html";
    }
}
