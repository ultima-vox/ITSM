import { delay, isMockMode, apiRequest } from './client';

export interface MetadataAttribute {
  key: string;
  type: string;
  required: boolean;
  searchable: boolean;
  labels: Record<string, string>;
  enumValues?: string[];
}

export interface MetadataRelation {
  key: string;
  targetObjectKey: string;
  cardinality: string;
  required: boolean;
  labels: Record<string, string>;
}

export interface ObjectDefinition {
  key: string;
  version: number;
  labels: Record<string, string>;
  attributes: MetadataAttribute[];
  relations: MetadataRelation[];
}

export interface ObjectDefinitionVersionView {
  definition: ObjectDefinition;
  active: boolean;
}

/** Sample UI catalog keys for Translation admin (work-item namespace). */
export interface SampleTranslationRow {
  namespace: string;
  key: string;
  en: string;
  ru: string;
  de: string;
}

const MOCK_OBJECTS: ObjectDefinition[] = [
  {
    key: 'work-item',
    version: 1,
    labels: { en: 'Work Item', ru: 'Рабочий элемент', de: 'Arbeitselement' },
    attributes: [
      { key: 'number', type: 'TEXT', required: false, searchable: true, labels: { en: 'Number', ru: 'Номер', de: 'Nummer' } },
      {
        key: 'type',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'Type', ru: 'Тип', de: 'Typ' },
        enumValues: ['INCIDENT', 'SERVICE_REQUEST'],
      },
      { key: 'title', type: 'TEXT', required: true, searchable: true, labels: { en: 'Title', ru: 'Заголовок', de: 'Titel' } },
      { key: 'description', type: 'RICH_TEXT', required: true, searchable: true, labels: { en: 'Description', ru: 'Описание', de: 'Beschreibung' } },
      { key: 'service', type: 'TEXT', required: true, searchable: true, labels: { en: 'Service', ru: 'Услуга', de: 'Service' } },
      {
        key: 'state',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'State', ru: 'Состояние', de: 'Status' },
        enumValues: ['NEW', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED', 'CANCELLED'],
      },
      {
        key: 'priority',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'Priority', ru: 'Приоритет', de: 'Priorität' },
        enumValues: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'],
      },
      { key: 'requester_id', type: 'USER', required: true, searchable: false, labels: { en: 'Requester', ru: 'Заявитель', de: 'Anforderer' } },
      { key: 'assignee_id', type: 'USER', required: false, searchable: true, labels: { en: 'Assignee', ru: 'Исполнитель', de: 'Bearbeiter' } },
      { key: 'created_at', type: 'DATE_TIME', required: false, searchable: false, labels: { en: 'Created', ru: 'Создано', de: 'Erstellt' } },
      { key: 'updated_at', type: 'DATE_TIME', required: false, searchable: false, labels: { en: 'Updated', ru: 'Обновлено', de: 'Aktualisiert' } },
    ],
    relations: [
      {
        key: 'related_ci',
        targetObjectKey: 'configuration-item',
        cardinality: 'MANY_TO_MANY',
        required: false,
        labels: { en: 'Related CIs', ru: 'Связанные КЕ', de: 'Zugehörige CIs' },
      },
    ],
  },
  {
    key: 'change',
    version: 1,
    labels: { en: 'Change', ru: 'Изменение', de: 'Change' },
    attributes: [
      { key: 'number', type: 'TEXT', required: false, searchable: true, labels: { en: 'Number', ru: 'Номер', de: 'Nummer' } },
      { key: 'title', type: 'TEXT', required: true, searchable: true, labels: { en: 'Title', ru: 'Заголовок', de: 'Titel' } },
      {
        key: 'type',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'Type', ru: 'Тип', de: 'Typ' },
        enumValues: ['STANDARD', 'NORMAL', 'EMERGENCY'],
      },
      {
        key: 'status',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'Status', ru: 'Статус', de: 'Status' },
        enumValues: ['DRAFT', 'SCHEDULED', 'CAB_REVIEW', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'],
      },
      {
        key: 'risk',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'Risk', ru: 'Риск', de: 'Risiko' },
        enumValues: ['LOW', 'MEDIUM', 'HIGH'],
      },
      { key: 'window_start', type: 'DATE_TIME', required: false, searchable: false, labels: { en: 'Window start', ru: 'Начало окна', de: 'Fensterbeginn' } },
      { key: 'window_end', type: 'DATE_TIME', required: false, searchable: false, labels: { en: 'Window end', ru: 'Конец окна', de: 'Fensterende' } },
      { key: 'assignee_id', type: 'USER', required: false, searchable: true, labels: { en: 'Assignee', ru: 'Исполнитель', de: 'Bearbeiter' } },
      { key: 'implementation_plan', type: 'RICH_TEXT', required: false, searchable: false, labels: { en: 'Implementation plan', ru: 'План внедрения', de: 'Umsetzungsplan' } },
      { key: 'backout_plan', type: 'RICH_TEXT', required: false, searchable: false, labels: { en: 'Backout plan', ru: 'План отката', de: 'Rückfallplan' } },
    ],
    relations: [
      {
        key: 'affected_ci',
        targetObjectKey: 'configuration-item',
        cardinality: 'MANY_TO_MANY',
        required: false,
        labels: { en: 'Affected CIs', ru: 'Затронутые КЕ', de: 'Betroffene CIs' },
      },
    ],
  },
  {
    key: 'problem',
    version: 1,
    labels: { en: 'Problem', ru: 'Проблема', de: 'Problem' },
    attributes: [
      { key: 'number', type: 'TEXT', required: false, searchable: true, labels: { en: 'Number', ru: 'Номер', de: 'Nummer' } },
      { key: 'title', type: 'TEXT', required: true, searchable: true, labels: { en: 'Title', ru: 'Заголовок', de: 'Titel' } },
      {
        key: 'status',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'Status', ru: 'Статус', de: 'Status' },
        enumValues: ['NEW', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED'],
      },
      {
        key: 'priority',
        type: 'ENUM',
        required: true,
        searchable: true,
        labels: { en: 'Priority', ru: 'Приоритет', de: 'Priorität' },
        enumValues: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'],
      },
      { key: 'known_error', type: 'BOOLEAN', required: false, searchable: true, labels: { en: 'Known error', ru: 'Известная ошибка', de: 'Known Error' } },
      { key: 'root_cause', type: 'RICH_TEXT', required: false, searchable: true, labels: { en: 'Root cause', ru: 'Корневая причина', de: 'Ursache' } },
      { key: 'workaround', type: 'RICH_TEXT', required: false, searchable: true, labels: { en: 'Workaround', ru: 'Обходной путь', de: 'Workaround' } },
      { key: 'assignee_id', type: 'USER', required: false, searchable: true, labels: { en: 'Assignee', ru: 'Исполнитель', de: 'Bearbeiter' } },
    ],
    relations: [
      {
        key: 'related_incidents',
        targetObjectKey: 'work-item',
        cardinality: 'MANY_TO_MANY',
        required: false,
        labels: { en: 'Related incidents', ru: 'Связанные инциденты', de: 'Zugehörige Incidents' },
      },
    ],
  },
];

/** Static sample rows for Settings → Translation admin (demo). */
export const SAMPLE_WORK_ITEM_TRANSLATIONS: SampleTranslationRow[] = [
  { namespace: 'object.work-item', key: 'label', en: 'Work Item', ru: 'Рабочий элемент', de: 'Arbeitselement' },
  { namespace: 'object.work-item', key: 'attr.title', en: 'Title', ru: 'Заголовок', de: 'Titel' },
  { namespace: 'object.work-item', key: 'attr.description', en: 'Description', ru: 'Описание', de: 'Beschreibung' },
  { namespace: 'object.work-item', key: 'attr.service', en: 'Service', ru: 'Услуга', de: 'Service' },
  { namespace: 'object.work-item', key: 'attr.priority', en: 'Priority', ru: 'Приоритет', de: 'Priorität' },
  { namespace: 'object.work-item', key: 'attr.state', en: 'State', ru: 'Состояние', de: 'Status' },
  { namespace: 'object.work-item', key: 'attr.type', en: 'Type', ru: 'Тип', de: 'Typ' },
  { namespace: 'object.work-item', key: 'attr.assignee_id', en: 'Assignee', ru: 'Исполнитель', de: 'Bearbeiter' },
  { namespace: 'object.work-item', key: 'attr.requester_id', en: 'Requester', ru: 'Заявитель', de: 'Anforderer' },
  { namespace: 'workflow.work-item', key: 'state.NEW', en: 'New', ru: 'Новый', de: 'Neu' },
  { namespace: 'workflow.work-item', key: 'state.IN_PROGRESS', en: 'In Progress', ru: 'В работе', de: 'In Bearbeitung' },
  { namespace: 'workflow.work-item', key: 'state.RESOLVED', en: 'Resolved', ru: 'Решён', de: 'Gelöst' },
];

/** GET /api/v1/metadata/objects — mock object definitions when VITE_USE_MOCK. */
export async function fetchObjectDefinitions(): Promise<ObjectDefinition[]> {
  if (isMockMode()) {
    await delay(220);
    return structuredClone(MOCK_OBJECTS);
  }
  return apiRequest<ObjectDefinition[]>('/metadata/objects');
}

export async function fetchObjectDefinitionVersions(
  key: string,
): Promise<ObjectDefinitionVersionView[]> {
  if (isMockMode()) return [];
  return apiRequest<ObjectDefinitionVersionView[]>(
    `/metadata/objects/${encodeURIComponent(key)}/versions`,
  );
}

export async function createObjectDefinitionDraft(
  definition: Omit<ObjectDefinition, 'version'>,
): Promise<ObjectDefinitionVersionView> {
  return apiRequest<ObjectDefinitionVersionView>('/metadata/objects/drafts', {
    method: 'POST',
    body: definition,
  });
}

export async function publishObjectDefinitionVersion(
  key: string,
  version: number,
): Promise<ObjectDefinitionVersionView> {
  return apiRequest<ObjectDefinitionVersionView>(
    `/metadata/objects/${encodeURIComponent(key)}/versions/${version}/publish`,
    { method: 'POST' },
  );
}

/* ── Form definition (form engine metadata) ───────────────── */

/** CEL expression for conditional visibility / read-only (server authority). */
export interface FormExpression {
  language: 'cel';
  source: string;
}

export interface FormField {
  attributeKey: string;
  required: boolean;
  visibleWhen?: FormExpression | null;
  readOnlyWhen?: FormExpression | null;
}

export interface FormSection {
  key: string;
  labels: Record<string, string>;
  fields: FormField[];
}

/** Matches backend FormDefinition shape loosely (id/key/objectKey/version/sections). */
export interface FormDefinition {
  id: string;
  key: string;
  objectKey: string;
  version: number;
  sections: FormSection[];
}

export interface FormDefinitionVersionView {
  definition: FormDefinition;
  active: boolean;
}

export async function fetchFormDefinitionVersions(formKey: string): Promise<FormDefinitionVersionView[]> {
  if (isMockMode()) return [];
  return apiRequest(`/metadata/forms/definitions/${encodeURIComponent(formKey)}/versions`);
}

export async function createFormDefinitionDraft(
  definition: Pick<FormDefinition, 'key' | 'objectKey' | 'sections'>,
): Promise<FormDefinitionVersionView> {
  return apiRequest('/metadata/forms/drafts', { method: 'POST', body: definition });
}

export async function publishFormDefinitionVersion(
  formKey: string, version: number,
): Promise<FormDefinitionVersionView> {
  return apiRequest(`/metadata/forms/definitions/${encodeURIComponent(formKey)}/versions/${version}/publish`,
    { method: 'POST' });
}

/**
 * Mock work-item form: title, description, service, impact, urgency.
 * Values map to existing WorkItem store fields.
 */
const MOCK_WORK_ITEM_FORM: FormDefinition = {
  id: 'form-work-item-default',
  key: 'work-item.default',
  objectKey: 'work-item',
  version: 1,
  sections: [
    {
      key: 'main',
      labels: { en: 'Details', ru: 'Основное', de: 'Details' },
      fields: [
        { attributeKey: 'title', required: true, visibleWhen: null, readOnlyWhen: null },
        {
          attributeKey: 'description',
          required: true,
          visibleWhen: null,
          readOnlyWhen: null,
        },
        { attributeKey: 'service', required: true, visibleWhen: null, readOnlyWhen: null },
      ],
    },
    {
      key: 'classification',
      labels: {
        en: 'Classification',
        ru: 'Классификация',
        de: 'Klassifizierung',
      },
      fields: [
        { attributeKey: 'impact', required: true, visibleWhen: null, readOnlyWhen: null },
        { attributeKey: 'urgency', required: true, visibleWhen: null, readOnlyWhen: null },
      ],
    },
  ],
};

const MOCK_FORMS: FormDefinition[] = [MOCK_WORK_ITEM_FORM];

/**
 * Attribute presentation hints used by the light form engine
 * (labels + control type) when object metadata is not joined in.
 */
export const FORM_FIELD_META: Record<
  string,
  {
    type: 'TEXT' | 'RICH_TEXT' | 'ENUM' | 'SELECT';
    labels: Record<string, string>;
    enumValues?: string[];
  }
> = {
  title: {
    type: 'TEXT',
    labels: { en: 'Title', ru: 'Заголовок', de: 'Titel' },
  },
  description: {
    type: 'RICH_TEXT',
    labels: { en: 'Description', ru: 'Описание', de: 'Beschreibung' },
  },
  service: {
    type: 'SELECT',
    labels: { en: 'Service', ru: 'Услуга', de: 'Service' },
    enumValues: ['workplace', 'access', 'apps'],
  },
  impact: {
    type: 'ENUM',
    labels: { en: 'Impact', ru: 'Влияние', de: 'Auswirkung' },
    enumValues: ['high', 'medium', 'low'],
  },
  urgency: {
    type: 'ENUM',
    labels: { en: 'Urgency', ru: 'Срочность', de: 'Dringlichkeit' },
    enumValues: ['high', 'medium', 'low'],
  },
  priority: {
    type: 'ENUM',
    labels: { en: 'Priority', ru: 'Приоритет', de: 'Priorität' },
    enumValues: ['critical', 'high', 'medium', 'low'],
  },
};

/** GET active form definition for an object key (mock when VITE_USE_MOCK). */
export async function fetchFormDefinition(
  objectKey: string,
): Promise<FormDefinition | null> {
  if (isMockMode()) {
    await delay(120);
    const found = MOCK_FORMS.find((f) => f.objectKey === objectKey) ?? null;
    return found ? structuredClone(found) : null;
  }
  try {
    return await apiRequest<FormDefinition>(
      `/metadata/forms/by-object/${encodeURIComponent(objectKey)}`,
    );
  } catch {
    return null;
  }
}
