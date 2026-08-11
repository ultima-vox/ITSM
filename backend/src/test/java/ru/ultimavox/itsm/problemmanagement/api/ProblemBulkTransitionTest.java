package ru.ultimavox.itsm.problemmanagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.problemmanagement.application.ProblemCommands;
import ru.ultimavox.itsm.problemmanagement.application.ProblemQuery;
import ru.ultimavox.itsm.problemmanagement.domain.Problem;

class ProblemBulkTransitionTest {
    @Test void reportsPerItemFailuresAndChecksRecordPermission() {
        ProblemCommands commands = mock(ProblemCommands.class);
        AccessControl access = mock(AccessControl.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("operator");
        when(commands.transition(any(), eq(Problem.Status.RESOLVED), any(), any(), any(), eq("operator")))
                .thenThrow(new IllegalStateException("invalid"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        var response = new ProblemController(mock(ProblemQuery.class), commands, access)
                .bulkTransition(auth, new ProblemController.BulkTransitionRequest(
                        List.of(first, second), Problem.Status.RESOLVED));

        assertThat(response.succeeded()).isZero();
        assertThat(response.results()).extracting(ProblemController.BulkTransitionResult::errorCode)
                .containsOnly("INVALID_TRANSITION");
        verify(access).require("operator", "problem.write", "problem", first.toString());
        verify(access).require("operator", "problem.write", "problem", second.toString());
    }
}
