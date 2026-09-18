package com.taskflow.unit;

import com.taskflow.dto.ProjectSummaryResponse;
import com.taskflow.exception.TaskValidationException;
import com.taskflow.model.Priority;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private com.taskflow.repository.ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private com.taskflow.repository.UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    private Project proyectoConTareas;
    private Project proyectoVacio;

    @BeforeEach
    void setup() {
        proyectoConTareas = new Project(2L, "App Móvil", "d", 2L, null);
        proyectoVacio = new Project(3L, "Vacío", "d", 1L, null);
    }

    @Test
    void resumenDe_proyectoConTareas_calculaConteosYVencida() throws Exception {
        // tarea 1: TODO
        Task t1 = new Task(10L, "T-1", "d", TaskStatus.TODO, Priority.MED, 2L, null, null);
        // tarea 2: IN_PROGRESS
                Task t2 = new Task(11L, "T-2", "d", TaskStatus.IN_PROGRESS, Priority.HIGH, 2L, 1L, null);
        // tarea 3: DONE
                Task t3 = new Task(12L, "T-3", "d", TaskStatus.DONE, Priority.LOW, 2L, 1L, LocalDate.now().minusDays(5));
        // tarea 4: IN_PROGRESS vencida
                Task t4 = new Task(13L, "T-4", "d", TaskStatus.IN_PROGRESS, Priority.MED, 2L, 1L, LocalDate.now().minusDays(1));

        when(taskRepository.findByProjectId(2L)).thenReturn(List.of(t1, t2, t3, t4));

        ProjectSummaryResponse resumen = projectService.resumenDe(proyectoConTareas);

        assertEquals(2L, resumen.projectId());
        assertEquals("App Móvil", resumen.projectName());
        assertEquals(4, resumen.totalTasks());
        assertEquals(1, resumen.byStatus().get(TaskStatus.TODO.name()).intValue());
        assertEquals(2, resumen.byStatus().get(TaskStatus.IN_PROGRESS.name()).intValue());
        assertEquals(1, resumen.byStatus().get(TaskStatus.DONE.name()).intValue());
        assertEquals(1, resumen.overdue());
    }

    @Test
    void resumenDe_proyectoSinTareas_devuelveCeros() {
        when(taskRepository.findByProjectId(3L)).thenReturn(List.of());

        ProjectSummaryResponse resumen = projectService.resumenDe(proyectoVacio);

        assertEquals(3L, resumen.projectId());
        assertEquals("Vacío", resumen.projectName());
        assertEquals(0, resumen.totalTasks());
        assertEquals(0, resumen.byStatus().get(TaskStatus.TODO.name()).intValue());
        assertEquals(0, resumen.byStatus().get(TaskStatus.IN_PROGRESS.name()).intValue());
        assertEquals(0, resumen.byStatus().get(TaskStatus.DONE.name()).intValue());
        assertEquals(0, resumen.overdue());
    }
}
