package com.example.account.controller;

import com.example.common.dto.AccountDeductRequest;
import com.example.common.dto.AccountRestoreRequest;
import com.example.common.result.Result;
import com.example.account.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @PostMapping("/deduct")
    public Result<?> deduct(@RequestBody AccountDeductRequest request) {
        accountService.deduct(request);
        return Result.success();
    }

    @PostMapping("/restore")
    public Result<?> restore(@RequestBody AccountRestoreRequest request) {
        accountService.restore(request);
        return Result.success();
    }
}