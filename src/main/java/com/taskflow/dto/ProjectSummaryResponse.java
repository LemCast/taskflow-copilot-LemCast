package com.taskflow.dto;

import java.util.Map;

/**
 * Resumen de un proyecto para el tablero: conteos por estado y vencidas.
 */
public record ProjectSummaryResponse(
        Long projectId,
        String projectName,
        int totalTasks,
        Map<String, Integer> byStatus,
        int overdue
) {
}
