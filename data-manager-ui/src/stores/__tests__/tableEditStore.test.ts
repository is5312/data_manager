import { describe, it, expect, beforeEach } from 'vitest';
import { useTableEditStore } from '../tableEditStore';

describe('tableEditStore', () => {
  beforeEach(() => {
    // Clear all changes before each test
    useTableEditStore.getState().clearChanges();
  });

  describe('Insert Tracking', () => {
    it('adds insert to store', () => {
      const store = useTableEditStore.getState();
      const success = store.addInsert('temp1', { name: 'Test' });

      expect(success).toBe(true);
      expect(store.isRowInserted('temp1')).toBe(true);
      expect(store.getPendingChangesCount()).toBe(1);
    });

    it('removes insert from store', () => {
      const store = useTableEditStore.getState();
      store.addInsert('temp1', { name: 'Test' });
      store.removeInsert('temp1');

      expect(store.isRowInserted('temp1')).toBe(false);
      expect(store.getPendingChangesCount()).toBe(0);
    });

    it('enforces 50 change limit', () => {
      const store = useTableEditStore.getState();

      // Add 50 inserts
      for (let i = 0; i < 50; i++) {
        store.addInsert(`temp${i}`, { name: `Test${i}` });
      }

      // 51st insert should fail
      const success = store.addInsert('temp51', { name: 'Test51' });
      expect(success).toBe(false);
      expect(store.getPendingChangesCount()).toBe(50);
    });

    it('returns false when limit reached', () => {
      const store = useTableEditStore.getState();

      // Fill up to limit
      for (let i = 0; i < 50; i++) {
        store.addInsert(`temp${i}`, { name: `Test${i}` });
      }

      const success = store.addInsert('temp51', { name: 'Test51' });
      expect(success).toBe(false);
    });
  });

  describe('Update Tracking', () => {
    it('tracks cell updates', () => {
      const store = useTableEditStore.getState();
      const success = store.updateCell(1, 'name', 'Old', 'New');

      expect(success).toBe(true);
      expect(store.isCellUpdated(1, 'name')).toBe(true);
      expect(store.getPendingChangesCount()).toBe(1);
    });

    it('removes update if reverted to original', () => {
      const store = useTableEditStore.getState();
      store.updateCell(1, 'name', 'Old', 'New');
      store.updateCell(1, 'name', 'New', 'Old');

      expect(store.isCellUpdated(1, 'name')).toBe(false);
      expect(store.getPendingChangesCount()).toBe(0);
    });

    it('groups updates by row', () => {
      const store = useTableEditStore.getState();
      store.updateCell(1, 'name', 'Old1', 'New1');
      store.updateCell(1, 'age', 'Old2', 'New2');

      expect(store.isCellUpdated(1, 'name')).toBe(true);
      expect(store.isCellUpdated(1, 'age')).toBe(true);
      expect(store.getPendingChangesCount()).toBe(1); // One row, multiple cells
    });

    it('enforces 50 change limit', () => {
      const store = useTableEditStore.getState();

      // Add 50 updates
      for (let i = 0; i < 50; i++) {
        store.updateCell(i, 'name', `Old${i}`, `New${i}`);
      }

      // 51st update should fail
      const success = store.updateCell(50, 'name', 'Old50', 'New50');
      expect(success).toBe(false);
    });
  });

  describe('Delete Tracking', () => {
    it('marks row for deletion', () => {
      const store = useTableEditStore.getState();
      const success = store.markForDelete(1);

      expect(success).toBe(true);
      expect(store.isRowDeleted(1)).toBe(true);
      expect(store.getPendingChangesCount()).toBe(1);
    });

    it('unmarks row for deletion', () => {
      const store = useTableEditStore.getState();
      store.markForDelete(1);
      store.unmarkForDelete(1);

      expect(store.isRowDeleted(1)).toBe(false);
      expect(store.getPendingChangesCount()).toBe(0);
    });

    it('removes pending updates when deleted', () => {
      const store = useTableEditStore.getState();
      store.updateCell(1, 'name', 'Old', 'New');
      store.markForDelete(1);

      expect(store.isRowDeleted(1)).toBe(true);
      expect(store.isCellUpdated(1, 'name')).toBe(false);
    });

    it('enforces 50 change limit', () => {
      const store = useTableEditStore.getState();

      // Add 50 deletes
      for (let i = 0; i < 50; i++) {
        store.markForDelete(i);
      }

      // 51st delete should fail
      const success = store.markForDelete(50);
      expect(success).toBe(false);
    });
  });

  describe('State Queries', () => {
    it('hasChanges returns correct value', () => {
      const store = useTableEditStore.getState();
      expect(store.hasChanges()).toBe(false);

      store.addInsert('temp1', { name: 'Test' });
      expect(store.hasChanges()).toBe(true);
    });

    it('getPendingChangesCount returns correct count', () => {
      const store = useTableEditStore.getState();
      expect(store.getPendingChangesCount()).toBe(0);

      store.addInsert('temp1', { name: 'Test' });
      store.updateCell(1, 'name', 'Old', 'New');
      store.markForDelete(2);

      expect(store.getPendingChangesCount()).toBe(3);
    });

    it('isAtLimit returns correct value', () => {
      const store = useTableEditStore.getState();
      expect(store.isAtLimit()).toBe(false);

      // Fill to limit
      for (let i = 0; i < 50; i++) {
        store.addInsert(`temp${i}`, { name: `Test${i}` });
      }

      expect(store.isAtLimit()).toBe(true);
    });

    it('isRowDeleted works correctly', () => {
      const store = useTableEditStore.getState();
      expect(store.isRowDeleted(1)).toBe(false);

      store.markForDelete(1);
      expect(store.isRowDeleted(1)).toBe(true);
    });

    it('isRowInserted works correctly', () => {
      const store = useTableEditStore.getState();
      expect(store.isRowInserted('temp1')).toBe(false);

      store.addInsert('temp1', { name: 'Test' });
      expect(store.isRowInserted('temp1')).toBe(true);
    });

    it('isCellUpdated works correctly', () => {
      const store = useTableEditStore.getState();
      expect(store.isCellUpdated(1, 'name')).toBe(false);

      store.updateCell(1, 'name', 'Old', 'New');
      expect(store.isCellUpdated(1, 'name')).toBe(true);
    });

    it('getAllChanges returns correct structure', () => {
      const store = useTableEditStore.getState();
      store.addInsert('temp1', { name: 'Test' });
      store.updateCell(1, 'name', 'Old', 'New');
      store.markForDelete(2);

      const changes = store.getAllChanges();
      expect(changes.inserts).toHaveLength(1);
      expect(changes.updates).toHaveLength(1);
      expect(changes.deletes).toHaveLength(1);
    });
  });

  describe('Clear Changes', () => {
    it('clears all inserts', () => {
      const store = useTableEditStore.getState();
      store.addInsert('temp1', { name: 'Test' });
      store.clearChanges();

      expect(store.isRowInserted('temp1')).toBe(false);
      expect(store.getPendingChangesCount()).toBe(0);
    });

    it('clears all updates', () => {
      const store = useTableEditStore.getState();
      store.updateCell(1, 'name', 'Old', 'New');
      store.clearChanges();

      expect(store.isCellUpdated(1, 'name')).toBe(false);
      expect(store.getPendingChangesCount()).toBe(0);
    });

    it('clears all deletes', () => {
      const store = useTableEditStore.getState();
      store.markForDelete(1);
      store.clearChanges();

      expect(store.isRowDeleted(1)).toBe(false);
      expect(store.getPendingChangesCount()).toBe(0);
    });
  });
});

