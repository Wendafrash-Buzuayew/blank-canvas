export function formatMoney(amount: number, currencySymbol: string = '$'): string {
  return `${currencySymbol}${amount.toFixed(2)}`;
}

export function generateOrderNumber(): string {
  return Math.floor(1000 + Math.random() * 9000).toString();
}

export function timeAgo(isoDate: string): string {
  const date = new Date(isoDate);
  const seconds = Math.floor((new Date().getTime() - date.getTime()) / 1000);
  
  if (seconds < 60) return 'Just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}
