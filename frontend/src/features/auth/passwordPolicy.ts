/**
 * Mirror of the backend's PasswordPolicy (Java): at least 8 characters with an upper-case letter, a lower-case
 * letter, a digit and a special character (not a letter, digit or whitespace); at most 72 UTF-8 bytes.
 */
export type PasswordRule = { id: string; label: string; met: boolean };

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_BYTES = 72;

export function passwordRules(password: string): PasswordRule[] {
  const rules: PasswordRule[] = [
    { id: 'length', label: `At least ${PASSWORD_MIN_LENGTH} characters`, met: Array.from(password).length >= PASSWORD_MIN_LENGTH },
    { id: 'upper', label: 'An upper-case letter', met: /\p{Lu}/u.test(password) },
    { id: 'lower', label: 'A lower-case letter', met: /\p{Ll}/u.test(password) },
    { id: 'digit', label: 'A digit', met: /\p{Nd}/u.test(password) },
    { id: 'special', label: 'A special character, e.g. ! # $ %', met: /[^\p{L}\p{Nd}\s]/u.test(password) },
  ];
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) {
    rules.push({ id: 'max', label: `At most ${PASSWORD_MAX_BYTES} bytes`, met: false });
  }
  return rules;
}

export function isPasswordAcceptable(password: string): boolean {
  return passwordRules(password).every((rule) => rule.met);
}
