import React from 'react';
import {
  QrCode,
  Smartphone,
  ChefHat,
  Table as TableIcon,
  Printer,
  BarChart3,
  Building2,
  Sparkles,
} from 'lucide-react';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { IdentityChip } from '../ui/Chip';

interface LandingPageProps {
  onStartCustomerDemo: () => void;
  onStartMerchantDemo: () => void;
  onBookDemo: () => void;
}

const FEATURES = [
  { icon: QrCode, iconClass: 'bg-brand-soft text-brand-press', title: 'Smart QR Menus', body: 'Customers point their phone camera to instantly view interactive digital menus with appetizing photos, filter tags, and dietary options without downloading an app.' },
  { icon: ChefHat, iconClass: 'bg-warn-soft text-warn', title: 'Real-Time Kitchen Orders', body: 'Orders land instantly on the kitchen display board (KDS) with table identification, exact quantities, special customer notes, and prep timer alerts.' },
  { icon: Printer, iconClass: 'bg-success-soft text-success', title: 'Printable QR Stand Designer', body: 'Customize brand colors, logo overlays, and call-to-actions. Export vector SVGs, PNGs, or print-ready 4x6" acrylic table stand templates in seconds.' },
  { icon: TableIcon, iconClass: 'bg-info-soft text-info', title: 'Table & Floor Management', body: 'Organize multiple branches, floors, and VIP sections. Assign unique QR links per table so the kitchen knows exactly where to deliver meals.' },
  { icon: BarChart3, iconClass: 'bg-info-soft text-info', title: 'Analytics Dashboard', body: 'Track daily sales volume, peak ordering hours, bestselling dishes, table turnover speed, and customer order notes.' },
  { icon: Building2, iconClass: 'bg-danger-soft text-danger', title: 'Multi-Branch & Multi-Tenant', body: 'Expand effortlessly from single coffee shops to hotel chains with role-based staff permissions for Cashiers, Waiters, and Managers.' },
];

export const LandingPage: React.FC<LandingPageProps> = ({
  onStartCustomerDemo,
  onStartMerchantDemo,
  onBookDemo,
}) => {
  return (
    <div className="space-y-20 pb-16">
      {/* Hero - DESIGN.md 4.2: display-xl is landing-hero-only. Dark ground
          uses only the measured-safe on-ink / on-ink-muted pair; the four
          stat callouts differentiate by icon and position, not by four
          unverified hues on a dark background. */}
      <section className="relative overflow-hidden rounded-card bg-ink-gradient px-4 py-16 text-on-ink shadow-[var(--shadow-lift)] sm:px-6 sm:py-20 lg:px-8">
        <div className="relative mx-auto max-w-4xl space-y-6 text-center">
          <div className="inline-flex items-center gap-2 rounded-pill border border-white/20 bg-white/10 px-3.5 py-1.5 text-label-s text-on-ink backdrop-blur-md">
            <Sparkles className="h-4 w-4 text-brand" aria-hidden="true" />
            <span>Next-Gen Smart QR Menu &amp; Order Management Platform</span>
          </div>

          <h1 className="font-display text-display-l tracking-tight sm:text-display-xl">
            Transform Your Restaurant with <span className="text-brand-gradient">Smart QR Menus</span>
          </h1>

          <p className="mx-auto max-w-2xl text-body-l text-on-ink-muted">
            Create menus, generate table QR codes, receive real-time kitchen orders, and print acrylic stands - all from one unified platform.
          </p>

          <div className="flex flex-col items-center justify-center gap-4 pt-4 sm:flex-row">
            <Button onClick={onStartCustomerDemo} size="lg" fullWidth className="sm:w-auto">
              <Smartphone className="h-5 w-5" aria-hidden="true" />
              Try Customer QR Menu
            </Button>
            <Button onClick={onStartMerchantDemo} variant="secondary" size="lg" fullWidth className="sm:w-auto !bg-surface">
              <ChefHat className="h-5 w-5 text-brand-press" aria-hidden="true" />
              Merchant Portal Demo
            </Button>
          </div>

          <div className="mx-auto grid max-w-3xl grid-cols-2 gap-4 pt-10 text-left sm:grid-cols-4">
            {[
              { value: '0s', label: 'App Download Needed' },
              { value: '3x', label: 'Faster Table Turnover' },
              { value: '+25%', label: 'Avg Order Value Increase' },
              { value: '100%', label: 'Real-Time Kitchen Sync' },
            ].map((stat) => (
              <div key={stat.label} className="rounded-card border border-white/10 bg-white/5 p-4 backdrop-blur-md">
                <div className="text-title-l text-on-ink [font-variant-numeric:tabular-nums]">{stat.value}</div>
                <div className="text-label-s text-on-ink-muted">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Value proposition grid */}
      <section className="mx-auto max-w-6xl space-y-8 px-4">
        <div className="space-y-2 text-center">
          <span className="text-label-s uppercase tracking-widest text-brand-press">Platform Capabilities</span>
          <h2 className="font-display text-display-l text-ink">One QR. One Table. One Seamless Experience.</h2>
          <p className="mx-auto max-w-xl text-body-m text-muted">
            Everything your restaurant, cafe, bar, or hotel needs to digitize dining operations.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {FEATURES.map((feature) => (
            <Card key={feature.title} interactive className="space-y-4">
              <div className={`flex h-12 w-12 items-center justify-center rounded-[var(--radius-xl2)] ${feature.iconClass}`}>
                <feature.icon className="h-6 w-6" aria-hidden="true" />
              </div>
              <h3 className="text-title-s text-ink">{feature.title}</h3>
              <p className="text-body-m text-muted">{feature.body}</p>
            </Card>
          ))}
        </div>
      </section>

      {/* Pricing */}
      <section className="mx-auto max-w-5xl space-y-8 px-4 text-center">
        <div className="space-y-2">
          <span className="text-label-s uppercase tracking-widest text-brand-press">Pricing Plans</span>
          <h2 className="font-display text-display-l text-ink">Transparent Plans for Every Business</h2>
        </div>

        <div className="grid grid-cols-1 gap-6 text-left md:grid-cols-3">
          <Card className="space-y-4">
            <IdentityChip>Starter Free</IdentityChip>
            <div className="text-title-l text-ink [font-variant-numeric:tabular-nums]">$0 <span className="text-body-m font-normal text-muted">/ forever</span></div>
            <p className="text-body-m text-muted">Perfect for small pop-ups &amp; coffee corners.</p>
            <ul className="space-y-2 border-t border-line pt-3 text-body-m text-ink">
              <li>&#10003; Up to 5 Tables</li>
              <li>&#10003; Basic Menu Builder</li>
              <li>&#10003; Standard QR Code Export</li>
            </ul>
            <Button onClick={onStartMerchantDemo} variant="secondary" fullWidth>Start Free</Button>
          </Card>

          <Card className="relative space-y-4 border-2 !border-brand-dark shadow-[var(--shadow-lift)]">
            <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-pill bg-brand-dark px-3 py-1 text-label-s uppercase text-brand-fg">
              Most Popular
            </span>
            <span className="text-label-s uppercase text-brand-press">Standard SaaS</span>
            <div className="text-title-l text-ink [font-variant-numeric:tabular-nums]">$29 <span className="text-body-m font-normal text-muted">/ month</span></div>
            <p className="text-body-m text-muted">Ideal for busy restaurants &amp; bars.</p>
            <ul className="space-y-2 border-t border-line pt-3 text-body-m text-ink">
              <li>&#10003; Up to 50 Tables</li>
              <li>&#10003; Real-time Kitchen Board (KDS)</li>
              <li>&#10003; QR Stand Studio Customizer</li>
              <li>&#10003; Sales Analytics &amp; PDF Export</li>
            </ul>
            <Button onClick={onStartMerchantDemo} fullWidth>Launch Merchant Trial</Button>
          </Card>

          <Card className="space-y-4">
            <IdentityChip>Enterprise</IdentityChip>
            <div className="text-title-l text-ink">Custom</div>
            <p className="text-body-m text-muted">For hotel chains &amp; large franchises.</p>
            <ul className="space-y-2 border-t border-line pt-3 text-body-m text-ink">
              <li>&#10003; Unlimited Tables &amp; Branches</li>
              <li>&#10003; POS &amp; M-PESA Integration</li>
              <li>&#10003; White-Label Domain</li>
            </ul>
            <Button onClick={onBookDemo} variant="secondary" fullWidth>Book Demo</Button>
          </Card>
        </div>
      </section>
    </div>
  );
};
