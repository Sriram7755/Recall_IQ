package com.coderecall.backend.service;

import com.coderecall.backend.config.OpenRouterConfig;
import com.coderecall.backend.dto.OpenRouterRequest;
import com.coderecall.backend.dto.OpenRouterResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LlmService {


    private final RestClient restClient;
    private final OpenRouterConfig openRouterConfig;

    public LlmService(
            RestClient restClient,
            OpenRouterConfig openRouterConfig
    ) {
        this.restClient = restClient;
        this.openRouterConfig = openRouterConfig;
    }

    public String classifyTopic(
            String title,
            String description,
            List<String> leetcodeTopics
    ) {

        String topics = leetcodeTopics == null
                ? ""
                : leetcodeTopics.stream()
                .collect(Collectors.joining(", "));

        String prompt = """
            Classify the following LeetCode problem into exactly ONE primary DSA topic.

            Allowed Topics:
            Arrays
            Strings
            LinkedList
            Stack
            Queue
            Trees
            BST
            Graph
            DP
            Greedy
            Backtracking
            BinarySearch
            Heap
            Trie

            Problem Title:
            %s

            Problem Description:
            %s

            LeetCode Tags:
            %s

            Return ONLY the topic name.
            Do not explain.
            Do not return multiple topics.
            """
                .formatted(
                        title,
                        description,
                        topics
                );

        OpenRouterRequest request = new OpenRouterRequest(
                "google/gemma-4-26b-a4b-it-20260403:free",
                List.of(
                        new OpenRouterRequest.Message(
                                "user",
                                prompt
                        )
                )
        );

        OpenRouterResponse response = restClient.post()
                .uri("https://openrouter.ai/api/v1/chat/completions")
                .header(
                        "Authorization",
                        "Bearer " + openRouterConfig.getApiKey()
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .body(request)
                .retrieve()
                .body(OpenRouterResponse.class);

        return response.getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .trim();
    }

}
