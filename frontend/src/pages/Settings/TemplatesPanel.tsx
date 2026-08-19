import { useMemo, useState } from 'react';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useToast } from '@/hooks/useToast';
import {
  archiveWorkItemTemplate,
  createWorkItemTemplate,
  fetchWorkItemTemplates,
  updateWorkItemTemplate,
  type WorkItemTemplate,
  type WorkItemTemplateDraft,
} from '@/api';
import { Button, ErrorState, Input, Select, Textarea } from '@/components/ui';

const emptyDraft = (): WorkItemTemplateDraft => ({
  name: '',
  type: 'INCIDENT',
  title: '',
  description: '',
  service: '',
  impact: 'MEDIUM',
  urgency: 'MEDIUM',
  teamId: null,
});

export function TemplatesPanel() {
  const t = useT();
  const { success, error: toastError } = useToast();
  const list = useAsync(() => fetchWorkItemTemplates(true), []);
  const [draft, setDraft] = useState<WorkItemTemplateDraft>(emptyDraft);
  const [editing, setEditing] = useState<WorkItemTemplate | null>(null);
  const [busy, setBusy] = useState(false);

  const rows = useMemo(() => list.data ?? [], [list.data]);

  const patch = (part: Partial<WorkItemTemplateDraft>) =>
    setDraft((current) => ({ ...current, ...part }));

  const startEdit = (row: WorkItemTemplate) => {
    setEditing(row);
    setDraft({
      name: row.name,
      type: row.type,
      title: row.title,
      description: row.description,
      service: row.service,
      impact: row.impact,
      urgency: row.urgency,
      teamId: row.teamId ?? null,
    });
  };

  const resetForm = () => {
    setEditing(null);
    setDraft(emptyDraft());
  };

  const save = async () => {
    if (
      !draft.name.trim() ||
      !draft.title.trim() ||
      !draft.service.trim() ||
      !draft.description.trim()
    ) {
      toastError(t('settings.templates.validation'));
      return;
    }
    setBusy(true);
    try {
      if (editing) {
        await updateWorkItemTemplate(editing.id, editing.version, draft);
        success(t('settings.templates.updated'));
      } else {
        await createWorkItemTemplate(draft);
        success(t('settings.templates.created'));
      }
      resetForm();
      list.reload();
    } catch {
      toastError(t('settings.templates.saveFailed'));
    } finally {
      setBusy(false);
    }
  };

  const archive = async (row: WorkItemTemplate) => {
    setBusy(true);
    try {
      await archiveWorkItemTemplate(row.id, row.version);
      if (editing?.id === row.id) resetForm();
      success(t('settings.templates.archived'));
      list.reload();
    } catch {
      toastError(t('settings.templates.saveFailed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="settings-panel-stack">
      <section className="panel settings-card settings-card--solo">
        <h2>{t('settings.templates.title')}</h2>
        <p className="panel-hint">{t('settings.templates.hint')}</p>
        {list.loading && !list.data ? (
          <p className="muted">{t('app.loading')}</p>
        ) : list.error ? (
          <ErrorState onRetry={list.reload} />
        ) : rows.length === 0 ? (
          <p className="muted">{t('settings.templates.empty')}</p>
        ) : (
          <ul className="settings-template-list">
            {rows.map((row) => (
              <li key={row.id} className="settings-template-list__row">
                <button
                  type="button"
                  className="text-button"
                  onClick={() => startEdit(row)}
                >
                  <b>{row.name}</b>
                  <span className="muted">
                    {' '}
                    · {row.type} · {row.service}
                    {row.active ? '' : ` · ${t('settings.templates.inactive')}`}
                  </span>
                </button>
                {row.active && (
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={busy}
                    onClick={() => void archive(row)}
                  >
                    {t('settings.templates.archive')}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel settings-card">
        <h3>
          {editing
            ? t('settings.templates.edit', { name: editing.name })
            : t('settings.templates.create')}
        </h3>
        <div className="settings-template-form">
          <Input
            label={t('settings.templates.name')}
            value={draft.name}
            onChange={(e) => patch({ name: e.target.value })}
          />
          <Select
            label={t('settings.templates.type')}
            value={draft.type}
            onChange={(e) =>
              patch({ type: e.target.value as WorkItemTemplateDraft['type'] })
            }
            options={[
              { value: 'INCIDENT', label: t('workItemType.incident') },
              { value: 'SERVICE_REQUEST', label: t('workItemType.request') },
            ]}
          />
          <Input
            label={t('settings.templates.itemTitle')}
            value={draft.title}
            onChange={(e) => patch({ title: e.target.value })}
          />
          <Input
            label={t('settings.templates.service')}
            value={draft.service}
            onChange={(e) => patch({ service: e.target.value })}
          />
          <Select
            label={t('settings.templates.impact')}
            value={draft.impact}
            onChange={(e) =>
              patch({
                impact: e.target.value as WorkItemTemplateDraft['impact'],
              })
            }
            options={[
              { value: 'LOW', label: t('priority.low') },
              { value: 'MEDIUM', label: t('priority.medium') },
              { value: 'HIGH', label: t('priority.high') },
            ]}
          />
          <Select
            label={t('settings.templates.urgency')}
            value={draft.urgency}
            onChange={(e) =>
              patch({
                urgency: e.target.value as WorkItemTemplateDraft['urgency'],
              })
            }
            options={[
              { value: 'LOW', label: t('priority.low') },
              { value: 'MEDIUM', label: t('priority.medium') },
              { value: 'HIGH', label: t('priority.high') },
            ]}
          />
          <Textarea
            label={t('settings.templates.description')}
            value={draft.description}
            onChange={(e) => patch({ description: e.target.value })}
            rows={3}
          />
        </div>
        <div className="settings-template-form__actions">
          <Button variant="primary" disabled={busy} onClick={() => void save()}>
            {editing ? t('app.save') : t('settings.templates.create')}
          </Button>
          {editing && (
            <Button variant="ghost" disabled={busy} onClick={resetForm}>
              {t('app.cancel')}
            </Button>
          )}
        </div>
      </section>
    </div>
  );
}
