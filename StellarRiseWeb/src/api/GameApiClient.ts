// GameAPI HTTP クライアント — Kotlin ApiRepository + TokenManager の移植
// エンドポイント: https://game.sorapenguin.dev/idlegame
import {
  ApiResponse, AuthData, GameDataRequest, GameDataResponse,
  LoginRequest, RegisterRequest, ScoreData,
} from '../types/ApiTypes.js';
import { GameState } from '../types/GameState.js';

const API_BASE = 'https://game.sorapenguin.dev/idlegame';

const TOKEN_KEY    = 'sr_token';
const USERNAME_KEY = 'sr_username';

// ─── Token management (localStorage) ──────────────────────────────────────────

export function saveAuth(token: string, username: string): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getUsername(): string | null {
  return localStorage.getItem(USERNAME_KEY);
}

export function isLoggedIn(): boolean {
  return getToken() !== null;
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
}

// ─── HTTP helpers ─────────────────────────────────────────────────────────────

async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<ApiResponse<T> | null> {
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      headers: { 'Content-Type': 'application/json', ...options.headers },
      ...options,
    });
    if (!res.ok) return null;
    return (await res.json()) as ApiResponse<T>;
  } catch {
    return null;
  }
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// ─── Auth endpoints ───────────────────────────────────────────────────────────

export async function register(password: string): Promise<AuthData | null> {
  const body: RegisterRequest = { password };
  const res = await apiFetch<AuthData>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res?.success && res.data) {
    saveAuth(res.data.token, res.data.username);
    return res.data;
  }
  return null;
}

export async function login(username: string, password: string): Promise<AuthData | null> {
  const body: LoginRequest = { username, password };
  const res = await apiFetch<AuthData>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res?.success && res.data) {
    saveAuth(res.data.token, res.data.username);
    return res.data;
  }
  return null;
}

export async function deleteAccount(): Promise<boolean> {
  const res = await apiFetch<null>('/auth/account', {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (res?.success) {
    clearAuth();
    return true;
  }
  return false;
}

// ─── Game data endpoints ──────────────────────────────────────────────────────

export async function loadGameData(): Promise<GameDataResponse | null> {
  const res = await apiFetch<GameDataResponse>('/game-data', {
    headers: authHeaders(),
  });
  return res?.success ? res.data : null;
}

export async function saveGameData(s: GameState, totalAttackVal: number): Promise<boolean> {
  const body: GameDataRequest = {
    stage:                s.stage,
    coins:                s.coins,
    gems:                 s.gems,
    totalAttack:          totalAttackVal,
    weaponSlots:          s.weaponSlots,
    maxMilestoneReached:  s.maxMilestoneReached,
    totalEnemiesDefeated: s.totalEnemiesDefeated,
    totalCoinsEarned:     s.totalCoinsEarned,
    stateJson:            JSON.stringify(s),
  };
  const res = await apiFetch<GameDataResponse>('/game-data', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  });
  return res?.success ?? false;
}

export function deserializeState(json: string): GameState | null {
  try {
    const parsed = JSON.parse(json) as Partial<GameState>;
    // 必須フィールドチェック
    if (typeof parsed.stage !== 'number') return null;
    return parsed as GameState;
  } catch {
    return null;
  }
}

// ─── Server time ──────────────────────────────────────────────────────────────

export async function getServerTime(): Promise<number | null> {
  const res = await apiFetch<number>('/time');
  return res?.success ? res.data : null;
}

// ─── Score ─────────────────────────────────────────────────────────────────────

export async function getScore(): Promise<ScoreData | null> {
  const res = await apiFetch<ScoreData>('/score', { headers: authHeaders() });
  return res?.success ? res.data : null;
}
