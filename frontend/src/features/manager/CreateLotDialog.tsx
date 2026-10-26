import { useState, type SubmitEvent } from 'react';
import { errorMessage, fieldErrors } from '../../api/problem';
import type { Lot } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Alert } from '../../components/ui/Feedback';
import { Field, Input } from '../../components/ui/Field';
import { useCreateLot } from './hooks';

const EMPTY = { name: '', location: '', capacity: '100' };

export function CreateLotDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (lot: Lot) => void }) {
  const [form, setForm] = useState(EMPTY);
  const create = useCreateLot();
  const errors = fieldErrors(create.error);
  const capacity = Number(form.capacity);
  const capacityError = form.capacity && (!Number.isInteger(capacity) || capacity < 1 || capacity > 500) ? 'Between 1 and 500 spots.' : null;
  const canSubmit = !!form.name.trim() && !!form.location.trim() && !capacityError && !!form.capacity;

  function close() {
    setForm(EMPTY);
    create.reset();
    onClose();
  }

  function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit) return;
    create.mutate(
      { name: form.name.trim(), location: form.location.trim(), capacity },
      {
        onSuccess: (lot) => {
          onCreated(lot);
          close();
        },
      },
    );
  }

  return (
    <Dialog open={open} onClose={close} title="Create a lot" description="Spots are generated automatically, each with its own sensor id.">
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {create.isError && Object.keys(errors).length === 0 && <Alert tone="danger">{errorMessage(create.error)}</Alert>}
        <Field label="Name" error={errors.name}>
          <Input value={form.name} maxLength={100} placeholder="Lot D — York Lanes" onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </Field>
        <Field label="Location" error={errors.location}>
          <Input value={form.location} maxLength={200} placeholder="Street address or landmark" onChange={(e) => setForm({ ...form, location: e.target.value })} />
        </Field>
        <Field label="Number of spots" hint="1–500. Spots are labelled L<lot id>-001 and up." error={errors.capacity ?? capacityError}>
          <Input type="number" min={1} max={500} inputMode="numeric" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
        </Field>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={close}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending} disabled={!canSubmit}>
            Create lot
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
