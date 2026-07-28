/**
 * The scheduler's heartbeat.
 *
 * It only has to fire often enough to keep the audio graph's lookahead window
 * full; the clicks themselves are placed on the audio hardware clock, so pump
 * jitter never reaches the ear. Running it inside a Worker keeps it clear of
 * main-thread jank and of the timer throttling browsers apply to pages that
 * are not in front.
 */

import { capabilities } from './platform.js';

const WORKER_SOURCE = `
  let id = null;
  onmessage = (event) => {
    clearInterval(id);
    id = null;
    if (event.data > 0) id = setInterval(() => postMessage(0), event.data);
  };
`;

export function createPump(onTick) {
  let worker = null;
  let timer = null;
  let workerUrl = null;

  if (capabilities.worker) {
    try {
      workerUrl = URL.createObjectURL(new Blob([WORKER_SOURCE], { type: 'text/javascript' }));
      worker = new Worker(workerUrl);
      worker.onmessage = onTick;
    } catch {
      worker = null;
    }
  }

  return {
    usingWorker: Boolean(worker),
    start(intervalMs) {
      this.stop();
      if (worker) worker.postMessage(intervalMs);
      else timer = setInterval(onTick, intervalMs);
    },
    stop() {
      if (worker) worker.postMessage(0);
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    },
    dispose() {
      this.stop();
      if (worker) worker.terminate();
      if (workerUrl) URL.revokeObjectURL(workerUrl);
      worker = null;
      workerUrl = null;
    },
  };
}
