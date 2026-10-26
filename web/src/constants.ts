/**
 * How long one tile of walking takes while a direction key is held, and the length of the hero's ease, so the
 * glide lasts exactly as long as the step and the walk never stutters. This is the one place the walking pace is
 * set: the walker and the ease are both pure functions of it, so changing it changes the feel and nothing else.
 */
export const STEP_MS = 180;

/** How long the hero must stand still before the position is announced, so a walk is announced once. */
export const SETTLE_MS = 500;

/** The longest walk the server accepts in one call, and therefore how far ahead of it the page may go. */
export const MAX_WALK = 32;

/** How long the marker on a refused tile stays visible. */
export const BUMP_MS = 300;
