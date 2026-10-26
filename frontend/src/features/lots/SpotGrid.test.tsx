import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { availableSpotIds, spots } from '../../test/fixtures';
import { SpotGrid, SpotLegend } from './SpotGrid';
import { isBookableState, managerSpotState, spotState, type SpotVisualState } from './spotState';

const available = new Set(availableSpotIds);

describe('spotState', () => {
  it('derives each visual state with manager flags taking priority', () => {
    expect(spots.map((s) => spotState(s, available))).toEqual(['free', 'occupied', 'booked', 'maintenance', 'disabled', 'free']);
  });

  it('ignores bookings when no window is selected', () => {
    expect(spotState(spots[2]!, null)).toBe('free');
  });

  it('treats a spot in a closed lot as unavailable', () => {
    expect(spotState({ ...spots[0]!, bookable: false }, available)).toBe('disabled');
  });

  it('lets drivers book free spots and spots that are occupied only right now', () => {
    const bookable = (['free', 'occupied', 'booked', 'maintenance', 'disabled'] as SpotVisualState[]).filter(isBookableState);
    expect(bookable).toEqual(['free', 'occupied']);
  });

  it('manager view shows sensor state and manager flags only', () => {
    expect(spots.map(managerSpotState)).toEqual(['free', 'occupied', 'free', 'maintenance', 'disabled', 'free']);
  });
});

function renderGrid(onSelect = vi.fn(), selectedId: number | null = null) {
  render(
    <SpotGrid
      label="Spots in Lot A"
      spots={spots}
      getState={(s) => spotState(s, available)}
      isSelectable={(_, state) => isBookableState(state)}
      selectedId={selectedId}
      onSelect={onSelect}
    />,
  );
  return onSelect;
}

describe('SpotGrid', () => {
  it('announces every spot with its label and state, not just a colour', () => {
    renderGrid();
    const group = screen.getByRole('radiogroup', { name: 'Spots in Lot A' });
    const radios = within(group).getAllByRole('radio');
    expect(radios.map((r) => r.getAttribute('aria-label'))).toEqual([
      'Spot A-001, Free',
      'Spot A-002, Occupied now',
      'Spot A-003, Booked',
      'Spot A-004, Maintenance',
      'Spot A-005, Unavailable',
      'Spot A-006, Free',
    ]);
    expect(radios.map((r) => r.dataset.state)).toEqual(['free', 'occupied', 'booked', 'maintenance', 'disabled', 'free']);
  });

  it('marks booked, maintenance and disabled spots as not selectable and ignores clicks on them', async () => {
    const onSelect = renderGrid();
    const user = userEvent.setup();
    for (const label of ['Spot A-003, Booked', 'Spot A-004, Maintenance', 'Spot A-005, Unavailable']) {
      const radio = screen.getByRole('radio', { name: label });
      expect(radio).toHaveAttribute('aria-disabled', 'true');
      await user.click(radio);
    }
    expect(onSelect).not.toHaveBeenCalled();
    await user.click(screen.getByRole('radio', { name: 'Spot A-002, Occupied now' }));
    expect(onSelect).toHaveBeenCalledWith(spots[1]);
  });

  it('is keyboard navigable: one tab stop, arrows skip unselectable spots', async () => {
    const onSelect = renderGrid(vi.fn(), 2);
    const user = userEvent.setup();
    const tabbable = screen.getAllByRole('radio').filter((r) => r.tabIndex === 0);
    expect(tabbable).toHaveLength(1);
    expect(tabbable[0]).toHaveAccessibleName('Spot A-002, Occupied now');
    expect(tabbable[0]).toBeChecked();

    tabbable[0]!.focus();
    await user.keyboard('{ArrowRight}');
    // A-003..A-005 can't be booked, so the next stop is A-006
    expect(onSelect).toHaveBeenLastCalledWith(spots[5]);
    expect(screen.getByRole('radio', { name: 'Spot A-006, Free' })).toHaveFocus();
  });
});

describe('SpotLegend', () => {
  it('lists each state with its label and count', () => {
    render(<SpotLegend states={['free', 'occupied', 'booked', 'maintenance', 'disabled']} counts={{ free: 2, occupied: 1, booked: 1 }} />);
    const legend = screen.getByRole('list', { name: 'Spot legend' });
    const items = within(legend).getAllByRole('listitem').map((li) => li.textContent);
    expect(items).toEqual(['Free2', 'Occupied now1', 'Booked1', 'Maintenance', 'Unavailable']);
  });
});
