import type { WorldView } from '../api/client';

/** A tile position on the map. x counts columns from the left, y counts rows from the top, both from 0. */
export interface Point {
  readonly x: number;
  readonly y: number;
}

/** A character on the map. The quest id is carried for the dialogue work (#12); this feature only draws them. */
export interface WorldNpc {
  readonly id: string;
  readonly name: string;
  readonly questId: string;
  readonly x: number;
  readonly y: number;
}

/** A gate tile. Its id is the capital letter that also appears in the map rows. */
export interface WorldGate {
  readonly id: string;
  readonly x: number;
  readonly y: number;
}

/** The map as the client works with it. Everything here comes from the server; nothing is invented. */
export interface WorldModel {
  readonly id: string;
  readonly width: number;
  readonly height: number;
  readonly rows: readonly string[];
  readonly legend: Readonly<Record<string, string>>;
  readonly npcs: readonly WorldNpc[];
  readonly gates: readonly WorldGate[];
  readonly heroStart: Point;
}

/** Turns the server's world answer into the model the rest of the client reads. */
export function buildWorld(view: WorldView): WorldModel {
  return {
    id: view.id,
    width: view.width,
    height: view.height,
    rows: [...view.rows],
    legend: { ...view.legend },
    npcs: view.npcs.map((npc) => ({ id: npc.id, name: npc.name, questId: npc.questId, x: npc.x, y: npc.y })),
    gates: view.gates.map((gate) => ({ id: gate.id, x: gate.x, y: gate.y })),
    heroStart: { x: view.heroStart.x, y: view.heroStart.y },
  };
}

/** The map character at a position, or null when the position is outside the map. */
export function tileAt(world: WorldModel, x: number, y: number): string | null {
  if (x < 0 || y < 0 || x >= world.width || y >= world.height) {
    return null;
  }
  return world.rows[y]?.charAt(x) ?? null;
}

/** The character standing on a tile, if any. Characters block movement and are drawn on top of the ground. */
export function npcAt(world: WorldModel, x: number, y: number): WorldNpc | null {
  return world.npcs.find((npc) => npc.x === x && npc.y === y) ?? null;
}
