package com.coderecall.backend.service;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

@Service
public class TopicClassificationService {

    private static final Set<String> KNOWN_TOPICS = Set.of(
            "Array", "String", "Linked List", "Tree", "Graph", "Dynamic Programming",
            "Stack", "Queue", "Heap", "Hash Table", "Binary Search", "Greedy",
            "Backtracking", "Two Pointers", "Sorting", "Sliding Window", "Trie", "Math", "Bit Manipulation"
    );

    public String resolveTopic(List<String> leetcodeTopics) {
        if (leetcodeTopics == null || leetcodeTopics.isEmpty()) {
            return "Other";
        }

        // 1. Try exact matches first
        for (String tag : leetcodeTopics) {
            if (KNOWN_TOPICS.contains(tag)) {
                return tag;
            }
        }

        // 2. Try case-insensitive substring matches
        for (String tag : leetcodeTopics) {
            for (String known : KNOWN_TOPICS) {
                if (tag.toLowerCase().contains(known.toLowerCase()) ||
                    known.toLowerCase().contains(tag.toLowerCase())) {
                    return known;
                }
            }
        }

        // 3. Fallback: use the first tag, format properly
        String firstTag = leetcodeTopics.get(0).trim();
        if (!firstTag.isEmpty()) {
            return Character.toUpperCase(firstTag.charAt(0)) + firstTag.substring(1);
        }

        return "Other";
    }
}
