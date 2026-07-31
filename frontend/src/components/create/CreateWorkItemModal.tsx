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
import { Modal, Button, Input, Textarea, Select } from '@/components/ui';
import { useT } from '@/i18n';
import {
  createWorkItem,
  formatBytes,
  uploadAndLinkWorkItemAttachment,
} from '@/api';
import type { CreateKind } from '@/types';

interface Props {
  kind: CreateKind | null;
  onClose: () => void;
}

interface PendingFile {
  key: string;
  file: File;
}

export function CreateWorkItemModal({ kind, onClose }: Props) {
  const t = useT();
  const open = kind !== null;
  const incident = kind === 'incident';
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [service, setService] = useState('');
  const [files, setFiles] = useState<PendingFile[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [uploadNote, setUploadNote] = useState('');

  useEffect(() => {
    if (!open) {
      setTitle('');
      setDescription('');
      setService('');
      setFiles([]);
      setErrors({});
      setSubmitting(false);
      setSent(false);
      setUploadNote('');
    }
  }, [open, kind]);

  if (!kind) return null;

  const validate = () => {
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = t('create.validationTitle');
    if (!description.trim()) next.description = t('create.validationDetails');
    if (!service) next.service = t('create.validationService');
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
      const created = await createWorkItem({
        kind,
        title: title.trim(),
        description: description.trim(),
        service,
      });

      // Upload + persist link to work item (mock and live).
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
            <Input
              label={t('create.shortDescription')}
              required
              autoFocus
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={
                incident
                  ? t('create.shortPlaceholderIncident')
                  : t('create.shortPlaceholderRequest')
              }
              error={errors.title}
            />
            <Textarea
              label={t('create.details')}
              required
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t('create.detailsPlaceholder')}
              error={errors.description}
            />
            <div className="form-row">
              <Select
                label={t('create.service')}
                required
                value={service}
                onChange={(e) => setService(e.target.value)}
                placeholder={t('create.selectService')}
                error={errors.service}
                options={[
                  {
                    value: 'workplace',
                    label: incident
                      ? t('create.serviceWorkplace')
                      : t('create.serviceAccess'),
                  },
                  { value: 'apps', label: t('create.serviceApps') },
                ]}
              />
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
                  disabled={submitting}
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
