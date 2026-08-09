import React, { useState } from 'react';
import { 
  Table as TableIcon, 
  Plus, 
  Trash2, 
  Edit3, 
  QrCode, 
  Users, 
  CheckCircle2, 
  Clock, 
  Building2, 
  Layers,
  Smartphone
} from 'lucide-react';
import { Merchant, Branch, Floor, Table, TableStatus } from '../../types';

interface TableManagerProps {
  merchant: Merchant;
  branches: Branch[];
  floors: Floor[];
  tables: Table[];
  onSaveTable: (table: Table) => void;
  onDeleteTable: (tableId: string) => void;
  onUpdateTableStatus: (tableId: string, status: TableStatus) => void;
  onOpenQRDesigner: (tableNumber: string) => void;
  onSimulateCustomerScan: (tableNumber: string) => void;
}

export const TableManager: React.FC<TableManagerProps> = ({
  merchant,
  branches,
  floors,
  tables,
  onSaveTable,
  onDeleteTable,
  onUpdateTableStatus,
  onOpenQRDesigner,
  onSimulateCustomerScan,
}) => {
  const merchantBranches = branches.filter(b => b.merchantId === merchant.id);
  const [selectedBranchId, setSelectedBranchId] = useState<string>(merchantBranches[0]?.id || 'b1');

  const branchFloors = floors.filter(f => f.branchId === selectedBranchId);
  const [selectedFloorId, setSelectedFloorId] = useState<string>('all');

  const branchTables = tables.filter(t => t.branchId === selectedBranchId);
  const filteredTables = branchTables.filter(t => selectedFloorId === 'all' || t.floorId === selectedFloorId);

  // Table Add/Edit Modal
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingTable, setEditingTable] = useState<Partial<Table> | null>(null);

  const handleOpenModal = (tbl?: Table) => {
    setEditingTable(tbl || {
      tableNumber: `Table ${branchTables.length + 1}`,
      capacity: 4,
      floorId: branchFloors[0]?.id || 'f1',
      status: 'Available',
    });
    setIsModalOpen(true);
  };

  const handleSaveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingTable?.tableNumber) return;
    onSaveTable({
      id: editingTable.id || `t-${Date.now()}`,
      branchId: selectedBranchId,
      floorId: editingTable.floorId || branchFloors[0]?.id || 'f1',
      tableNumber: editingTable.tableNumber,
      capacity: Number(editingTable.capacity || 4),
      qrCodeUrl: editingTable.qrCodeUrl || '',
      status: editingTable.status || 'Available',
    });
    setIsModalOpen(false);
  };

  return (
    <div className="space-y-6">

      {/* Top Action Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-gray-100 shadow-xs">
        <div>
          <h2 className="text-xl font-black text-gray-900 flex items-center gap-2">
            <TableIcon className="w-6 h-6 text-[#E60028]" />
            Table & QR Code Manager
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">Configure floor layout, seating capacity, and QR codes per table</p>
        </div>

        <button
          onClick={() => handleOpenModal()}
          className="px-4 py-2.5 bg-[#E60028] hover:bg-[#CC0024] text-white text-xs font-bold rounded-xl shadow-md flex items-center gap-1.5 transition-colors self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          Add New Table
        </button>
      </div>

      {/* Branch & Floor Selectors */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-4 rounded-2xl border border-gray-100 shadow-xs">
        
        {/* Branch Selector */}
        <div className="flex items-center gap-2">
          <Building2 className="w-4 h-4 text-gray-400" />
          <span className="text-xs font-bold text-gray-700">Branch:</span>
          <select
            value={selectedBranchId}
            onChange={(e) => setSelectedBranchId(e.target.value)}
            className="text-xs font-bold bg-gray-50 border border-gray-200 rounded-xl px-3 py-1.5 focus:outline-none"
          >
            {merchantBranches.map(b => (
              <option key={b.id} value={b.id}>{b.name}</option>
            ))}
          </select>
        </div>

        {/* Floor Pills */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0 no-scrollbar text-xs">
          <span className="text-gray-400 font-bold flex items-center gap-1">
            <Layers className="w-3.5 h-3.5" /> Floor:
          </span>
          <button
            onClick={() => setSelectedFloorId('all')}
            className={`px-3 py-1 rounded-xl font-bold whitespace-nowrap transition-colors ${
              selectedFloorId === 'all' ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            All Floors ({branchTables.length})
          </button>
          {branchFloors.map(f => (
            <button
              key={f.id}
              onClick={() => setSelectedFloorId(f.id)}
              className={`px-3 py-1 rounded-xl font-bold whitespace-nowrap transition-colors ${
                selectedFloorId === f.id ? 'bg-[#E60028] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {f.name} ({branchTables.filter(t => t.floorId === f.id).length})
            </button>
          ))}
        </div>
      </div>

      {/* Tables Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {filteredTables.map(t => {
          const floorObj = floors.find(f => f.id === t.floorId);
          return (
            <div 
              key={t.id}
              className="bg-white rounded-3xl p-5 border border-gray-100 shadow-xs flex flex-col justify-between space-y-4 hover:shadow-md transition-all"
            >
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-lg font-black text-gray-900">{t.tableNumber}</span>
                  
                  {/* Table Status Selector */}
                  <select
                    value={t.status}
                    onChange={(e) => onUpdateTableStatus(t.id, e.target.value as TableStatus)}
                    className={`text-xs font-extrabold rounded-lg px-2 py-1 border focus:outline-none ${
                      t.status === 'Occupied' 
                        ? 'bg-amber-100 text-amber-900 border-amber-300' 
                        : t.status === 'Reserved' 
                        ? 'bg-indigo-100 text-indigo-900 border-indigo-300' 
                        : 'bg-emerald-100 text-emerald-900 border-emerald-300'
                    }`}
                  >
                    <option value="Available">Available</option>
                    <option value="Occupied">Occupied</option>
                    <option value="Reserved">Reserved</option>
                  </select>
                </div>

                <div className="space-y-1 text-xs text-gray-500">
                  <div className="flex items-center gap-1">
                    <Users className="w-3.5 h-3.5 text-gray-400" />
                    <span>Capacity: <strong className="text-gray-900">{t.capacity} Guests</strong></span>
                  </div>
                  <div className="flex items-center gap-1">
                    <Layers className="w-3.5 h-3.5 text-gray-400" />
                    <span>Section: {floorObj?.name || 'Main Dining'}</span>
                  </div>
                </div>
              </div>

              {/* Actions: Scan Simulator & QR Designer */}
              <div className="space-y-2 pt-3 border-t border-gray-100">
                <button
                  onClick={() => onSimulateCustomerScan(t.tableNumber)}
                  className="w-full py-2 bg-red-50 hover:bg-red-100 text-[#E60028] text-xs font-bold rounded-xl flex items-center justify-center gap-1.5 transition-colors"
                >
                  <Smartphone className="w-3.5 h-3.5" />
                  Simulate Customer QR Scan
                </button>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => onOpenQRDesigner(t.tableNumber)}
                    className="flex-1 py-2 bg-gray-900 hover:bg-black text-white text-xs font-bold rounded-xl flex items-center justify-center gap-1.5 transition-colors"
                  >
                    <QrCode className="w-3.5 h-3.5" />
                    QR Stand Studio
                  </button>
                  <button
                    onClick={() => handleOpenModal(t)}
                    className="p-2 text-gray-400 hover:text-gray-700 bg-gray-100 rounded-xl"
                  >
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(`Delete ${t.tableNumber}?`)) onDeleteTable(t.id);
                    }}
                    className="p-2 text-red-400 hover:text-red-600 bg-red-50 rounded-xl"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

            </div>
          );
        })}
      </div>

      {/* Add / Edit Table Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form onSubmit={handleSaveSubmit} className="bg-white max-w-sm w-full p-6 rounded-3xl shadow-2xl space-y-4">
            <h3 className="font-extrabold text-base text-gray-900">
              {editingTable?.id ? 'Edit Table Details' : 'Add New Restaurant Table'}
            </h3>

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Table Designation / Number</label>
              <input
                type="text"
                required
                value={editingTable?.tableNumber || ''}
                onChange={(e) => setEditingTable({ ...editingTable, tableNumber: e.target.value })}
                placeholder="e.g. Table 12 or VIP-1"
                className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#E60028]/20"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Seating Capacity</label>
              <input
                type="number"
                required
                min={1}
                value={editingTable?.capacity || 4}
                onChange={(e) => setEditingTable({ ...editingTable, capacity: parseInt(e.target.value) })}
                className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1">Floor Section</label>
              <select
                value={editingTable?.floorId || ''}
                onChange={(e) => setEditingTable({ ...editingTable, floorId: e.target.value })}
                className="w-full text-xs p-2.5 rounded-xl border border-gray-200 focus:outline-none"
              >
                {branchFloors.map(f => (
                  <option key={f.id} value={f.id}>{f.name}</option>
                ))}
              </select>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="submit"
                className="flex-1 py-2.5 bg-[#E60028] text-white font-bold text-xs rounded-xl shadow-xs"
              >
                Save Table
              </button>
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                className="py-2.5 px-4 bg-gray-100 text-gray-700 font-bold text-xs rounded-xl"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

    </div>
  );
};
