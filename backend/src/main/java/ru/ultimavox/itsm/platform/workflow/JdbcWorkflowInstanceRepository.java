package ru.ultimavox.itsm.platform.workflow;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcWorkflowInstanceRepository implements WorkflowInstanceRepository {

    private final JdbcTemplate jdbc;

    JdbcWorkflowInstanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkflowInstance> findByObject(String objectType, String objectId) {
        List<WorkflowInstance> rows = jdbc.query(
                """
                SELECT id, object_type, object_id, state, definition_version, version, updated_at
                FROM workflow_instance
                WHERE org_id = ? AND object_type = ? AND object_id = ?
                """,
                (rs, i) -> new WorkflowInstance(
                        rs.getObject("id", UUID.class),
                        rs.getString("object_type"),
                        rs.getString("object_id"),
                        rs.getString("state"),
                        rs.getInt("definition_version"),
                        rs.getInt("version"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                OrganizationContext.current(), objectType, objectId
        );
        return rows.stream().findFirst();
    }

    @Override
    public WorkflowInstance insert(WorkflowInstance instance) {
        Instant now = instance.updatedAt() != null ? instance.updatedAt() : Instant.now();
        jdbc.update(
                """
                INSERT INTO workflow_instance (id, org_id, object_type, object_id, state, definition_version, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                instance.id(),
                OrganizationContext.current(),
                instance.objectType(),
                instance.objectId(),
                instance.state(),
                instance.definitionVersion(),
                instance.version(),
                Timestamp.from(now)
        );
        return new WorkflowInstance(
                instance.id(),
                instance.objectType(),
                instance.objectId(),
                instance.state(),
                instance.definitionVersion(),
                instance.version(),
                now
        );
    }

    @Override
    public WorkflowInstance updateState(WorkflowInstance instance, String newState, int expectedVersion) {
        Instant now = Instant.now();
        int updated = jdbc.update(
                """
                UPDATE workflow_instance
                SET state = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND org_id = ? AND version = ?
                """,
                newState,
                Timestamp.from(now),
                instance.id(),
                OrganizationContext.current(),
                expectedVersion
        );
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Workflow instance concurrent update for " + instance.objectType() + "/" + instance.objectId()
            );
        }
        return new WorkflowInstance(
                instance.id(),
                instance.objectType(),
                instance.objectId(),
                newState,
                instance.definitionVersion(),
                expectedVersion + 1,
                now
        );
    }
}
