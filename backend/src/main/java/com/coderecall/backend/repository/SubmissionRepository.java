package com.coderecall.backend.repository;

import com.coderecall.backend.model.Submission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends MongoRepository<Submission, String> {

    List<Submission> findByUserId(String userId);

    Optional<Submission> findByUserIdAndProblemIdAndLanguage(String userId, String problemId, String language);

    long countByUserId(String userId);

    long countByUserIdAndDifficulty(String userId, String difficulty);

    List<Submission> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}