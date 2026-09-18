import React, { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Store, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useMerchant, useUpdateMerchant, useUpdateBranch } from '../hooks/useApiData';
import { useBranchesLookup } from '../hooks/useLookups';
import { authApi, ApiError } from '../lib/api';
import { FormField } from '../components/ui/FormField';
import { Button } from '../components/ui/Button';
import { Spinner } from '../components/ui/States';

/**
 * Shown exactly once, right after a Super App login auto-registers a new
 * merchant. The Super App handshake only ever hands over a merchant short
 * code and an MSISDN (see SuperAppMerchantClaim) - this form collects the
 * rest of the business profile that merchant-service requires, names the
 * default branch SuperAppProvisioningService already created (it starts out
 * as "Main" / "Pending onboarding"), and lets the merchant optionally set a
 * real email/password as a browser-login fallback.
 *
 * Split across two steps, which is what the two questions that actually
 * matter downstream are: step 1 is the business name (it becomes
 * `merchantName` on every ETHQR standee this merchant prints - see
 * EthQrCard), step 2 is the branch location. The remaining fields ride along
 * with whichever of the two they belong to rather than getting steps of
 * their own: city/address/category are required by merchant-service's
 * updateMerchant and cannot simply be dropped, and the fallback login is
 * optional. Nothing is submitted until step 2 - both writes plus
 * completeOnboarding go out together from handleSubmit, so abandoning the
 * wizard midway leaves the merchant exactly where it started and they get
 * the same two steps again on the next login.
 *
 * ProtectedRoute redirects every other route here while
 * user.onboardingComplete is false; submitting flips it back via
 * PATCH /api/auth/me/onboarding and sends the merchant straight to the menu
 * builder — the natural next step, not the generic role-home dashboard.
 */
export const OnboardingPage: React.FC = () => {
  const { user, refreshUser } = useAuth();
  const merchantQuery = useMerchant(user?.merchantId);
  const branchesQuery = useBranchesLookup(user?.merchantId);
  const updateMerchant = useUpdateMerchant();
  const updateBranch = useUpdateBranch();

  const [name, setName] = useState('');
  const [city, setCity] = useState('');
  const [address, setAddress] = useState('');
  const [category, setCategory] = useState('Restaurant');
  const [branchName, setBranchName] = useState('');
  const [branchLocation, setBranchLocation] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [prefilled, setPrefilled] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [step, setStep] = useState<1 | 2>(1);

  const branch = branchesQuery.data?.[0];

  // Prefill once the merchant record loads - it exists already (auto-created
  // at Super App login) with placeholder values the merchant is here to
  // replace, not blank fields to fill from scratch.
  useEffect(() => {
    if (merchantQuery.data && branch && !prefilled) {
      setName(merchantQuery.data.name.startsWith('Merchant ') ? '' : merchantQuery.data.name);
      setCity(merchantQuery.data.city === 'Pending onboarding' ? '' : merchantQuery.data.city);
      setAddress(merchantQuery.data.address === 'Pending onboarding' ? '' : merchantQuery.data.address);
      setCategory(merchantQuery.data.category === 'Pending onboarding' ? 'Restaurant' : merchantQuery.data.category);
      setBranchName(branch.name === 'Main' ? '' : branch.name);
      setBranchLocation(branch.address === 'Pending onboarding' ? '' : branch.address || '');
      setPrefilled(true);
    }
  }, [merchantQuery.data, branch, prefilled]);

  if (!user) return <Navigate to="/login" replace />;
  if (user.onboardingComplete !== false) return <Navigate to="/merchant/menu" replace />;

  if (merchantQuery.isLoading || !merchantQuery.data || branchesQuery.isLoading || !branch) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas p-4">
        <Spinner label="Loading your account…" />
      </div>
    );
  }

  // Step 1's fields are all required by merchant-service's updateMerchant, so
  // they are checked before advancing rather than at submit time - a failure
  // discovered on step 2 would point at inputs no longer on screen.
  const handleNext = () => {
    if (!name.trim() || !city.trim() || !address.trim() || !category.trim()) {
      setError('Fill in your business name, city, address and category to continue.');
      return;
    }
    setError(null);
    setStep(2);
  };

  const handleBack = () => {
    setError(null);
    setStep(1);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // The form's submit also fires on Enter in a step-1 field, where it must
    // advance rather than save a half-filled profile.
    if (step === 1) {
      handleNext();
      return;
    }
    if (!branchName.trim() || !branchLocation.trim()) {
      setError('Give your first branch a name and a location to finish.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await updateMerchant.mutateAsync({
        id: user.merchantId!,
        data: {
          name,
          // Permanent and already assigned at auto-registration - echoed back
          // unchanged, or merchant-service's updateMerchant rejects the call.
          slug: merchantQuery.data!.slug,
          phone: merchantQuery.data!.phone,
          city,
          address,
          category,
        },
      });

      await updateBranch.mutateAsync({
        id: branch.id,
        merchantId: branch.merchantId,
        data: {
          name: branchName,
          // Unchanged - the branch's phone isn't collected here, same
          // reasoning as the merchant's slug/phone above.
          phone: branch.phone,
          address: branchLocation,
        },
      });

      if (email.trim() || password.trim()) {
        await authApi.updateOwnCredentials({
          email: email.trim() || undefined,
          password: password.trim() || undefined,
        });
      }

      await authApi.completeOnboarding();
      await refreshUser();
      // refreshUser() updates user.onboardingComplete; the guard above then
      // sends the next render to /merchant/menu.
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your details. Please try again.');
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas p-4">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-card bg-brand text-ink shadow-[var(--shadow-lift-brand)]">
            <Store className="h-7 w-7" aria-hidden="true" />
          </div>
          <h1 className="font-display text-title-l text-ink">Set up your business</h1>
          <p className="mt-1 text-body-m text-muted">
            Two quick steps before your menu goes live — this only takes a minute.
          </p>
          {/* Step indicator. aria-label carries the position for a screen
              reader, since the filled/empty bars convey it only visually. */}
          <div
            className="mt-4 flex items-center justify-center gap-2"
            role="group"
            aria-label={`Step ${step} of 2`}
          >
            {([1, 2] as const).map((n) => (
              <span
                key={n}
                aria-hidden="true"
                className={`h-1.5 w-12 rounded-pill ${n <= step ? 'bg-brand-dark' : 'bg-line'}`}
              />
            ))}
          </div>
          <p className="mt-2 text-label-s uppercase text-muted">
            Step {step} of 2 · {step === 1 ? 'Your business' : 'Your first branch'}
          </p>
        </div>

        <div className="overflow-hidden rounded-card border border-line bg-surface shadow-[var(--shadow-lift)]">
          <form onSubmit={handleSubmit} className="space-y-4 p-6">
            {error && (
              <div role="alert" className="flex items-center gap-2 rounded-control bg-danger-soft px-3 py-3 text-label-s text-ink">
                <AlertCircle className="h-4 w-4 shrink-0 text-danger" aria-hidden="true" />
                {error}
              </div>
            )}

            {/* ---- Step 1: the business ----
                The name here is what prints as `merchantName` on every ETHQR
                standee, so it is the first thing asked for. */}
            {step === 1 && (
              <>
                <FormField
                  label="Business / cafe name"
                  required
                  maxLength={100}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Sunrise Cafe"
                  hint="Printed on your payment standees — use the name guests know you by."
                />
                <FormField
                  label="City"
                  required
                  maxLength={60}
                  value={city}
                  onChange={(e) => setCity(e.target.value)}
                  placeholder="e.g. Addis Ababa"
                />
                <FormField
                  label="Address"
                  required
                  maxLength={120}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="e.g. Bole Road"
                />
                <FormField
                  label="Category"
                  required
                  maxLength={40}
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  placeholder="e.g. Restaurant, Cafe, Bar"
                />

                <Button type="submit" fullWidth size="lg">
                  Continue
                </Button>
              </>
            )}

            {/* ---- Step 2: the first branch ----
                This branch already exists (SuperAppProvisioningService created
                it as "Main" / "Pending onboarding"); these fields rename it
                rather than adding a second one, which a Free-tier merchant
                could not do anyway (see BranchService's 1-branch cap). */}
            {step === 2 && (
              <>
                <FormField
                  label="Branch name"
                  required
                  maxLength={100}
                  value={branchName}
                  onChange={(e) => setBranchName(e.target.value)}
                  placeholder="e.g. Bole Branch"
                />
                <FormField
                  label="Branch location"
                  required
                  maxLength={120}
                  value={branchLocation}
                  onChange={(e) => setBranchLocation(e.target.value)}
                  placeholder="e.g. Bole Road, near Friendship Building"
                  hint="Where a guest would find this branch — shown on printed table standees."
                />

                <div className="border-t border-line pt-4">
                  <p className="mb-1 text-label-s uppercase text-muted">Fallback login (optional)</p>
                  <p className="mb-3 text-label-s text-muted">
                    You’ll always be able to sign in from the Mini App. Set an email and password here only if you also
                    want to sign in from a web browser.
                  </p>
                  <div className="space-y-3">
                    <FormField
                      label="Email"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@restaurant.com"
                    />
                    <FormField
                      label="Password"
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="At least 8 characters"
                    />
                  </div>
                </div>

                <div className="flex gap-3">
                  <Button type="button" variant="secondary" size="lg" onClick={handleBack} disabled={submitting}>
                    Back
                  </Button>
                  <Button type="submit" loading={submitting} fullWidth size="lg">
                    {submitting ? 'Saving…' : 'Finish setup'}
                  </Button>
                </div>
              </>
            )}
          </form>
        </div>
      </div>
    </div>
  );
};
