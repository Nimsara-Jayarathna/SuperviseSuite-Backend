package com.supervisesuite.backend.meetings.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateMeetingChannelRequest {

    @NotBlank(message = "Platform is required.")
    private String platform;

    @NotBlank(message = "Channel name is required.")
    private String channelName;

    @NotBlank(message = "Link or identifier is required.")
    private String linkOrIdentifier;

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getLinkOrIdentifier() {
        return linkOrIdentifier;
    }

    public void setLinkOrIdentifier(String linkOrIdentifier) {
        this.linkOrIdentifier = linkOrIdentifier;
    }
}

