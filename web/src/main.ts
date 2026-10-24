import './style.css';
import { createClient } from './api/client';
import { apiBase } from './config';
import { describeFailure, describeHealth } from './status';

const statusLine = document.getElementById('server-status');
const client = createClient({ base: apiBase });
let shown = '';

/** Only touches the page when the words change, so the live region does not repeat itself while the server is away. */
function show(text: string): void {
  if (statusLine && text !== shown) {
    statusLine.textContent = text;
    shown = text;
  }
}

/** Asks the server how it is and keeps asking, with growing pauses, until it answers. */
async function connect(attempt = 0): Promise<void> {
  const result = await client.health();
  if (result.ok) {
    show(describeHealth(result.value));
    return;
  }
  show(describeFailure(result.error));
  const pause = Math.min(10_000, 2_000 * (attempt + 1));
  window.setTimeout(() => void connect(attempt + 1), pause);
}

void connect();
