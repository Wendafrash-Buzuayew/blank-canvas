import React from 'react';
import { Loader2, AlertTriangle, Inbox, RefreshCw } from 'lucide-react';

// ============ Loading States ============

export const Spinner: React.FC<{ label?: string; className?: string }> = ({ label, className = '' }) => (
  <div className={`flex flex-col items-center justify-center py-12 text-slate-500 ${className}`}>
    <Loader2 className="w-8 h-8 animate-spin text-[#E60028]" />
    {label && <p className="text-xs font-medium mt-3 text-slate-500">{label}</p>}
  </div>
);

export const Skeleton: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`animate-pulse bg-slate-200/70 rounded-lg ${className}`} />
);

export const SkeletonCards: React.FC<{ count?: number; cols?: number }> = ({ count = 4, cols = 4 }) => (
  <div className={`grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-${cols} gap-6`}>
    {Array.from({ length: count }).map((_, i) => (
      <div key={i} className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-3">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-7 w-32" />
        <Skeleton className="h-3 w-20" />
      </div>
    ))}
  </div>
);

export const TableLoading: React.FC<{ rows?: number }> = ({ rows = 4 }) => (
  <div className="space-y-3">
    {Array.from({ length: rows }).map((_, i) => (
      <div key={i} className="flex items-center gap-4 p-3 bg-white rounded-xl border border-slate-200">
        <Skeleton className="h-10 w-10 rounded-lg" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-3 w-1/3" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      </div>
    ))}
  </div>
);

// ============ Error States ============

export const ErrorState: React.FC<{
  message?: string;
  onRetry?: () => void;
  status?: number;
}> = ({ message = 'An unexpected error occurred. Please try again.', onRetry, status }) => (
  <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
    <div className="w-12 h-12 rounded-full bg-red-50 border border-red-100 flex items-center justify-center mb-3">
      <AlertTriangle className="w-6 h-6 text-red-500" />
    </div>
    {status && (
      <span className="text-[10px] font-bold uppercase tracking-wider text-red-400 mb-1">
        Error {status}
      </span>
    )}
    <p className="text-xs text-slate-600 font-medium max-w-sm">{message}</p>
    {onRetry && (
      <button
        onClick={onRetry}
        className="mt-4 px-4 py-2 bg-slate-900 text-white text-xs font-bold rounded-xl flex items-center gap-2 hover:bg-black transition-colors"
      >
        <RefreshCw className="w-3.5 h-3.5" />
        Try Again
      </button>
    )}
  </div>
);

// ============ Empty States ============

export const EmptyState: React.FC<{
  title: string;
  description?: string;
  action?: React.ReactNode;
}> = ({ title, description, action }) => (
  <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
    <div className="w-12 h-12 rounded-full bg-slate-50 border border-slate-200 flex items-center justify-center mb-3">
      <Inbox className="w-6 h-6 text-slate-400" />
    </div>
    <p className="text-sm font-bold text-slate-700">{title}</p>
    {description && <p className="text-xs text-slate-500 mt-1 max-w-sm">{description}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>
);

// ============ Button Loading ============

export const ButtonSpinner: React.FC = () => (
  <Loader2 className="w-4 h-4 animate-spin" />
);