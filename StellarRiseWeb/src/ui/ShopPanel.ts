// ショップパネル — Web版は広告なし。スター扉のみ。
// 詳細は DIFFERENCES.md 参照
import {
  GameState, canUseStarDoor, starDoorUsedToday, DAILY_STAR_DOOR_MAX,
  skipTicketsAfterAdd, todayString, formatNumber,
} from '../types/GameState.js';

// ShopPanel は SettingsPanel に統合（スター扉）
// このファイルは将来のIAP拡張用プレースホルダー
export class ShopPanel {
  private getState: () => GameState;

  constructor(getState: () => GameState) { this.getState = getState; }

  init() {}
  update(_s: GameState) {}
}
