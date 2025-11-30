package com.example.workflow;

public class PauseForUserReviewException extends RuntimeException {
    private final String requestId;
    private final String reviewData;
    
    public PauseForUserReviewException(String requestId, String reviewData, String message) {
        super(message);
        this.requestId = requestId;
        this.reviewData = reviewData;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getReviewData() {
        return reviewData;
    }
}

