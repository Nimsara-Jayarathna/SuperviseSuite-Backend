package com.supervisesuite.backend.meetings.service;

import com.supervisesuite.backend.meetings.dto.CreateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.dto.MeetingRecordDto;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingRecordRequest;
import java.util.List;

public interface MeetingRecordService {
    List<MeetingRecordDto> listForSupervisor(String authenticatedUserId, String projectId);

    List<MeetingRecordDto> listForStudent(String authenticatedUserId, String projectId);

    MeetingRecordDto createAsSupervisor(
        String authenticatedUserId,
        String projectId,
        CreateMeetingRecordRequest request
    );

    MeetingRecordDto createAsStudent(
        String authenticatedUserId,
        String projectId,
        CreateMeetingRecordRequest request
    );

    MeetingRecordDto updateAsSupervisor(
        String authenticatedUserId,
        String projectId,
        String recordId,
        UpdateMeetingRecordRequest request
    );

    void deleteAsSupervisor(String authenticatedUserId, String projectId, String recordId);

    MeetingRecordDto approveAsSupervisor(String authenticatedUserId, String projectId, String recordId);
}

