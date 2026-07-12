package com.coderecall.backend.dto;

public class GitHubFileRequest {

    private String message;
    private String content;
    private String branch;
    private String sha;

    public GitHubFileRequest() {
    }

    public GitHubFileRequest(
            String message,
            String content,
            String branch
    ) {
        this.message = message;
        this.content = content;
        this.branch = branch;
    }

    public GitHubFileRequest(
            String message,
            String content,
            String branch,
            String sha
    ) {
        this.message = message;
        this.content = content;
        this.branch = branch;
        this.sha = sha;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }


}
