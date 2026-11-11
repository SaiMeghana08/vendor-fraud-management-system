import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { notificationApi } from '../api/endpoints';
import type { Notification } from '../types';
import { formatDateTime } from '../utils/format';

/**
 * The notification bell. Every call is scoped to the authenticated
 * recipient by the backend, so this component cannot be pointed at
 * anyone else's notifications.
 */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const loadSummary = () => {
    notificationApi.summary()
      .then((summary) => setUnread(summary.unreadCount))
      .catch(() => undefined);
  };

  useEffect(() => {
    loadSummary();
    const timer = setInterval(loadSummary, 30_000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    notificationApi.list(0, 15)
      .then((page) => setItems(page.content))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onClickAway = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClickAway);
    return () => document.removeEventListener('mousedown', onClickAway);
  }, [open]);

  const onSelect = async (notification: Notification) => {
    if (!notification.read) {
      try {
        await notificationApi.markRead(notification.id);
        setItems((current) => current.map((item) =>
          item.id === notification.id ? { ...item, read: true } : item));
        setUnread((count) => Math.max(0, count - 1));
      } catch {
        // A failed mark-read must not block navigation.
      }
    }
    if (notification.referenceType === 'BANK_CHANGE_REQUEST' && notification.referenceId) {
      setOpen(false);
      navigate(`/requests/${notification.referenceId}`);
    }
  };

  return (
    <div className="relative" ref={containerRef}>
      <button type="button" onClick={() => setOpen((value) => !value)}
              aria-label={`Notifications${unread > 0 ? `, ${unread} unread` : ''}`}
              className="relative rounded-md p-2 text-slate-500 hover:bg-slate-100">
        <Bell className="h-5 w-5" aria-hidden />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center
                           justify-center rounded-full bg-rose-600 px-1 text-[10px]
                           font-semibold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 overflow-hidden rounded-lg
                        border border-slate-200 bg-white shadow-lg">
          <p className="border-b border-slate-100 px-4 py-2 text-xs font-semibold
                        uppercase tracking-wide text-slate-500">
            Notifications
          </p>
          <ul className="max-h-80 divide-y divide-slate-100 overflow-y-auto">
            {loading && <li className="px-4 py-3 text-sm text-slate-500">Loading…</li>}
            {!loading && items.length === 0 && (
              <li className="px-4 py-6 text-sm text-slate-500">You have no notifications.</li>
            )}
            {items.map((notification) => (
              <li key={notification.id}>
                <button type="button" onClick={() => onSelect(notification)}
                        className={`w-full px-4 py-3 text-left hover:bg-slate-50
                                    ${notification.read ? '' : 'bg-brand-50/40'}`}>
                  <span className="block text-sm font-medium text-slate-800">
                    {notification.title}
                  </span>
                  <span className="mt-0.5 block text-xs text-slate-600">
                    {notification.message}
                  </span>
                  <span className="mt-1 block text-[11px] text-slate-400">
                    {formatDateTime(notification.createdAt)}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
