// 設定パネル — Kotlin SettingsFragment の移植（スター扉含む）
import {
  GameState,
  canUseStarDoor, starDoorUsedToday, DAILY_STAR_DOOR_MAX,
  todayString, formatNumber,
} from '../types/GameState.js';
import { isLoggedIn, getUsername } from '../api/GameApiClient.js';

export class SettingsPanel {
  private getState: () => GameState;
  private onUseStarDoor:    (() => void)   | null = null;
  private onCloudSave:      (() => void)   | null = null;
  private onLogout:         (() => void)   | null = null;
  private onDeleteAccount:  (() => void)   | null = null;
  private onResetGame:      (() => void)   | null = null;

  constructor(getState: () => GameState) { this.getState = getState; }

  setStarDoorCallback(cb: () => void)       { this.onUseStarDoor = cb; }
  setCloudSaveCallback(cb: () => void)      { this.onCloudSave = cb; }
  setLogoutCallback(cb: () => void)         { this.onLogout = cb; }
  setDeleteAccountCallback(cb: () => void)  { this.onDeleteAccount = cb; }
  setResetGameCallback(cb: () => void)      { this.onResetGame = cb; }

  init() {
    document.getElementById('star-door-btn')?.addEventListener('click', () => this.onUseStarDoor?.());
    document.getElementById('cloud-save-btn')?.addEventListener('click', () => this.onCloudSave?.());
    document.getElementById('logout-btn')?.addEventListener('click', () => this.onLogout?.());
    document.getElementById('delete-account-btn')?.addEventListener('click', () => this.onDeleteAccount?.());
    document.getElementById('reset-game-btn')?.addEventListener('click', () => this.onResetGame?.());
  }

  update(s: GameState) {
    this.renderAccount();
    this.renderStarDoor(s);
    this.renderStats(s);
  }

  private renderAccount() {
    const info    = document.getElementById('account-info');
    const logoutBtn = document.getElementById('logout-btn') as HTMLButtonElement | null;
    const deleteBtn = document.getElementById('delete-account-btn') as HTMLButtonElement | null;
    const cloudBtn  = document.getElementById('cloud-save-btn') as HTMLButtonElement | null;

    if (isLoggedIn()) {
      const user = getUsername() ?? '?';
      if (info)      info.textContent      = `ログイン中: ${user}`;
      if (logoutBtn) logoutBtn.style.display = '';
      if (deleteBtn) deleteBtn.style.display = '';
      if (cloudBtn)  cloudBtn.textContent = '今すぐ同期';
    } else {
      if (info)      info.textContent      = 'ゲストプレイ中（ログインでクロスセーブ有効）';
      if (logoutBtn) logoutBtn.style.display = 'none';
      if (deleteBtn) deleteBtn.style.display = 'none';
      if (cloudBtn)  cloudBtn.textContent = 'ログイン / 新規登録';
    }
  }

  private renderStarDoor(s: GameState) {
    const today     = todayString();
    const used      = starDoorUsedToday(s, today);
    const remaining = DAILY_STAR_DOOR_MAX - used;
    const ticketsEl  = document.getElementById('settings-tickets');
    const remainEl   = document.getElementById('star-door-remaining');
    const btn        = document.getElementById('star-door-btn') as HTMLButtonElement | null;
    if (ticketsEl)  ticketsEl.textContent  = s.skipTickets.toString();
    if (remainEl)   remainEl.textContent   = remaining.toString();
    if (btn) btn.disabled = !canUseStarDoor(s, today);
  }

  private renderStats(s: GameState) {
    const set = (id: string, v: string) => {
      const el = document.getElementById(id);
      if (el) el.textContent = v;
    };
    set('stat-total-kills',  s.totalEnemiesDefeated.toLocaleString());
    set('stat-max-stage',    s.maxBossStageCleared.toLocaleString());
    set('stat-total-coins',  formatNumber(s.totalCoinsEarned));
    set('stat-login-streak', `${s.loginStreak}日`);
  }
}
