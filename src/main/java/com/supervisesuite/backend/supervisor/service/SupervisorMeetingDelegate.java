package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.CreateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;
import com.supervisesuite.backend.meetings.dto.MeetingRecordDto;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.service.MeetingChannelService;
import com.supervisesuite.backend.meetings.service.MeetingRecordService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorMeetingDelegate {

    private final MeetingChannelService meetingChannelService;
    private final MeetingRecordService meetingRecordService;

    SupervisorMeetingDelegate(
            MeetingChannelService meetingChannelService,
            MeetingRecordService meetingRecordService) {
        this.meetingChannelService = meetingChannelService;
        this.meetingRecordService = meetingRecordService;
    }

    @Transactional(readOnly = true)
    List<MeetingChannelDto> getProjectMeetingChannels(String authenticatedUserId, String projectId) {
        return meetingChannelService.listForSupervisor(authenticatedUserId, projectId);
    }

    @Transactional
    MeetingChannelDto addProjectMeetingChannel(
            String authenticatedUserId,
            String projectId,
            CreateMeetingChannelRequest request) {
        return meetingChannelService.createAsSupervisor(authenticatedUserId, projectId, request);
    }

    @Transactional
    MeetingChannelDto updateProjectMeetingChannel(
            String authenticatedUserId,
            String projectId,
            String channelId,
            UpdateMeetingChannelRequest request) {
        return meetingChannelService.updateAsSupervisor(authenticatedUserId, projectId, channelId, request);
    }

    @Transactional
    void deleteProjectMeetingChannel(String authenticatedUserId, String projectId, String channelId) {
        meetingChannelService.deleteAsSupervisor(authenticatedUserId, projectId, channelId);
    }

    @Transactional
    MeetingChannelDto approveProjectMeetingChannel(String authenticatedUserId, String projectId, String channelId) {
        return meetingChannelService.approveAsSupervisor(authenticatedUserId, projectId, channelId);
    }

    @Transactional(readOnly = true)
    List<MeetingRecordDto> getProjectMeetingRecords(String authenticatedUserId, String projectId) {
        return meetingRecordService.listForSupervisor(authenticatedUserId, projectId);
    }

    @Transactional
    MeetingRecordDto addProjectMeetingRecord(
            String authenticatedUserId,
            String projectId,
            CreateMeetingRecordRequest request) {
        return meetingRecordService.createAsSupervisor(authenticatedUserId, projectId, request);
    }

    @Transactional
    MeetingRecordDto updateProjectMeetingRecord(
            String authenticatedUserId,
            String projectId,
            String recordId,
            UpdateMeetingRecordRequest request) {
        return meetingRecordService.updateAsSupervisor(authenticatedUserId, projectId, recordId, request);
    }

    @Transactional
    void deleteProjectMeetingRecord(String authenticatedUserId, String projectId, String recordId) {
        meetingRecordService.deleteAsSupervisor(authenticatedUserId, projectId, recordId);
    }

    @Transactional
    MeetingRecordDto approveProjectMeetingRecord(String authenticatedUserId, String projectId, String recordId) {
        return meetingRecordService.approveAsSupervisor(authenticatedUserId, projectId, recordId);
    }
}
