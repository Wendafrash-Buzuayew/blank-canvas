import React from 'react';
import { Star, MessageSquare } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { Spinner, ErrorState, EmptyState } from '../components/ui/States';
import { Card } from '../components/ui/Card';
import { useReviews } from '../hooks/useApiData';

export const ReviewsPage: React.FC = () => {
  const { data: reviews, isLoading, error, refetch } = useReviews();

  const count = reviews?.length ?? 0;
  const average = count > 0 ? reviews!.reduce((sum, r) => sum + r.rating, 0) / count : 0;

  return (
    <DashboardLayout title="Customer Reviews">
      <div className="space-y-6">
        <Card compact className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <h2 className="flex items-center gap-2 text-title-m text-ink">
              <Star className="h-6 w-6 text-brand-press" aria-hidden="true" /> Customer Reviews
            </h2>
            <p className="mt-0.5 text-body-m text-muted">
              What customers say about their visit - left directly from your digital menu.
            </p>
          </div>
          {count > 0 && (
            <div className="text-right">
              <div className="text-title-l [font-variant-numeric:tabular-nums]">
                {average.toFixed(1)} <span className="text-body-m text-muted">/ 5</span>
              </div>
              <div className="text-label-s text-muted">{count} review{count === 1 ? '' : 's'}</div>
            </div>
          )}
        </Card>

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
              <Card key={review.id} compact>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1" aria-label={`${review.rating} out of 5 stars`}>
                    {[1, 2, 3, 4, 5].map((n) => (
                      <Star
                        key={n}
                        className={`h-4 w-4 ${n <= review.rating ? 'fill-brand-press text-brand-press' : 'text-line-strong'}`}
                        aria-hidden="true"
                      />
                    ))}
                  </div>
                  <span className="text-label-s text-muted">{new Date(review.createdAt).toLocaleDateString()}</span>
                </div>
                {review.customerName && <p className="mt-2 text-label-m text-ink">{review.customerName}</p>}
                {review.comment && (
                  <p className="mt-1 flex items-start gap-1.5 text-body-m text-muted">
                    <MessageSquare className="mt-0.5 h-3.5 w-3.5 shrink-0 text-line-strong" aria-hidden="true" />
                    {review.comment}
                  </p>
                )}
              </Card>
            ))}
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};
