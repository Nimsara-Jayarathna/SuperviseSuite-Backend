package com.supervisesuite.backend.meetings.dto;

import java.time.LocalDate;
import java.util.UUID;

public class UpdateMeetingRecordRequest {

    private LocalDate meetingDate;

    private Integer durationMinutes;

    private String discussionSummary;

    private String discussionDetails;

    private UUID channelId;

    public LocalDate getMeetingDate() {
        return meetingDate;
    }

    public void setMeetingDate(LocalDate meetingDate) {
        this.meetingDate = meetingDate;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getDiscussionSummary() {
        return discussionSummary;
    }

    public void setDiscussionSummary(String discussionSummary) {
        this.discussionSummary = discussionSummary;
    }

    public String getDiscussionDetails() {
        return discussionDetails;
    }

    public void setDiscussionDetails(String discussionDetails) {
        this.discussionDetails = discussionDetails;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public void setChannelId(UUID channelId) {
        this.channelId = channelId;
    }
}

