package com.wildme.wildbook_lite.bulkimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.exception.BulkImportException;
import com.wildme.wildbook_lite.exception.DuplicateBulkImportException;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock BulkImportTaskRepository taskRepo;
    @Mock BulkImportRunner runner;
    @Mock BulkImportMapper mapper;

    BulkImportService service;

    @BeforeEach
    void setUp() {
        // Real ObjectMapper (cheap, deterministic); everything else mocked.
        service = new BulkImportService(taskRepo, new ObjectMapper(), runner, mapper);
    }

    private BulkImportRequest request(List<String> fieldNames, List<List<String>> rows) {
        return new BulkImportRequest(UUID.randomUUID(), 1L, "encounter", fieldNames, rows, true);
    }

    @Test
    @DisplayName("submit rejects a duplicate bulkImportId with 409")
    void submit_duplicateId_throws() {
        when(taskRepo.findByBulkImportId(any())).thenReturn(Optional.of(new BulkImportTask()));

        var req = request(List.of("species", "datetime", "location"), List.of(List.of("Orca", "2020", "Bay")));

        // AssertJ: fluent exception assertion — type AND message in one chain.
        assertThatThrownBy(() -> service.submit(req))
            .isInstanceOf(DuplicateBulkImportException.class)
            .hasMessageContaining("already exists");

        // Mockito: a duplicate must never reach save() or dispatch.
        verify(taskRepo, never()).save(any());
        verify(runner, never()).run(any());
    }

    @Test
    @DisplayName("submit rejects a payload missing a required column with 400")
    void submit_missingRequiredColumn_throws() {
        when(taskRepo.findByBulkImportId(any())).thenReturn(Optional.empty());

        // "species" is required but absent.
        var req = request(List.of("datetime", "location"), List.of(List.of("2020", "Bay")));

        assertThatThrownBy(() -> service.submit(req))
            .isInstanceOf(BulkImportException.class)
            .hasMessageContaining("Missing required fields");
    }

    @Test
    @DisplayName("a fresh task starts in PENDING with zeroed counters")
    void newTask_defaults() {
        var task = new BulkImportTask();

        // AssertJ reads like a sentence and chains multiple checks.
        assertThat(task.getStatus()).isEqualTo(BulkImportStatus.PENDING);
        assertThat(task.getProcessedRows()).isZero();
        assertThat(task.getErrorsJson()).isNull();
    }
}
