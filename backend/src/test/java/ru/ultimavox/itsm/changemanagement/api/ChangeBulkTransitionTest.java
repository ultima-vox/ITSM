package ru.ultimavox.itsm.changemanagement.api;

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
import ru.ultimavox.itsm.changemanagement.application.CabVoteService;
import ru.ultimavox.itsm.changemanagement.application.ChangeCommands;
import ru.ultimavox.itsm.changemanagement.application.ChangeQuery;
import ru.ultimavox.itsm.changemanagement.domain.Change;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

class ChangeBulkTransitionTest {
    @Test void reportsPerItemFailuresAndChecksRecordPermission() {
        ChangeCommands commands = mock(ChangeCommands.class);
        AccessControl access = mock(AccessControl.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("operator");
        when(commands.transition(any(), eq(Change.Status.APPROVED), any(), any(), eq("operator")))
                .thenThrow(new IllegalStateException("invalid"));
        UUID id = UUID.randomUUID();

        var response = new ChangeController(mock(ChangeQuery.class), commands,
                mock(CabVoteService.class), access).bulkTransition(auth,
                new ChangeController.BulkTransitionRequest(List.of(id), Change.Status.APPROVED));

        assertThat(response.succeeded()).isZero();
        assertThat(response.results()).singleElement()
                .extracting(ChangeController.BulkTransitionResult::errorCode).isEqualTo("INVALID_TRANSITION");
        verify(access).require("operator", "change.write", "change", id.toString());
    }
}
