package com.coderecall.backend.repository;

import com.coderecall.backend.model.Repository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RepositoryRepository extends MongoRepository<Repository, String> {
    Optional<Repository> findByUserId(String userId);
    Optional<Repository> findByUserIdAndIsActive(String userId, boolean isActive);
}
