// GameAPI リクエスト/レスポンス型
// エンドポイント: https://game.sorapenguin.dev/idlegame

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

// ─── Auth ──────────────────────────────────────────────────────────────────

export interface RegisterRequest {
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthData {
  token: string;
  userId: number;
  username: string;
}

// ─── Game Data ─────────────────────────────────────────────────────────────

export interface GameDataRequest {
  stage: number;
  coins: number;
  gems: number;
  totalAttack: number;
  weaponSlots: number;
  maxMilestoneReached: number;
  totalEnemiesDefeated: number;
  totalCoinsEarned: number;
  stateJson: string;
}

export interface GameDataResponse {
  id: number;
  userId: number;
  stage: number;
  coins: number;
  gems: number;
  totalAttack: number;
  weaponSlots: number;
  stateJson: string | null;
  lastSavedAt: string;
}

// ─── Score ────────────────────────────────────────────────────────────────

export interface ScoreData {
  userId: number;
  totalKills: number;
  maxStageReached: number;
  totalCoinsEarned: number;
  updatedAt: string;
}

// ─── Time ─────────────────────────────────────────────────────────────────

// GET /idlegame/time → data: epoch ms (number)
export type TimeResponse = ApiResponse<number>;
