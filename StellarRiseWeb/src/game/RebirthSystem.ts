// 転生・ワールドシステム — Kotlin MainViewModel.rebirth() / rebirthSkip() / enterWorld() の移植
import {
  GameState, WorldWeaponState,
  canRebirth, rebirthUnlocked, rebirthMaxSkipTarget, rebirthSkipCost,
  worldStartStage, worldOf, MAX_WORLD,
} from '../types/GameState.js';

// ─── 転生 ─────────────────────────────────────────────────────────────────────

export function rebirth(s: GameState): GameState | null {
  if (!canRebirth(s)) return null;
  const worldStart = worldStartStage(s.currentWorld);
  const startStage = s.maxBossStageCleared >= 10000
    ? Math.max(s.maxBossStageCleared + 1, worldStart)
    : worldStart;
  const startLastCleared = s.currentWorld > 1
    ? worldStart
    : s.maxBossStageCleared >= 10000
      ? s.maxBossStageCleared
      : 0;

  return {
    ...s,
    stage:                startStage,
    coins:                0,
    lastClearedBossStage: startLastCleared,
    autoDeleteLevel:      s.currentWorld > 1 ? 0 : s.autoDeleteLevel,
  };
}

// ─── 転生スキップ ─────────────────────────────────────────────────────────────

export function performRebirthSkip(s: GameState): GameState | null {
  if (!rebirthUnlocked(s)) return null;
  const target = rebirthMaxSkipTarget(s);
  if (target <= s.stage || target <= 0) return null;
  const cost = rebirthSkipCost(s, target);
  if (s.gems < cost) return null;
  return {
    ...s,
    stage:                target,
    gems:                 s.gems - cost,
    lastClearedBossStage: Math.max(s.lastClearedBossStage, target),
  };
}

// ─── ワールド移行（自動） ─────────────────────────────────────────────────────

export function enterWorld(s: GameState, newWorld: number, arrivingStage?: number): GameState {
  if (newWorld === s.currentWorld) return s;

  const savedState: WorldWeaponState = {
    weapons:              s.weapons,
    ironFragments:        s.ironFragments,
    silverFragments:      s.silverFragments,
    goldFragments:        s.goldFragments,
    stage:                s.stage,
    lastClearedBossStage: s.lastClearedBossStage,
    coinAttackLevel:      s.coinAttackLevel,
  };

  const newWorldStates = { ...s.worldWeaponStates, [s.currentWorld]: savedState };
  const targetState    = s.worldWeaponStates[newWorld];
  const newStage       = targetState?.stage ?? arrivingStage ?? worldStartStage(newWorld);

  return {
    ...s,
    currentWorld:         newWorld,
    worldWeaponStates:    newWorldStates,
    weapons:              targetState?.weapons ?? {},
    ironFragments:        targetState?.ironFragments ?? 0,
    silverFragments:      targetState?.silverFragments ?? 0,
    goldFragments:        targetState?.goldFragments ?? 0,
    stage:                newStage,
    lastClearedBossStage: targetState?.lastClearedBossStage ?? worldStartStage(newWorld),
    coinAttackLevel:      targetState?.coinAttackLevel ?? 0,
    autoDeleteLevel:      0,
  };
}

// ─── 手動ワールド切替 ─────────────────────────────────────────────────────────

export function switchWorld(s: GameState, targetWorld: number): GameState | null {
  if (targetWorld === s.currentWorld) return null;
  if (targetWorld < 1 || targetWorld > MAX_WORLD) return null;
  if (!(targetWorld in s.worldWeaponStates)) return null;
  return enterWorld(s, targetWorld);
}

// ─── ワールド移行チェック（バトル後に呼ぶ） ────────────────────────────────────

export function checkWorldTransition(
  s: GameState, stageAfter: number
): { newState: GameState; newWorldId: number | null } {
  const targetWorld = worldOf(stageAfter);
  if (targetWorld <= s.currentWorld || targetWorld > MAX_WORLD) {
    return { newState: s, newWorldId: null };
  }
  const isFirstVisit = !(targetWorld in s.worldWeaponStates);
  const newState = enterWorld(s, targetWorld, stageAfter);
  return { newState, newWorldId: isFirstVisit ? targetWorld : null };
}
