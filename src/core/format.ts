/** Display formatting for the session readout. Pure, so it stays testable. */

export function formatClock(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(whole / 60);
  return `${minutes}:${String(whole % 60).padStart(2, '0')}`;
}

export function formatSteps(count: number): string {
  return `${count.toLocaleString('en-US')} ${count === 1 ? 'step' : 'steps'}`;
}
