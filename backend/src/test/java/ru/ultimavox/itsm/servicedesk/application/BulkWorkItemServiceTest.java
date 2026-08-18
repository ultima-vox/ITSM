package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;

@ExtendWith(MockitoExtension.class)
class BulkWorkItemServiceTest {
  @Mock AssignWorkItem assign;
  @Mock UpdateWorkItem update;
  @Mock TransitionWorkItem transition;
  private BulkWorkItemService service;

  @BeforeEach
  void setUp() {
    service = new BulkWorkItemService(assign, update, transition);
  }

  @Test
  void deDuplicatesAssignmentsWithoutChangingOrder() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    var result = service.assign(List.of(first, second, first), "agent", "team", "actor");
    assertThat(result.updated()).isEqualTo(2);
    verify(assign).assign(first, new AssignWorkItem.Command("agent", "team"), "actor");
    verify(assign).assign(second, new AssignWorkItem.Command("agent", "team"), "actor");
  }

  @Test
  void mapsHighPriorityWithoutAccidentallyCreatingCriticalPriority() {
    UUID id = UUID.randomUUID();
    service.setPriority(List.of(id), Priority.HIGH, "actor");
    ArgumentCaptor<UpdateWorkItem.Command> command = ArgumentCaptor.forClass(UpdateWorkItem.Command.class);
    verify(update).update(org.mockito.ArgumentMatchers.eq(id), command.capture(),
        org.mockito.ArgumentMatchers.eq("actor"));
    assertThat(command.getValue().impact().name()).isEqualTo("HIGH");
    assertThat(command.getValue().urgency().name()).isEqualTo("MEDIUM");
  }

  @Test
  void rejectsEmptyAndOversizedBatches() {
    assertThatThrownBy(() -> service.assign(List.of(), "agent", null, "actor"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
    List<UUID> oversized = java.util.stream.IntStream.rangeClosed(0, 200)
        .mapToObj(ignored -> UUID.randomUUID()).toList();
    assertThatThrownBy(() -> service.setPriority(oversized, Priority.LOW, "actor"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("200");
  }
}
