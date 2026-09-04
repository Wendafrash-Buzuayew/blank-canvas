import React from 'react';
import { Star, MessageSquare } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { useReviews } from '../hooks/useApiData';

export const ReviewsPage: React.FC = () => {
  const { data: reviews, isLoading, error, refetch } = useReviews();

  const count = reviews?.length ?? 0;
  const average = count > 0 ? reviews!.reduce((sum, r) => sum + r.rating, 0) / count : 0;

  return (
    <DashboardLayout title="Customer Reviews">
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div>
            <h2 className="text-xl font-black text-slate-900 flex items-center gap-2">
              <Star className="w-6 h-6 text-[#E60028]" /> Customer Reviews
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">What customers say about their visit — left directly from your digital menu.</p>
          </div>
          {count > 0 && (
            <div className="text-right">
              <div className="text-2xl font-black text-slate-900">{average.toFixed(1)} <span className="text-sm text-slate-400 font-bold">/ 5</span></div>
              <div className="text-xs text-slate-500">{count} review{count === 1 ? '' : 's'}</div>
            </div>
          )}
        </div>

        {isLoading && <Spinner label="Loading reviews..." />}
        {!isLoading && error && <ErrorState message="Could not load reviews." onRetry={() => refetch()} />}

        {!isLoading && !error && count === 0 && (
          <EmptyState
            title="No reviews yet"
            description="Once customers start rating their visits from your digital menu, they'll show up here."
          />
        )}

        {!isLoading && !error && count > 0 && (
          <div className="space-y-3">
            {reviews!.map((review) => (
              <div key={review.id} className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1">
                    {[1, 2, 3, 4, 5].map((n) => (
                      <Star key={n} className={`w-4 h-4 ${n <= review.rating ? 'text-[#E60028] fill-[#E60028]' : 'text-slate-200'}`} />
                    ))}
                  </div>
                  <span className="text-[10px] text-slate-400">{new Date(review.createdAt).toLocaleDateString()}</span>
                </div>
                {review.customerName && <p className="text-xs font-bold text-slate-700 mt-2">{review.customerName}</p>}
                {review.comment && (
                  <p className="text-sm text-slate-600 mt-1 flex items-start gap-1.5">
                    <MessageSquare className="w-3.5 h-3.5 text-slate-300 shrink-0 mt-0.5" />
                    {review.comment}
                  </p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};
