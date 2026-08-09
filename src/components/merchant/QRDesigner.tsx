import React, { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { 
  QrCode, 
  Download, 
  Printer, 
  Sparkles, 
  Paintbrush, 
  Image as ImageIcon, 
  Check, 
  Copy, 
  ExternalLink,
  Store,
  Table as TableIcon,
  Loader2,
  FileDown
} from 'lucide-react';
import { Merchant, QRDesignConfig, Table } from '../../types';
import { qrApi } from '../../lib/api';
import { useTableQr } from '../../hooks/useApiData';

interface QRDesignerProps {
  merchant: Merchant;
  tables: Table[];
  activeTableNumber: string;
  setActiveTableNumber: (tbl: string) => void;
  config: QRDesignConfig;
  onSaveConfig: (newConfig: QRDesignConfig) => void;
}

export const QRDesigner: React.FC<QRDesignerProps> = ({
  merchant,
  tables,
  activeTableNumber,
  setActiveTableNumber,
  config,
  onSaveConfig,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [localConfig, setLocalConfig] = useState<QRDesignConfig>(config);
  const [selectedTableNum, setSelectedTableNum] = useState<string>(activeTableNumber || '1');
  const [exporting, setExporting] = useState<'png' | 'pdf' | null>(null);

  // Find the selected table to get its numeric ID for backend API calls
  const selectedTable = tables.find(t => t.tableNumber === selectedTableNum);
  const selectedTableId = selectedTable ? Number(selectedTable.id) : undefined;

  // Fetch real QR metadata from backend
  const { data: qrMetadata, isLoading: qrLoading, refetch: refetchQr } = useTableQr(selectedTableId);

  const menuUrl = qrMetadata?.qrUrl || `https://qrserve.com/menu/${merchant.slug}/${selectedTable?.branchId || 1}/${selectedTableId || 1}`;

  useEffect(() => {
    setLocalConfig(config);
  }, [config]);

  useEffect(() => {
    if (!canvasRef.current) return;

    // If we have backend QR metadata with base64 content, render that
    if (qrMetadata?.base64Content) {
      const img = new Image();
      img.onload = () => {
        if (canvasRef.current) {
          const ctx = canvasRef.current.getContext('2d');
          if (ctx) {
            ctx.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height);
            ctx.drawImage(img, 0, 0, canvasRef.current.width, canvasRef.current.height);
          }
        }
      };
      img.src = qrMetadata.base64Content;
      return;
    }

    // Fallback: generate QR client-side
    QRCode.toCanvas(
      canvasRef.current,
      menuUrl,
      {
        width: 220,
        margin: 2,
        color: {
          dark: localConfig.patternColor || '#1E1E1E',
          light: '#FFFFFF',
        },
      },
      (err) => {
        if (err) console.error('QR rendering error', err);
      }
    );
  }, [menuUrl, localConfig, qrMetadata]);

  const handleDownloadPNG = async () => {
    if (!selectedTableId) return;
    setExporting('png');
    try {
      // Use backend high-res PNG export
      const blob = await qrApi.exportPng({
        tableId: selectedTableId,
        format: 'PNG',
        brandColor: localConfig.primaryColor,
        titleText: localConfig.headerTitle,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.download = `QRStand-${merchant.slug}-Table-${selectedTableNum}.png`;
      link.href = url;
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to export PNG from backend, falling back to canvas', err);
      // Fallback to canvas export
      if (!canvasRef.current) return;
      const link = document.createElement('a');
      link.download = `QRStand-${merchant.slug}-Table-${selectedTableNum}.png`;
      link.href = canvasRef.current.toDataURL('image/png');
      link.click();
    } finally {
      setExporting(null);
    }
  };

  const handleDownloadPDF = async () => {
    if (!selectedTableId) return;
    setExporting('pdf');
    try {
      const blob = await qrApi.exportPdf({
        tableId: selectedTableId,
        format: 'PDF',
        brandColor: localConfig.primaryColor,
        titleText: localConfig.headerTitle,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.download = `QRStand-${merchant.slug}-Table-${selectedTableNum}.pdf`;
      link.href = url;
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to export PDF from backend', err);
      alert('PDF export failed. Please try again.');
    } finally {
      setExporting(null);
    }
  };

  const handleCopyUrl = async () => {
    try {
      await navigator.clipboard.writeText(menuUrl);
      alert('QR URL copied to clipboard!');
    } catch {
      // Clipboard API unavailable
    }
  };

  const handlePrintStand = () => {
    window.print();
  };

  const templates: { name: QRDesignConfig['template']; primary: string; accent: string; bg: string; pattern: string }[] = [
    { name: 'Modern', primary: '#E60028', accent: '#FFB000', bg: '#FFFFFF', pattern: '#1E1E1E' },
    { name: 'Elegant', primary: '#18181B', accent: '#D4AF37', bg: '#FAFAFA', pattern: '#09090B' },
    { name: 'Coffee', primary: '#78350F', accent: '#D97706', bg: '#FFFBEB', pattern: '#451A03' },
    { name: 'Luxury Hotel', primary: '#0F172A', accent: '#E2E8F0', bg: '#020617', pattern: '#FFFFFF' },
  ];

  const applyTemplate = (tpl: typeof templates[0]) => {
    const updated = {
      ...localConfig,
      template: tpl.name,
      primaryColor: tpl.primary,
      accentColor: tpl.accent,
      backgroundColor: tpl.bg,
      patternColor: tpl.pattern,
    };
    setLocalConfig(updated);
    onSaveConfig(updated);
  };

  return (
    <div className="space-y-6">

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-gray-100 shadow-xs">
        <div>
          <h2 className="text-xl font-black text-gray-900 flex items-center gap-2">
            <QrCode className="w-6 h-6 text-[#E60028]" />
            QR Code Stand Designer & Print Studio
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">Customize brand colors, table display templates, and export print-ready cards</p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleDownloadPNG}
            disabled={exporting !== null || !selectedTableId}
            className="px-4 py-2.5 bg-gray-900 hover:bg-black text-white text-xs font-bold rounded-xl shadow-md flex items-center gap-1.5 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {exporting === 'png' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            Export High-Res PNG
          </button>
          <button
            onClick={handleDownloadPDF}
            disabled={exporting !== null || !selectedTableId}
            className="px-4 py-2.5 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl shadow-md flex items-center gap-1.5 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {exporting === 'pdf' ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileDown className="w-4 h-4" />}
            Export PDF Stand
          </button>
          <button
            onClick={handlePrintStand}
            className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-white text-xs font-bold rounded-xl shadow-md flex items-center gap-1.5 transition-colors"
          >
            <Printer className="w-4 h-4" />
            Print
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        
        {/* Left Column (Controls & Customizer) */}
        <div className="lg:col-span-6 space-y-6">

          {/* Table Selector */}
          <div className="bg-white p-5 rounded-3xl border border-gray-100 shadow-xs space-y-3">
            <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider">
              Select Table to Generate Stand
            </label>
            <div className="flex items-center gap-3">
              <select
                value={selectedTableNum}
                onChange={(e) => {
                  setSelectedTableNum(e.target.value);
                  setActiveTableNumber(e.target.value);
                  refetchQr();
                }}
                className="w-full text-xs font-bold p-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
              >
                {tables.length === 0 ? (
                  <option value="">No tables available</option>
                ) : (
                  tables.map(t => (
                    <option key={t.id} value={t.tableNumber}>
                      Table {t.tableNumber} (Capacity: {t.capacity})
                    </option>
                  ))
                )}
              </select>
            </div>
            <div className="text-[11px] text-gray-400 truncate flex items-center gap-1">
              <span>Target URL:</span>
              {qrLoading ? (
                <span className="flex items-center gap-1 text-gray-500">
                  <Loader2 className="w-3 h-3 animate-spin" /> Fetching from backend...
                </span>
              ) : (
                <span className="font-mono text-gray-700 truncate">{menuUrl}</span>
              )}
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={handleCopyUrl}
                className="text-[11px] font-bold text-gray-600 bg-gray-100 hover:bg-gray-200 px-2.5 py-1 rounded-lg flex items-center gap-1 transition-colors"
              >
                <Copy className="w-3 h-3" /> Copy URL
              </button>
              <a
                href={menuUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-[11px] font-bold text-[#E60028] hover:underline flex items-center gap-1"
              >
                <ExternalLink className="w-3 h-3" /> Open Menu Link
              </a>
            </div>
          </div>

          {/* Presets / Templates */}
          <div className="bg-white p-5 rounded-3xl border border-gray-100 shadow-xs space-y-3">
            <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider flex items-center gap-1.5">
              <Sparkles className="w-4 h-4 text-amber-500" />
              Theme Presets
            </label>

            <div className="grid grid-cols-2 gap-3">
              {templates.map(tpl => (
                <button
                  key={tpl.name}
                  onClick={() => applyTemplate(tpl)}
                  className={`p-3 rounded-2xl border text-left transition-all ${
                    localConfig.template === tpl.name
                      ? 'border-[#E60028] bg-red-50/50 ring-2 ring-red-500/20 font-bold'
                      : 'border-gray-200 bg-white hover:border-gray-300'
                  }`}
                >
                  <div className="text-xs font-black text-gray-900 mb-1">{tpl.name}</div>
                  <div className="flex gap-1.5">
                    <span className="w-4 h-4 rounded-full border border-gray-300" style={{ backgroundColor: tpl.primary }} />
                    <span className="w-4 h-4 rounded-full border border-gray-300" style={{ backgroundColor: tpl.accent }} />
                    <span className="w-4 h-4 rounded-full border border-gray-300" style={{ backgroundColor: tpl.pattern }} />
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Color & Typography Controls */}
          <div className="bg-white p-5 rounded-3xl border border-gray-100 shadow-xs space-y-4">
            <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider flex items-center gap-1.5">
              <Paintbrush className="w-4 h-4 text-gray-600" />
              Branding & Text Customization
            </label>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] font-bold text-gray-600 mb-1">Header Title</label>
                <input
                  type="text"
                  value={localConfig.headerTitle}
                  onChange={(e) => {
                    const updated = { ...localConfig, headerTitle: e.target.value };
                    setLocalConfig(updated);
                    onSaveConfig(updated);
                  }}
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-gray-600 mb-1">Call To Action</label>
                <input
                  type="text"
                  value={localConfig.callToAction}
                  onChange={(e) => {
                    const updated = { ...localConfig, callToAction: e.target.value };
                    setLocalConfig(updated);
                    onSaveConfig(updated);
                  }}
                  className="w-full text-xs p-2.5 rounded-xl border border-gray-200"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-gray-600 mb-1">Primary Color</label>
                <input
                  type="color"
                  value={localConfig.primaryColor}
                  onChange={(e) => {
                    const updated = { ...localConfig, primaryColor: e.target.value };
                    setLocalConfig(updated);
                    onSaveConfig(updated);
                  }}
                  className="w-full h-9 rounded-xl border border-gray-200 cursor-pointer p-1"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-gray-600 mb-1">QR Pattern Color</label>
                <input
                  type="color"
                  value={localConfig.patternColor}
                  onChange={(e) => {
                    const updated = { ...localConfig, patternColor: e.target.value };
                    setLocalConfig(updated);
                    onSaveConfig(updated);
                  }}
                  className="w-full h-9 rounded-xl border border-gray-200 cursor-pointer p-1"
                />
              </div>
            </div>
          </div>

        </div>

        {/* Right Column (Live Stand Card Preview) */}
        <div className="lg:col-span-6 flex flex-col items-center justify-center">

          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">
            Live Acrylic Stand Card Preview
          </div>

          {/* Printable Stand Container Card */}
          <div 
            id="printable-qr-stand"
            className="w-80 rounded-3xl p-6 shadow-2xl border-4 transition-all duration-300 flex flex-col items-center text-center relative overflow-hidden"
            style={{
              backgroundColor: localConfig.backgroundColor,
              borderColor: localConfig.primaryColor,
              color: localConfig.template === 'Luxury Hotel' ? '#FFFFFF' : '#1E1E1E',
            }}
          >
            {/* Top Accent Stripe */}
            <div 
              className="absolute top-0 left-0 right-0 h-3" 
              style={{ backgroundColor: localConfig.primaryColor }}
            />

            {/* Merchant Logo & Name */}
            <div className="mt-3 flex flex-col items-center space-y-1.5">
              {merchant.logo && (
                <img 
                  src={merchant.logo} 
                  alt={merchant.name} 
                  className="w-14 h-14 rounded-2xl object-cover border-2 shadow-md bg-white"
                  style={{ borderColor: localConfig.primaryColor }}
                />
              )}
              <h3 className="font-black text-lg tracking-tight uppercase" style={{ color: localConfig.primaryColor }}>
                {localConfig.headerTitle || merchant.name}
              </h3>
              <p className="text-[11px] font-semibold opacity-70">
                {localConfig.subTitle}
              </p>
            </div>

            {/* Table Number Pill */}
            <div 
              className="my-4 px-4 py-1.5 rounded-full text-sm font-black text-white shadow-sm flex items-center gap-1.5"
              style={{ backgroundColor: localConfig.primaryColor }}
            >
              <TableIcon className="w-4 h-4" />
              TABLE {selectedTableNum}
            </div>

            {/* Canvas QR Code Box */}
            <div className="p-3 bg-white rounded-2xl shadow-inner border border-gray-200 relative">
              <canvas ref={canvasRef} className="rounded-xl" />
              {qrLoading && (
                <div className="absolute inset-0 bg-white/70 flex items-center justify-center rounded-2xl">
                  <Loader2 className="w-6 h-6 animate-spin text-[#E60028]" />
                </div>
              )}
            </div>

            {/* Call to Action */}
            <div className="mt-4 space-y-1">
              <div className="text-xs font-extrabold uppercase tracking-wide">
                {localConfig.callToAction}
              </div>
              <div className="text-[10px] opacity-60 font-mono">
                No app download required • Instant ordering
              </div>
            </div>

            {/* Cut Line Marker */}
            <div className="mt-6 pt-3 border-t border-dashed border-gray-300 text-[9px] opacity-50 w-full text-center">
              ✂️ Standard Acrylic Table Stand 4" x 6"
            </div>

          </div>

        </div>

      </div>

    </div>
  );
};