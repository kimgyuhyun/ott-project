package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.service.RatingService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/anime/{aniId}/ratings")
public class RatingController {

    private final RatingService ratingService;
    private final SecurityUtil securityUtil;

    @PostMapping
    public ResponseEntity<Double> createOrUpdate(@PathVariable Long aniId, @RequestParam Double score, HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        ratingService.createOrUpdateRating(userId, aniId, score);
        return ResponseEntity.ok(score);
    }

    @GetMapping("/me")
    public ResponseEntity<Double> myRating(@PathVariable Long aniId, HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        return ResponseEntity.ok(ratingService.getUserRating(userId, aniId));
    }

    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> stats(@PathVariable Long aniId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("distribution", ratingService.getDistribution(aniId));
        body.put("average", ratingService.getAverage(aniId));
        return ResponseEntity.ok(body);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable Long aniId, HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        ratingService.deleteMyRating(userId, aniId);
        return ResponseEntity.noContent().build();
    }
}


