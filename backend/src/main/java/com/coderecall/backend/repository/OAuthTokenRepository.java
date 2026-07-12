package com.coderecall.backend.repository;

import com.coderecall.backend.model.OAuthToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends MongoRepository<OAuthToken, String> {
    Optional<OAuthToken> findByUserId(String userId);
    void deleteByUserId(String userId);
}
