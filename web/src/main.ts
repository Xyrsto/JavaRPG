import './style.css';
import { createClient } from './api/client';
import { apiBase } from './config';
import { describeFailure, describeHealth } from './status';

const statusLine = document.getElementById('server-status');
const client = createClient({ base: apiBase });

/** Asks the server how it is and keeps asking, with growing pauses, until it answers. */
async function connect(attempt = 0): Promise<void> {
  const result = await client.health();
  if (result.ok) {
    if (statusLine) statusLine.textContent = describeHealth(result.value);
    return;
  }
  if (statusLine) statusLine.textContent = describeFailure(result.error);
  const pause = Math.min(10_000, 2_000 * (attempt + 1));
  window.setTimeout(() => void connect(attempt + 1), pause);
}

void connect();
