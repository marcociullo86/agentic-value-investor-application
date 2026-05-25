'use client';

import * as React from 'react';
import { useState, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import {
  Modal,
  ModalTrigger,
  ModalContent,
  ModalTitle,
  ModalDescription,
  ModalClose,
} from '@/components/ui/Modal';
import {
  getLlmCostStatus,
  updateLlmBudget,
  freezeLlm,
  unfreezeLlm,
  type LlmCostStatus,
} from '@/lib/api/llm-budget';
import { useAuthStore } from '@/lib/stores/useAuthStore';

export function LlmBudgetAdminPanel() {
  const [status, setStatus] = useState<LlmCostStatus | null>(null);
  const [newCap, setNewCap] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const user = useAuthStore((s) => s.user);

  const fetchStatus = useCallback(async () => {
    try {
      const res = await getLlmCostStatus();
      setStatus(res.data);
      setNewCap(res.data.monthlyCapUsd.toString());
    } catch {
      setError('Failed to load LLM cost status');
    }
  }, []);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  if (!user) return null;

  const handleUpdateBudget = async () => {
    setLoading(true);
    setError(null);
    try {
      const cap = parseFloat(newCap);
      if (isNaN(cap) || cap <= 0 || cap > 10000) {
        setError('Cap must be between $0.01 and $10,000');
        return;
      }
      await updateLlmBudget({ monthlyCapUsd: cap, reason: reason || undefined });
      setConfirmOpen(false);
      await fetchStatus();
    } catch (err: any) {
      const detail = err?.response?.data?.detail;
      setError(detail || 'Failed to update budget');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleFreeze = async () => {
    setLoading(true);
    try {
      if (status?.frozen) {
        await unfreezeLlm();
      } else {
        await freezeLlm();
      }
      await fetchStatus();
    } catch {
      setError('Failed to toggle freeze state');
    } finally {
      setLoading(false);
    }
  };

  const utilizationColor =
    (status?.utilization ?? 0) >= 100
      ? 'text-red-600'
      : (status?.utilization ?? 0) >= 80
        ? 'text-amber-600'
        : 'text-green-600';

  return (
    <Card className="p-6 max-w-lg">
      <h2 className="text-lg font-semibold mb-4">LLM Budget Administration</h2>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded mb-4 text-sm">
          {error}
        </div>
      )}

      {status && (
        <div className="space-y-3 mb-6">
          <div className="flex justify-between">
            <span className="text-slate-600">Monthly Cap</span>
            <span className="font-medium">${status.monthlyCapUsd.toFixed(2)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-600">Current Cost</span>
            <span className="font-medium">${status.totalCostUsd.toFixed(2)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-600">Utilization</span>
            <span className={`font-bold ${utilizationColor}`}>
              {status.utilization.toFixed(1)}%
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-600">Status</span>
            <span className={status.frozen ? 'text-red-600 font-bold' : 'text-green-600'}>
              {status.frozen ? 'FROZEN' : 'Active'}
            </span>
          </div>
        </div>
      )}

      <div className="space-y-3">
        <div>
          <label className="block text-sm text-slate-600 mb-1">New Monthly Cap ($)</label>
          <Input
            type="number"
            min="0.01"
            max="10000"
            step="0.01"
            value={newCap}
            onChange={(e) => setNewCap(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-sm text-slate-600 mb-1">Reason (optional)</label>
          <Input
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Increased for EP-011 analysis"
          />
        </div>

        <div className="flex gap-2 pt-2">
          <Modal open={confirmOpen} onOpenChange={setConfirmOpen}>
            <ModalTrigger asChild>
              <Button variant="default" disabled={loading}>
                Update Cap
              </Button>
            </ModalTrigger>
            <ModalContent>
              <ModalTitle>Confirm Budget Change</ModalTitle>
              <ModalDescription>
                Change monthly LLM budget cap from{' '}
                <strong>${status?.monthlyCapUsd.toFixed(2)}</strong> to{' '}
                <strong>${parseFloat(newCap || '0').toFixed(2)}</strong>?
              </ModalDescription>
              <div className="flex gap-2 justify-end mt-4">
                <ModalClose asChild>
                  <Button variant="outline">Cancel</Button>
                </ModalClose>
                <Button onClick={handleUpdateBudget} disabled={loading}>
                  {loading ? 'Saving...' : 'Confirm'}
                </Button>
              </div>
            </ModalContent>
          </Modal>

          <Button
            variant={status?.frozen ? 'default' : 'destructive'}
            onClick={handleToggleFreeze}
            disabled={loading}
          >
            {status?.frozen ? 'Unfreeze LLM' : 'Freeze LLM'}
          </Button>
        </div>
      </div>
    </Card>
  );
}
