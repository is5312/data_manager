package com.datamanager.backend.service;

import com.datamanager.backend.dto.BatchStatusDto;
import com.datamanager.backend.service.impl.BatchStatusServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchStatusServiceTest {

    @Mock
    private JobExplorer jobExplorer;

    @InjectMocks
    private BatchStatusServiceImpl batchStatusService;

    private JobExecution jobExecution;
    private StepExecution stepExecution;

    @BeforeEach
    void setUp() {
        jobExecution = mock(JobExecution.class);
        when(jobExecution.getId()).thenReturn(1L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusMinutes(5));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

        stepExecution = new StepExecution("step1", jobExecution);
        stepExecution.setReadCount(100);
        stepExecution.setWriteCount(100);
        stepExecution.setProcessSkipCount(0);

        // Mock getStepExecutions to return our step execution
        when(jobExecution.getStepExecutions()).thenReturn(Collections.singletonList(stepExecution));
        when(jobExecution.getAllFailureExceptions()).thenReturn(Collections.emptyList());
    }

    @Test
    void getBatchStatus_ReturnsStatus_ForRunningJob() {
        // Given
        when(jobExecution.getStatus()).thenReturn(BatchStatus.STARTED);
        when(jobExecution.getEndTime()).thenReturn(null);
        when(jobExplorer.getJobExecution(1L)).thenReturn(jobExecution);

        // When
        BatchStatusDto result = batchStatusService.getBatchStatus(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBatchId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("STARTED");
        verify(jobExplorer, times(1)).getJobExecution(1L);
    }

    @Test
    void getBatchStatus_ReturnsStatus_ForCompletedJob() {
        // Given
        when(jobExplorer.getJobExecution(1L)).thenReturn(jobExecution);

        // When
        BatchStatusDto result = batchStatusService.getBatchStatus(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBatchId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getExitCode()).isEqualTo("COMPLETED");
        verify(jobExplorer, times(1)).getJobExecution(1L);
    }

    @Test
    void getBatchStatus_ReturnsStatus_ForFailedJob() {
        // Given
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getExitStatus()).thenReturn(ExitStatus.FAILED);
        when(jobExplorer.getJobExecution(1L)).thenReturn(jobExecution);

        // When
        BatchStatusDto result = batchStatusService.getBatchStatus(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getExitCode()).isEqualTo("FAILED");
        verify(jobExplorer, times(1)).getJobExecution(1L);
    }

    @Test
    void getBatchStatus_IncludesAllMetrics_ReadWriteSkipCounts() {
        // Given
        when(jobExplorer.getJobExecution(1L)).thenReturn(jobExecution);

        // When
        BatchStatusDto result = batchStatusService.getBatchStatus(1L);

        // Then
        assertThat(result.getReadCount()).isEqualTo(100L);
        assertThat(result.getWriteCount()).isEqualTo(100L);
        assertThat(result.getSkipCount()).isEqualTo(0L);
    }

    @Test
    void getBatchStatus_IncludesErrorMessages() {
        // Given
        RuntimeException testError = new RuntimeException("Test error");
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getExitStatus()).thenReturn(ExitStatus.FAILED);
        when(jobExecution.getAllFailureExceptions()).thenReturn(Collections.singletonList(testError));
        when(jobExplorer.getJobExecution(1L)).thenReturn(jobExecution);

        // When
        BatchStatusDto result = batchStatusService.getBatchStatus(1L);

        // Then
        assertThat(result.getFailureExceptions()).isNotNull();
        assertThat(result.getFailureExceptions()).isNotEmpty();
    }

    @Test
    void getBatchStatus_ThrowsException_ForNonExistentBatch() {
        // Given
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> batchStatusService.getBatchStatus(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch not found");

        verify(jobExplorer, times(1)).getJobExecution(999L);
    }

    @Test
    void getBatchStatus_IncludesAllStatusFields() {
        // Given
        when(jobExplorer.getJobExecution(1L)).thenReturn(jobExecution);

        // When
        BatchStatusDto result = batchStatusService.getBatchStatus(1L);

        // Then
        assertThat(result.getBatchId()).isNotNull();
        assertThat(result.getStatus()).isNotNull();
        assertThat(result.getExitCode()).isNotNull();
        assertThat(result.getStartTime()).isNotNull();
        assertThat(result.getEndTime()).isNotNull();
        assertThat(result.getReadCount()).isNotNull();
        assertThat(result.getWriteCount()).isNotNull();
        assertThat(result.getSkipCount()).isNotNull();
    }
}

