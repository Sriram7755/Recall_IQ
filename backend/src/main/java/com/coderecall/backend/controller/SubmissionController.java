package com.coderecall.backend.controller;

import com.coderecall.backend.model.Submission;
import com.coderecall.backend.repository.SubmissionRepository;
import com.coderecall.backend.security.UserPrincipal;
import com.coderecall.backend.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@CrossOrigin("*")
public class SubmissionController {

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @PostMapping
    public ResponseEntity<Submission> saveSubmission(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Submission submission
    ) {
        Submission saved = submissionService.saveSubmission(userPrincipal.getId(), submission);
        if (Boolean.TRUE.equals(saved.getIgnoredDuplicate())) {
            return ResponseEntity.ok(saved);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<Submission>> getMySubmissions(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(submissionRepository.findByUserId(userPrincipal.getId()));
    }
}