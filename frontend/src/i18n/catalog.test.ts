import { describe, expect, it } from 'vitest';

import de from './locales/de.json';
import en from './locales/en.json';
import ru from './locales/ru.json';

type Catalog = { [key: string]: string | Catalog };

function flatten(catalog: Catalog, prefix = ''): string[] {
  return Object.entries(catalog).flatMap(([key, value]) =>
    typeof value === 'string'
      ? [`${prefix}${key}`]
      : flatten(value, `${prefix}${key}.`),
  );
}

const ruKeys = flatten(ru as Catalog);
const sources = import.meta.glob('/src/**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

describe('translation catalogs', () => {
  it('carry exactly the same keys in every language', () => {
    expect(flatten(en as Catalog).sort()).toEqual([...ruKeys].sort());
    expect(flatten(de as Catalog).sort()).toEqual([...ruKeys].sort());
  });

  it('resolve every key the interface asks for', () => {
    const known = new Set(ruKeys);
    const unresolved: string[] = [];

    for (const [path, source] of Object.entries(sources)) {
      if (path.endsWith('.test.ts') || path.endsWith('.test.tsx')) continue;
      // Only modules that pull in the translator; others carry their own t() helpers.
      if (!source.includes("from '@/i18n'")) continue;
      for (const match of source.matchAll(/\bt\(\s*'([^']+)'/g)) {
        const key = match[1]!;
        if (!known.has(key)) unresolved.push(`${path}: ${key}`);
      }
    }

    expect(unresolved).toEqual([]);
  });
});
