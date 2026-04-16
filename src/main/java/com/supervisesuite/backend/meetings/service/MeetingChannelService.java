package com.supervisesuite.backend.meetings.service;

import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingChannelRequest;
import java.util.List;

public interface MeetingChannelService {
    List<MeetingChannelDto> listForSupervisor(String authenticatedUserId, String projectId);

    List<MeetingChannelDto> listForStudent(String authenticatedUserId, String projectId);

    MeetingChannelDto createAsSupervisor(
        String authenticatedUserId,
        String projectId,
        CreateMeetingChannelRequest request
    );

    MeetingChannelDto createAsStudent(
        String authenticatedUserId,
        String projectId,
        CreateMeetingChannelRequest request
    );

    MeetingChannelDto updateAsSupervisor(
        String authenticatedUserId,
        String projectId,
        String channelId,
        UpdateMeetingChannelRequest request
    );

    void deleteAsSupervisor(String authenticatedUserId, String projectId, String channelId);

    MeetingChannelDto approveAsSupervisor(String authenticatedUserId, String projectId, String channelId);
}

