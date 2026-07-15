// セーブデータ管理 — Kotlin GameRepository + SyncScheduler の移植
// localStorage: キャッシュ (即時)
// GameAPI: 本体セーブ (5分ごと + タブ閉じ時)
import { GameState, createDefaultGameState } from '../types/GameState.js';
import { totalAttack } from '../types/GameState.js';
import {
  loadGameData, saveGameData, deserializeState, getServerTime, isLoggedIn,
} from '../api/GameApiClient.js';

const LOCAL_KEY      = 'sr_gamestate';
const API_INTERVAL_MS = 5 * 60 * 1000;

let lastApiSaveMs = 0;

// ─── 保存 ──────────────────────────────────────────────────────────────────────

export function saveLocal(s: GameState): void {
  try {
    localStorage.setItem(LOCAL_KEY, JSON.stringify({ ...s, lastSaveTime: Date.now() }));
  } catch { /* ストレージ満杯の場合は無視 */ }
}

export async function syncToApi(s: GameState): Promise<void> {
  if (!isLoggedIn()) return;
  const now = Date.now();
  if (now - lastApiSaveMs < API_INTERVAL_MS) return;
  const atk = totalAttack(s);
  const ok  = await saveGameData(s, atk);
  if (ok) lastApiSaveMs = now;
}

export async function flushToApi(s: GameState): Promise<void> {
  if (!isLoggedIn()) return;
  await saveGameData(s, totalAttack(s));
}

// ─── 起動時ロード ─────────────────────────────────────────────────────────────

export function loadLocal(): GameState {
  try {
    const json = localStorage.getItem(LOCAL_KEY);
    if (!json) return createDefaultGameState();
    const parsed = JSON.parse(json) as Partial<GameState>;
    // 全フィールドをデフォルトとマージ（フィールド追加時の後方互換）
    return { ...createDefaultGameState(), ...parsed };
  } catch {
    return createDefaultGameState();
  }
}

export async function loadAndMerge(): Promise<{ state: GameState; serverEpochMs: number | null }> {
  const local = loadLocal();

  if (!isLoggedIn()) {
    return { state: local, serverEpochMs: null };
  }

  const serverEpochMs = await getServerTime();
  const remote = await loadGameData();
  if (!remote?.stateJson) {
    return { state: local, serverEpochMs };
  }

  const server = deserializeState(remote.stateJson);
  if (!server) {
    return { state: local, serverEpochMs };
  }

  // より新しいセーブを採用
  if (server.lastSaveTime > local.lastSaveTime) {
    saveLocal(server);
    return { state: server, serverEpochMs };
  }
  return { state: local, serverEpochMs };
}

// ─── タブ閉じ時の保存 ─────────────────────────────────────────────────────────

export function registerBeforeUnload(getState: () => GameState): void {
  window.addEventListener('beforeunload', () => {
    const s = getState();
    saveLocal(s);
    // sendBeacon で非同期保存（レスポンス待ちなし）
    if (isLoggedIn()) {
      const body = JSON.stringify({
        stage: s.stage, coins: s.coins, gems: s.gems,
        totalAttack: totalAttack(s), weaponSlots: s.weaponSlots,
        maxMilestoneReached: s.maxMilestoneReached,
        totalEnemiesDefeated: s.totalEnemiesDefeated,
        totalCoinsEarned: s.totalCoinsEarned,
        stateJson: JSON.stringify(s),
      });
      const token = localStorage.getItem('sr_token');
      if (token) {
        navigator.sendBeacon(
          'https://game.sorapenguin.dev/idlegame/game-data',
          new Blob([body], { type: 'application/json' })
        );
      }
    }
  });
}
