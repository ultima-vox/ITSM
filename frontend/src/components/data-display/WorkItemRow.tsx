import { Link } from 'react-router-dom';
import type { WorkItem } from '@/types';
import { useT } from '@/i18n';
import { Avatar } from '@/components/ui';
import { PriorityBadge } from './PriorityBadge';
import { SlaMiniBar } from './SlaMiniBar';
import { formatRelative } from '@/lib/format';

interface WorkItemRowProps {
  item: WorkItem;
  dense?: boolean;
}

export function WorkItemRow({ item, dense }: WorkItemRowProps) {
  const t = useT();

  return (
    <Link
      to={`/work-items/${item.id}`}
      className={`wi-row${dense ? ' wi-row--dense' : ''}${
        item.slaState === 'breached' ? ' wi-row--breach' : ''
      }${item.slaState === 'at_risk' ? ' wi-row--risk' : ''}`}
      role="row"
    >
      <div className="wi-row__ticket" role="cell">
        <b>{item.number}</b>
        <span>{item.title}</span>
        <small>{t(`workItemType.${item.type}`)}</small>
      </div>
      <div role="cell">
        <PriorityBadge priority={item.priority} />
      </div>
      <div className="wi-row__person" role="cell">
        {item.assignee ? (
          <>
            <Avatar initials={item.assignee.initials} size="sm" />
            <span>{item.assignee.name}</span>
          </>
        ) : (
          <span className="muted">{t('overview.unassigned')}</span>
        )}
      </div>
      <div role="cell">
        <SlaMiniBar state={item.slaState} target={item.slaTarget} compact={dense} />
      </div>
      <span className="muted" role="cell">
        {formatRelative(item.updatedAt, t)}
      </span>
      {/* Mobile card meta — visible to AT when desktop cells are display:none */}
      <div className="wi-row__meta-line">
        <PriorityBadge priority={item.priority} />
        <SlaMiniBar state={item.slaState} target={item.slaTarget} compact />
        <span className="muted">{formatRelative(item.updatedAt, t)}</span>
      </div>
    </Link>
  );
}
