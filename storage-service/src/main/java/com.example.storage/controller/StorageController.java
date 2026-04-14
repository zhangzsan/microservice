package com.example.storage.controller;

import com.example.common.dto.StorageDeductRequest;
import com.example.common.dto.StorageRestoreRequest;
import com.example.common.result.Result;
import com.example.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/storage")
public class StorageController {

    @Autowired
    private StorageService storageService;

    @PostMapping("/deduct")
    public Result<?> deduct(@RequestBody StorageDeductRequest request) {
        storageService.deduct(request);
        return Result.success();
    }

    @PostMapping("/restore")
    public Result restore(@RequestBody StorageRestoreRequest request) {
        storageService.restore(request);
        return Result.success();
    }
}