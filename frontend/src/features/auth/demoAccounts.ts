/**
 * Seeded demo accounts (backend `demo` profile) shown as one-click sign-in buttons on the login page.
 * Hide them with VITE_SHOW_DEMO_ACCOUNTS=false for a deployment that isn't a demo, or hide only the super-manager
 * button with VITE_SHOW_SUPER_MANAGER_DEMO=false when its password is not the dev default.
 */
export type DemoAccount = { label: string; description: string; email: string; password: string };

export const DEMO_PASSWORD = 'Demo#2026';

const SUPER_MANAGER_DEMO: DemoAccount = {
  label: 'Super manager',
  description: 'Admin',
  email: 'admin@campus-parking.local',
  password: 'ChangeMe!2024',
};

export const DEMO_ACCOUNTS: readonly DemoAccount[] = [
  { label: 'Student', description: '$5/h', email: 'student.demo@my.yorku.ca', password: DEMO_PASSWORD },
  { label: 'Faculty', description: '$8/h', email: 'faculty.demo@yorku.ca', password: DEMO_PASSWORD },
  { label: 'Staff', description: '$10/h', email: 'staff.demo@yorku.ca', password: DEMO_PASSWORD },
  { label: 'Visitor', description: '$15/h', email: 'visitor.demo@example.com', password: DEMO_PASSWORD },
  { label: 'Manager', description: 'Dashboard', email: 'manager.demo@yorku.ca', password: DEMO_PASSWORD },
  ...(import.meta.env.VITE_SHOW_SUPER_MANAGER_DEMO !== 'false' ? [SUPER_MANAGER_DEMO] : []),
];

export const SHOW_DEMO_ACCOUNTS = import.meta.env.VITE_SHOW_DEMO_ACCOUNTS !== 'false';
