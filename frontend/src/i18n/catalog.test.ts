import { describe, expect, it } from 'vitest';

import de from './locales/de.json';
import en from './locales/en.json';
import ru from './locales/ru.json';

import deRaw from './locales/de.json?raw';
import enRaw from './locales/en.json?raw';
import ruRaw from './locales/ru.json?raw';

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

/**
 * A duplicated key parses without complaint and the last one silently wins, which is how
 * `workItem.fields` once shadowed its own object and blanked every field label.
 */
function duplicateKeys(raw: string): string[] {
  const found: string[] = [];
  const scopes: Array<Set<string>> = [];
  let index = 0;

  const readString = (): string => {
    let value = '';
    index += 1; // opening quote
    while (index < raw.length) {
      const char = raw[index]!;
      if (char === '\\') {
        value += raw.slice(index, index + 2);
        index += 2;
        continue;
      }
      if (char === '"') {
        index += 1;
        return value;
      }
      value += char;
      index += 1;
    }
    return value;
  };

  while (index < raw.length) {
    const char = raw[index]!;
    if (char === '{') {
      scopes.push(new Set<string>());
      index += 1;
      continue;
    }
    if (char === '}') {
      scopes.pop();
      index += 1;
      continue;
    }
    if (char === '"') {
      const text = readString();
      // A string followed by a colon is a key in the innermost object.
      let lookahead = index;
      while (lookahead < raw.length && /\s/.test(raw[lookahead]!)) lookahead += 1;
      if (raw[lookahead] === ':') {
        const scope = scopes[scopes.length - 1];
        if (scope) {
          if (scope.has(text)) found.push(text);
          scope.add(text);
        }
      }
      continue;
    }
    index += 1;
  }
  return found;
}

describe('translation catalogs', () => {
  it('declare every key once per object', () => {
    expect(duplicateKeys(ruRaw)).toEqual([]);
    expect(duplicateKeys(enRaw)).toEqual([]);
    expect(duplicateKeys(deRaw)).toEqual([]);
  });

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
