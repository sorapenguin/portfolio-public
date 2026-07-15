// Web Worker — オフライン収益計算 (タブ非アクティブ時もバックグラウンドで動作)
// Kotlin GameRepository.calculateOfflineResult() の Worker 版
import { GameState } from '../types/GameState.js';
import { calculateOfflineResult, OfflineResult } from '../game/OfflineCalc.js';

export interface OfflineWorkerRequest {
  state: GameState;
  serverEpochMs: number;
}

export interface OfflineWorkerResponse {
  result: OfflineResult | null;
}

self.onmessage = (e: MessageEvent<OfflineWorkerRequest>) => {
  const { state, serverEpochMs } = e.data;
  const result = calculateOfflineResult(state, serverEpochMs);
  const response: OfflineWorkerResponse = { result };
  self.postMessage(response);
};
