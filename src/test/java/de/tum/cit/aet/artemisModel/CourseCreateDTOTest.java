package de.tum.cit.aet.artemisModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CourseCreateDTOTest {

    @Test
    void forBenchmarking_setsRequiredDefaults() {
        CourseCreateDTO dto = CourseCreateDTO.forBenchmarking("Benchmark Course", "bench123");

        assertEquals("Benchmark Course", dto.title());
        assertEquals("bench123", dto.shortName());
        assertEquals(Language.ENGLISH, dto.language());
        assertEquals(Boolean.FALSE, dto.onlineCourse());
        assertEquals(Boolean.FALSE, dto.enrollmentEnabled());
        assertEquals(1, dto.accuracyOfScores());
        assertEquals("COMMUNICATION_AND_MESSAGING", dto.courseInformationSharingConfiguration());

        assertEquals(0, dto.maxComplaintTimeDays());
        assertEquals(0, dto.maxRequestMoreFeedbackTimeDays());
        assertEquals(0, dto.maxComplaintTextLimit());
        assertEquals(0, dto.maxComplaintResponseTextLimit());
        assertEquals(0, dto.maxComplaints());
        assertEquals(0, dto.maxTeamComplaints());

        assertFalse(dto.testCourse());
        assertFalse(dto.unenrollmentEnabled());
        assertFalse(dto.faqEnabled());
        assertFalse(dto.learningPathsEnabled());
        assertFalse(dto.studentCourseAnalyticsDashboardEnabled());
        assertFalse(dto.restrictedAthenaModulesAccess());

        assertNull(dto.description());
        assertNull(dto.semester());
        assertNull(dto.studentGroupName());
        assertNull(dto.teachingAssistantGroupName());
        assertNull(dto.editorGroupName());
        assertNull(dto.instructorGroupName());
        assertNull(dto.startDate());
        assertNull(dto.endDate());
        assertNull(dto.enrollmentStartDate());
        assertNull(dto.enrollmentEndDate());
        assertNull(dto.unenrollmentEndDate());
        assertNull(dto.defaultProgrammingLanguage());
        assertNull(dto.color());
        assertNull(dto.enrollmentConfirmationMessage());
        assertNull(dto.presentationScore());
        assertNull(dto.maxPoints());
        assertNull(dto.timeZone());
    }
}
