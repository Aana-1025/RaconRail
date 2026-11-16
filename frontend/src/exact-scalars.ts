export function exactMoney(minor: string, currency: string): string {
  if (/\s/.test(minor) || currency.length !== 3 || !/^(0|-?[1-9][0-9]{0,37})$/.test(minor) || !/^[A-Za-z]{3}$/.test(currency)) throw new Error('INVALID_SCALAR');
  const code = currency.toUpperCase();
  const digits = code === 'JPY' ? 0 : code === 'USD' || code === 'INR' ? 2 : undefined;
  if (digits === undefined) return `${minor} ${code} (minor units; unsupported currency)`;
  const negative = minor.startsWith('-');
  const value = (negative ? minor.slice(1) : minor).padStart(digits + 1, '0');
  return `${negative ? '-' : ''}${digits ? `${value.slice(0, -digits)}.${value.slice(-digits)}` : value} ${code}`;
}
