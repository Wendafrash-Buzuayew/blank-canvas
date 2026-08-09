import React from 'react';
import { 
  QrCode, 
  Smartphone, 
  ChefHat, 
  Table as TableIcon, 
  Printer, 
  BarChart3, 
  Building2, 
  CheckCircle2, 
  ArrowRight, 
  Sparkles, 
  Flame, 
  ShieldCheck,
  Zap,
  Clock,
  DollarSign
} from 'lucide-react';

interface LandingPageProps {
  onStartCustomerDemo: () => void;
  onStartMerchantDemo: () => void;
  onBookDemo: () => void;
}

export const LandingPage: React.FC<LandingPageProps> = ({
  onStartCustomerDemo,
  onStartMerchantDemo,
  onBookDemo,
}) => {
  return (
    <div className="space-y-16 pb-16">

      {/* Hero Section */}
      <section className="relative overflow-hidden bg-gradient-to-b from-gray-900 via-gray-950 to-black text-white py-20 px-4 sm:px-6 lg:px-8 rounded-3xl shadow-2xl">
        <div className="absolute inset-0 opacity-10 bg-[radial-gradient(#E60028_1px,transparent_1px)] [background-size:16px_16px]" />
        
        <div className="relative max-w-4xl mx-auto text-center space-y-6">
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/10 border border-white/20 text-xs font-bold text-amber-300 backdrop-blur-md">
            <Sparkles className="w-4 h-4 text-amber-400 animate-pulse" />
            <span>Next-Gen Smart QR Menu & Order Management Platform</span>
          </div>

          <h1 className="text-4xl sm:text-6xl font-black tracking-tight leading-tight">
            Transform Your Restaurant with <span className="bg-gradient-to-r from-[#E60028] via-amber-400 to-[#FF4D6D] bg-clip-text text-transparent">Smart QR Menus</span>
          </h1>

          <p className="text-base sm:text-lg text-gray-300 max-w-2xl mx-auto font-medium leading-relaxed">
            Create menus, generate table QR codes, receive real-time kitchen orders, and print acrylic stands — all from one unified platform.
          </p>

          <div className="pt-4 flex flex-col sm:flex-row items-center justify-center gap-4">
            <button
              onClick={onStartCustomerDemo}
              className="w-full sm:w-auto px-8 py-4 bg-[#E60028] hover:bg-[#CC0024] text-white font-extrabold text-sm rounded-2xl shadow-xl shadow-red-500/30 flex items-center justify-center gap-2 transition-all transform hover:-translate-y-0.5"
            >
              <Smartphone className="w-5 h-5" />
              Try Customer QR Menu (Scan Simulation)
            </button>

            <button
              onClick={onStartMerchantDemo}
              className="w-full sm:w-auto px-8 py-4 bg-white text-gray-900 hover:bg-gray-100 font-extrabold text-sm rounded-2xl shadow-lg flex items-center justify-center gap-2 transition-all transform hover:-translate-y-0.5"
            >
              <ChefHat className="w-5 h-5 text-[#E60028]" />
              Merchant Portal Demo
            </button>
          </div>

          {/* Key Metrics Banner */}
          <div className="pt-10 grid grid-cols-2 sm:grid-cols-4 gap-4 max-w-3xl mx-auto text-left">
            <div className="p-4 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
              <div className="text-2xl font-black text-amber-400">0s</div>
              <div className="text-xs text-gray-400">App Download Needed</div>
            </div>
            <div className="p-4 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
              <div className="text-2xl font-black text-red-400">3x</div>
              <div className="text-xs text-gray-400">Faster Table Turnover</div>
            </div>
            <div className="p-4 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
              <div className="text-2xl font-black text-emerald-400">+25%</div>
              <div className="text-xs text-gray-400">Avg Order Value Increase</div>
            </div>
            <div className="p-4 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
              <div className="text-2xl font-black text-indigo-400">100%</div>
              <div className="text-xs text-gray-400">Real-Time Kitchen Sync</div>
            </div>
          </div>
        </div>
      </section>

      {/* Value Proposition Grid */}
      <section className="space-y-8 max-w-6xl mx-auto px-4">
        <div className="text-center space-y-2">
          <span className="text-xs font-black uppercase text-[#E60028] tracking-widest">Platform Capabilities</span>
          <h2 className="text-3xl font-black text-gray-900">One QR. One Table. One Seamless Experience.</h2>
          <p className="text-sm text-gray-500 max-w-xl mx-auto">
            Everything your restaurant, cafe, bar, or hotel needs to digitize dining operations.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          
          <div className="p-6 rounded-3xl bg-white border border-gray-100 shadow-xs hover:shadow-xl transition-shadow space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-red-50 text-[#E60028] flex items-center justify-center">
              <QrCode className="w-6 h-6" />
            </div>
            <h3 className="font-extrabold text-lg text-gray-900">Smart QR Menus</h3>
            <p className="text-xs text-gray-500 leading-relaxed">
              Customers point their phone camera to instantly view interactive digital menus with appetizing photos, filter tags, and dietary options without downloading an app.
            </p>
          </div>

          <div className="p-6 rounded-3xl bg-white border border-gray-100 shadow-xs hover:shadow-xl transition-shadow space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center">
              <ChefHat className="w-6 h-6" />
            </div>
            <h3 className="font-extrabold text-lg text-gray-900">Real-Time Kitchen Orders</h3>
            <p className="text-xs text-gray-500 leading-relaxed">
              Orders land instantly on the kitchen display board (KDS) with table identification, exact quantities, special customer notes, and prep timer alerts.
            </p>
          </div>

          <div className="p-6 rounded-3xl bg-white border border-gray-100 shadow-xs hover:shadow-xl transition-shadow space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <Printer className="w-6 h-6" />
            </div>
            <h3 className="font-extrabold text-lg text-gray-900">Printable QR Stand Designer</h3>
            <p className="text-xs text-gray-500 leading-relaxed">
              Customize brand colors, logo overlays, and call-to-actions. Export vector SVGs, PNGs, or print-ready 4x6" acrylic table stand templates in seconds.
            </p>
          </div>

          <div className="p-6 rounded-3xl bg-white border border-gray-100 shadow-xs hover:shadow-xl transition-shadow space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <TableIcon className="w-6 h-6" />
            </div>
            <h3 className="font-extrabold text-lg text-gray-900">Table & Floor Management</h3>
            <p className="text-xs text-gray-500 leading-relaxed">
              Organize multiple branches, floors, and VIP sections. Assign unique QR links per table so the kitchen knows exactly where to deliver meals.
            </p>
          </div>

          <div className="p-6 rounded-3xl bg-white border border-gray-100 shadow-xs hover:shadow-xl transition-shadow space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-purple-50 text-purple-600 flex items-center justify-center">
              <BarChart3 className="w-6 h-6" />
            </div>
            <h3 className="font-extrabold text-lg text-gray-900">Analytics Dashboard</h3>
            <p className="text-xs text-gray-500 leading-relaxed">
              Track daily sales volume, peak ordering hours, bestselling dishes, table turnover speed, and customer order notes.
            </p>
          </div>

          <div className="p-6 rounded-3xl bg-white border border-gray-100 shadow-xs hover:shadow-xl transition-shadow space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center">
              <Building2 className="w-6 h-6" />
            </div>
            <h3 className="font-extrabold text-lg text-gray-900">Multi-Branch & Multi-Tenant</h3>
            <p className="text-xs text-gray-500 leading-relaxed">
              Expand effortlessly from single coffee shops to hotel chains with role-based staff permissions for Cashiers, Waiters, and Managers.
            </p>
          </div>

        </div>
      </section>

      {/* Simple Pricing Section */}
      <section className="max-w-5xl mx-auto px-4 text-center space-y-8">
        <div className="space-y-2">
          <span className="text-xs font-black uppercase text-[#E60028] tracking-widest">Pricing Plans</span>
          <h2 className="text-3xl font-black text-gray-900">Transparent Plans for Every Business</h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-left">
          
          <div className="p-6 rounded-3xl bg-white border border-gray-200 shadow-xs space-y-4">
            <span className="text-xs font-bold text-gray-400 uppercase">Starter Free</span>
            <div className="text-3xl font-black text-gray-900">$0 <span className="text-xs font-normal text-gray-500">/ forever</span></div>
            <p className="text-xs text-gray-500">Perfect for small pop-ups & coffee corners.</p>
            <ul className="text-xs text-gray-600 space-y-2 border-t pt-3">
              <li className="flex items-center gap-2">✓ Up to 5 Tables</li>
              <li className="flex items-center gap-2">✓ Basic Menu Builder</li>
              <li className="flex items-center gap-2">✓ Standard QR Code Export</li>
            </ul>
            <button onClick={onStartMerchantDemo} className="w-full py-3 bg-gray-900 text-white font-bold text-xs rounded-xl">
              Start Free
            </button>
          </div>

          <div className="p-6 rounded-3xl bg-white border-2 border-[#E60028] shadow-xl space-y-4 relative">
            <span className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-1 bg-[#E60028] text-white text-[10px] font-black rounded-full uppercase">
              Most Popular
            </span>
            <span className="text-xs font-bold text-[#E60028] uppercase">Standard SaaS</span>
            <div className="text-3xl font-black text-gray-900">$29 <span className="text-xs font-normal text-gray-500">/ month</span></div>
            <p className="text-xs text-gray-500">Ideal for busy restaurants & bars.</p>
            <ul className="text-xs text-gray-600 space-y-2 border-t pt-3">
              <li className="flex items-center gap-2">✓ Up to 50 Tables</li>
              <li className="flex items-center gap-2">✓ Real-time Kitchen Board (KDS)</li>
              <li className="flex items-center gap-2">✓ QR Stand Studio Customizer</li>
              <li className="flex items-center gap-2">✓ Sales Analytics & PDF Export</li>
            </ul>
            <button onClick={onStartMerchantDemo} className="w-full py-3 bg-[#E60028] text-white font-bold text-xs rounded-xl shadow-md">
              Launch Merchant Trial
            </button>
          </div>

          <div className="p-6 rounded-3xl bg-white border border-gray-200 shadow-xs space-y-4">
            <span className="text-xs font-bold text-gray-400 uppercase">Enterprise</span>
            <div className="text-3xl font-black text-gray-900">Custom</div>
            <p className="text-xs text-gray-500">For hotel chains & large franchises.</p>
            <ul className="text-xs text-gray-600 space-y-2 border-t pt-3">
              <li className="flex items-center gap-2">✓ Unlimited Tables & Branches</li>
              <li className="flex items-center gap-2">✓ POS & M-PESA Integration</li>
              <li className="flex items-center gap-2">✓ White-Label Domain</li>
            </ul>
            <button onClick={onBookDemo} className="w-full py-3 bg-gray-100 text-gray-900 font-bold text-xs rounded-xl">
              Book Demo
            </button>
          </div>

        </div>
      </section>

    </div>
  );
};
