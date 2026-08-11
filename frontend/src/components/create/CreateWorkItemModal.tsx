import { useEffect, useRef, useState } from 'react';
import {
  ArrowRight,
  CheckCircle2,
  CircleHelp,
  FilePlus2,
  Paperclip,
  Plus,
  ShieldCheck,
  X,
} from 'lucide-react';
import { Modal, Button, Select } from '@/components/ui';
import { DynamicForm, formRequiredKeys } from '@/components/form/DynamicForm';
import { useT } from '@/i18n';
import {
  createWorkItem,
  fetchFormDefinition,
  formatBytes,
  uploadAndLinkWorkItemAttachment,
  findDuplicateWorkItems,
  fetchWorkItemTemplates,
  type DuplicateWorkItemMatch,
  type FormDefinition,
  type WorkItemTemplate,
} from '@/api';
import type { CreateKind, ImpactLevel, UrgencyLevel } from '@/types';

interface Props {
  kind: CreateKind | null;
  onClose: () => void;
}

interface PendingFile {
  key: string;
  file: File;
}

/** Map service select keys to display labels stored on the work item. */
function serviceToStored(value: string, t: (k: string) => string): string {
  if (value === 'workplace') return t('create.serviceWorkplace');
  if (value === 'access') return t('create.serviceAccess');
  if (value === 'apps') return t('create.serviceApps');
  return value;
}

export function CreateWorkItemModal({ kind, onClose }: Props) {
  const t = useT();
  const open = kind !== null;
  const incident = kind === 'incident';
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [formDef, setFormDef] = useState<FormDefinition | null>(null);
  const [values, setValues] = useState<Record<string, string>>({
    title: '',
    description: '',
    service: '',
    impact: 'medium',
    urgency: incident ? 'high' : 'medium',
  });
  const [files, setFiles] = useState<PendingFile[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [uploadNote, setUploadNote] = useState('');
  const [duplicates, setDuplicates] = useState<DuplicateWorkItemMatch[]>([]);
  const [duplicatesLoading, setDuplicatesLoading] = useState(false);
  const [templates, setTemplates] = useState<WorkItemTemplate[]>([]);
  const [templateId, setTemplateId] = useState('');

  useEffect(() => {
    if (!open) {
      setValues({
        title: '',
        description: '',
        service: '',
        impact: 'medium',
        urgency: 'medium',
      });
      setFiles([]);
      setErrors({});
      setSubmitting(false);
      setSent(false);
      setUploadNote('');
      setDuplicates([]);
      setTemplates([]);
      setTemplateId('');
      return;
    }
    setValues((v) => ({
      ...v,
      impact: 'medium',
      urgency: kind === 'incident' ? 'high' : 'medium',
    }));
    let cancelled = false;
    void fetchWorkItemTemplates().then((items) => {
      if (!cancelled) setTemplates(items.filter((item) =>
        item.type === (kind === 'incident' ? 'INCIDENT' : 'SERVICE_REQUEST')));
    }).catch(() => { if (!cancelled) setTemplates([]); });
    void fetchFormDefinition('work-item').then((def) => {
      if (!cancelled) setFormDef(def);
    });
    return () => {
      cancelled = true;
    };
  }, [open, kind]);

  const applyTemplate = (id: string) => {
    setTemplateId(id);
    const template = templates.find((item) => item.id === id);
    if (!template) return;
    setValues((current) => ({ ...current,
      title: template.title, description: template.description, service: template.service,
      impact: template.impact.toLowerCase(), urgency: template.urgency.toLowerCase(),
    }));
    setErrors({});
  };

  useEffect(() => {
    const title = values.title.trim();
    if (!open || title.length < 8) { setDuplicates([]); setDuplicatesLoading(false); return; }
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setDuplicatesLoading(true);
      void findDuplicateWorkItems(title, values.description.trim(), controller.signal)
        .then(setDuplicates)
        .catch((error: unknown) => { if (!(error instanceof DOMException && error.name === 'AbortError')) setDuplicates([]); })
        .finally(() => { if (!controller.signal.aborted) setDuplicatesLoading(false); });
    }, 400);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [open, values.title, values.description]);

  if (!kind) return null;

  const setField = (key: string, value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((e) => {
        const next = { ...e };
        delete next[key];
        return next;
      });
    }
  };

  const validate = () => {
    const next: Record<string, string> = {};
    const required = formDef
      ? formRequiredKeys(formDef)
      : ['title', 'description', 'service'];
    for (const key of required) {
      if (!(values[key] ?? '').trim()) {
        if (key === 'title') next.title = t('create.validationTitle');
        else if (key === 'description') next.description = t('create.validationDetails');
        else if (key === 'service') next.service = t('create.validationService');
        else next[key] = t('app.required');
      }
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const onPickFiles = (list: FileList | null) => {
    if (!list?.length) return;
    const next: PendingFile[] = Array.from(list).map((file) => ({
      key: `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2, 6)}`,
      file,
    }));
    setFiles((prev) => {
      const names = new Set(prev.map((p) => `${p.file.name}:${p.file.size}`));
      const unique = next.filter((n) => !names.has(`${n.file.name}:${n.file.size}`));
      return [...prev, ...unique];
    });
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const removeFile = (key: string) => {
    setFiles((prev) => prev.filter((f) => f.key !== key));
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true);
    setUploadNote('');
    try {
      const serviceLabel = serviceToStored(values.service, t);
      const created = await createWorkItem({
        kind,
        title: values.title.trim(),
        description: values.description.trim(),
        service: serviceLabel,
        impact: (values.impact as ImpactLevel) || 'medium',
        urgency: (values.urgency as UrgencyLevel) || 'medium',
      });

      if (files.length > 0) {
        let ok = 0;
        for (const { file } of files) {
          try {
            await uploadAndLinkWorkItemAttachment(created.id, file);
            ok += 1;
          } catch {
            /* keep going; surface partial result */
          }
        }
        if (ok < files.length) {
          setUploadNote(t('create.uploadPartial', { n: ok, total: files.length }));
        }
      }

      setSent(true);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="md"
      className="create-modal"
      labelledBy="create-title"
    >
      {sent ? (
        <div className="success-state">
          <span>
            <CheckCircle2 size={24} />
          </span>
          <h2>{t('create.successTitle')}</h2>
          <p>{t('create.successText')}</p>
          {files.length > 0 && (
            <ul className="attachment-list attachment-list--success">
              {files.map(({ key, file }) => (
                <li key={key} className="attachment-chip">
                  <Paperclip size={12} aria-hidden />
                  <span className="attachment-chip__name" title={file.name}>
                    {file.name}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {uploadNote && <p className="field__hint">{uploadNote}</p>}
          <Button variant="primary" onClick={onClose}>
            {t('app.done')}
          </Button>
        </div>
      ) : (
        <>
          <div className="dialog-head">
            <div>
              <span className={`dialog-type dialog-type--${kind}`}>
                {incident ? <CircleHelp size={19} /> : <FilePlus2 size={19} />}
              </span>
              <div>
                <p>
                  {incident
                    ? t('create.incidentKicker')
                    : t('create.requestKicker')}
                </p>
                <h2 id="create-title">
                  {incident
                    ? t('create.incidentTitle')
                    : t('create.requestTitle')}
                </h2>
              </div>
            </div>
            <button
              type="button"
              className="icon-btn"
              aria-label={t('app.close')}
              onClick={onClose}
            >
              <X size={19} />
            </button>
          </div>

          <p className="dialog-intro">
            {incident ? t('create.incidentIntro') : t('create.requestIntro')}
          </p>

          <form onSubmit={onSubmit} noValidate>
            {templates.length > 0 && (
              <Select
                label={t('create.template')}
                value={templateId}
                placeholder={t('create.templatePlaceholder')}
                options={templates.map((template) => ({ value: template.id, label: template.name }))}
                onChange={(event) => applyTemplate(event.target.value)}
              />
            )}
            {formDef ? (
              <DynamicForm
                definition={formDef}
                values={values}
                onChange={setField}
                errors={errors}
                layout="create"
                autoFocusFirst
              />
            ) : (
              <p className="field__hint">{t('app.loading')}</p>
            )}

            {(duplicatesLoading || duplicates.length > 0) && (
              <aside className="panel mt-4" aria-live="polite" aria-labelledby="duplicate-heading">
                <h3 id="duplicate-heading">{t('create.possibleDuplicates')}</h3>
                {duplicatesLoading ? <p className="field__hint">{t('app.loading')}</p> : (
                  <ul className="attachment-list">{duplicates.map((match) => (
                    <li key={match.id} className="attachment-chip"><a href={`/work-items/${match.id}`} target="_blank" rel="noreferrer">
                      <b>{match.number}</b> · {match.title} · {Math.round(match.score * 100)}%
                    </a></li>
                  ))}</ul>
                )}
                <p className="field__hint">{t('create.duplicateHint')}</p>
              </aside>
            )}

            <div className="form-row create-attachments-row">
              <label className="field">
                <span className="field__label">{t('create.attachment')}</span>
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  className="sr-only"
                  onChange={(e) => onPickFiles(e.target.files)}
                />
                <button
                  type="button"
                  className="attachment-btn"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Plus size={14} />
                  {t('create.addFile')}
                </button>
              </label>
            </div>

            {files.length > 0 && (
              <ul className="attachment-list" aria-label={t('create.attachment')}>
                {files.map(({ key, file }) => (
                  <li key={key} className="attachment-chip">
                    <Paperclip size={12} aria-hidden />
                    <span className="attachment-chip__name" title={file.name}>
                      {file.name}
                    </span>
                    <span className="attachment-chip__size">
                      {formatBytes(file.size)}
                    </span>
                    <button
                      type="button"
                      className="attachment-chip__remove"
                      aria-label={t('create.removeFile')}
                      onClick={() => removeFile(key)}
                    >
                      <X size={12} />
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <div className="dialog-actions">
              <span>
                <ShieldCheck size={14} aria-hidden />
                {t('create.auditNote')}
              </span>
              <div>
                <Button variant="ghost" onClick={onClose}>
                  {t('app.cancel')}
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  disabled={submitting || !formDef}
                  iconRight={<ArrowRight size={15} />}
                >
                  {t('create.submit')}
                </Button>
              </div>
            </div>
          </form>
        </>
      )}
    </Modal>
  );
}
