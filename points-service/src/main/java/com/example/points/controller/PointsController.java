package com.example.points.controller;

import com.example.common.dto.PointsMessage;
import com.example.common.result.Result;
import com.example.points.service.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/points")
public class PointsController {

    @Autowired
    private PointsService pointsService;

    @PostMapping("/add")
    public Result addPoints(@RequestBody PointsMessage message) {
        pointsService.addPoints(message);
        return Result.success();
    }
}