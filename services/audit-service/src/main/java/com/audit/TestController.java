package com.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TestController {

    @Autowired
    private AuditService service;

    // test API
    @GetMapping("/test")
    public String test() {
        return "Audit Service Working";
    }

    // manual insert (for testing)
    @GetMapping("/add")
    public String add() {

        service.log(
                "TXN_TEST",
                "PSP",
                "INITIATED",
                "alice@sbi",
                "bob@hdfc",
                100.0,
                "init",
                "Manual test");

        return "Inserted into DB";
    }

    // view all logs
    @GetMapping("/all")
    public List<AuditLog> getAll() {
        return service.getAll();
    }
}