package com.supervisesuite.backend.supervisor.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class CreateSupervisorProjectResponse {
    private UUID id;
    private String title;
    private String summary;
    private String batch;
    private String semester;
    private String lifecycleStatus;
    private Integer progressPercent;
    private LocalDate milestoneDate;
    private List<StudentAssignment> students;
    private StudentAssignment leader;
    private List<Milestone> milestones;

    public CreateSupervisorProjectResponse() {
    }

    public CreateSupervisorProjectResponse(
        UUID id,
        String title,
        String summary,
        String batch,
        String semester,
        String lifecycleStatus,
        Integer progressPercent,
        LocalDate milestoneDate,
        List<StudentAssignment> students,
        StudentAssignment leader,
        List<Milestone> milestones
    ) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.batch = batch;
        this.semester = semester;
        this.lifecycleStatus = lifecycleStatus;
        this.progressPercent = progressPercent;
        this.milestoneDate = milestoneDate;
        this.students = students;
        this.leader = leader;
        this.milestones = milestones;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public LocalDate getMilestoneDate() {
        return milestoneDate;
    }

    public void setMilestoneDate(LocalDate milestoneDate) {
        this.milestoneDate = milestoneDate;
    }

    public List<StudentAssignment> getStudents() {
        return students;
    }

    public void setStudents(List<StudentAssignment> students) {
        this.students = students;
    }

    public StudentAssignment getLeader() {
        return leader;
    }

    public void setLeader(StudentAssignment leader) {
        this.leader = leader;
    }

    public List<Milestone> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<Milestone> milestones) {
        this.milestones = milestones;
    }

    public static class StudentAssignment {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String registrationNumber;

        public StudentAssignment() {
        }

        public StudentAssignment(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String registrationNumber
        ) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.registrationNumber = registrationNumber;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRegistrationNumber() {
            return registrationNumber;
        }

        public void setRegistrationNumber(String registrationNumber) {
            this.registrationNumber = registrationNumber;
        }
    }

    public static class Milestone {
        private UUID id;
        private String title;
        private String description;
        private LocalDate dueDate;
        private String status;
        private Integer sequenceNo;

        public Milestone() {
        }

        public Milestone(
            UUID id,
            String title,
            String description,
            LocalDate dueDate,
            String status,
            Integer sequenceNo
        ) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.dueDate = dueDate;
            this.status = status;
            this.sequenceNo = sequenceNo;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public void setDueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getSequenceNo() {
            return sequenceNo;
        }

        public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
        }
    }
}
