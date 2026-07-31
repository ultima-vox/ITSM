import type { ReactNode } from 'react';

export type TabTone = 'default' | 'warn' | 'danger';

export interface TabItem {
  id: string;
  label: string;
  count?: number;
  tone?: TabTone;
}

interface TabsProps {
  items: TabItem[];
  value: string;
  onChange: (id: string) => void;
  trailing?: ReactNode;
  className?: string;
}

export function Tabs({ items, value, onChange, trailing, className = '' }: TabsProps) {
  return (
    <div className={`tabs${className ? ` ${className}` : ''}`} role="tablist">
      {items.map((item) => {
        const active = value === item.id;
        const tone = item.tone && item.tone !== 'default' ? item.tone : undefined;
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={active}
            className={[
              'tabs__tab',
              active ? 'is-active' : '',
              tone ? `tabs__tab--${tone}` : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => onChange(item.id)}
          >
            {item.label}
            {item.count !== undefined && (
              <b className={tone ? `tabs__count tabs__count--${tone}` : 'tabs__count'}>
                {item.count}
              </b>
            )}
          </button>
        );
      })}
      {trailing && <div className="tabs__trailing">{trailing}</div>}
    </div>
  );
}
