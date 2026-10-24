/** Where the game server is. Empty means the same address as the page (the normal case, no cross-origin setup). */
export const apiBase: string = import.meta.env.VITE_API_BASE ?? '';
