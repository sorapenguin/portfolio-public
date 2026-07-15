// ホームパネル（バトル画面） — Kotlin MainFragment の移植
import {
  GameState, formatNumber, totalAttack, enemyHp,
  isAttackBoosted, isCraftCoinBoosted, isCraftAttackBoosted, isAutoMergeActive,
  getDailyMissions, todayString, currentMilestone, isMilestoneAchieved,
  worldDisplayName, worldStageDisplay,
} from '../types/GameState.js';
import { BattleCanvas } from './BattleCanvas.js';
import { OfflineResult } from '../game/OfflineCalc.js';

export class HomePanel {
  private canvas: BattleCanvas;
  private accumulatedDamage = 0;
  private rafId = 0;
  private getState: () => GameState;
  private onCollectOffline: ((doubled: boolean) => void) | null = null;
  private onClaimMilestone: ((id: string) => void) | null = null;
  private onClaimDailyMission: ((id: string) => void) | null = null;

  constructor(getState: () => GameState) {
    this.getState = getState;
    this.canvas = new BattleCanvas('battle-canvas');
  }

  setOfflineCallback(cb: (doubled: boolean) => void) { this.onCollectOffline = cb; }
  setMilestoneCallback(cb: (id: string) => void)    { this.onClaimMilestone = cb; }
  setDailyMissionCallback(cb: (id: string) => void) { this.onClaimDailyMission = cb; }

  init() {
    document.getElementById('offline-collect-btn')?.addEventListener('click', () => {
      this.onCollectOffline?.(false);
    });

    // サブタブは home には不要
    // Canvas 連続レンダー開始
    this.startRender();
  }

  startRender() {
    cancelAnimationFrame(this.rafId);
    const loop = () => {
      this.canvas.render(this.getState(), this.accumulatedDamage);
      this.rafId = requestAnimationFrame(loop);
    };
    this.rafId = requestAnimationFrame(loop);
  }

  stopRender() {
    cancelAnimationFrame(this.rafId);
  }

  // バトルごとにダメージ積算（HP バー演出用）
  onBattleWin() {
    this.accumulatedDamage = 0;
    this.canvas.spawnAttackParticles(this.getState());
  }

  onBossFlash() {
    this.canvas.triggerBossFlash();
  }

  // バトルログ追加
  pushLog(entry: string, log: string[]) {
    const box = document.getElementById('battle-log-box');
    if (!box) return;
    box.innerHTML = log
      .slice(0, 20)
      .map(e => `<p>${e}</p>`)
      .join('');
    box.scrollTop = 0;
  }

  // オフライン報酬表示
  showOfflineReward(result: OfflineResult) {
    const card = document.getElementById('offline-reward-card');
    const text = document.getElementById('offline-reward-text');
    if (!card || !text) return;
    text.textContent = `${result.minutes}分オフライン（${result.minutesWon}分勝利）  コイン+${formatNumber(result.coins)}  ジェム+${result.gems}  Stage ${result.stageBefore} → ${result.stageAfter}`;
    card.style.display = 'block';
  }

  hideOfflineReward() {
    const card = document.getElementById('offline-reward-card');
    if (card) card.style.display = 'none';
  }

  update(s: GameState) {
    this.updateHeader(s);
    this.updateBoostBar(s);
    this.updateStats(s);
    this.updateDailyMissions(s);
    this.updateMilestone(s);
  }

  private updateHeader(s: GameState) {
    const worldStage = document.getElementById('world-stage');
    const coins      = document.getElementById('res-coins');
    const gems       = document.getElementById('res-gems');
    if (worldStage) {
      const disp = worldStageDisplay(s.currentWorld, s.stage);
      worldStage.textContent = `${worldDisplayName(s.currentWorld)}  Stage ${disp.toLocaleString()}`;
    }
    if (coins) coins.textContent = `💰 ${formatNumber(s.coins)}`;
    if (gems)  gems.textContent  = `💎 ${s.gems}`;
  }

  private updateBoostBar(s: GameState) {
    const bar = document.getElementById('boost-bar');
    if (!bar) return;
    const tags: string[] = [];
    if (isAttackBoosted(s))      tags.push('<span class="boost-tag boost-atk">攻撃×2</span>');
    if (isCraftCoinBoosted(s))   tags.push('<span class="boost-tag boost-coin">コイン×2</span>');
    if (isCraftAttackBoosted(s)) tags.push(`<span class="boost-tag boost-craft-atk">攻撃×${s.craftAttackBoostMultiplier.toFixed(1)}</span>`);
    if (isAutoMergeActive(s))    tags.push('<span class="boost-tag boost-auto-merge">自動合成中</span>');
    bar.innerHTML = tags.length > 0 ? tags.join('') : '<span style="font-size:11px;color:var(--text-dim)">なし</span>';
  }

  private updateStats(s: GameState) {
    const set = (id: string, v: string) => {
      const el = document.getElementById(id);
      if (el) el.textContent = v;
    };
    set('stat-atk',    formatNumber(totalAttack(s)));
    set('stat-hp',     formatNumber(enemyHp(s)));
    set('stat-stones', s.prestigeStones.toString());
    set('stat-tickets', s.skipTickets.toString());
  }

  private updateDailyMissions(s: GameState) {
    const list = document.getElementById('daily-missions-list');
    if (!list) return;
    const today    = todayString();
    const missions = getDailyMissions(s, today);
    list.innerHTML = missions.map(m => `
      <div class="achieve-item">
        <div class="achieve-info">
          <div class="achieve-title">${m.title}</div>
          <div class="achieve-desc">${m.progressText}</div>
          <div class="prog-bar-bg"><div class="prog-bar-fill" style="width:${Math.min(100, (m.progress / m.target) * 100).toFixed(1)}%"></div></div>
        </div>
        <div>
          <div class="achieve-reward">💎 ${m.reward}${m.rewardSkipTickets > 0 ? ` 券×${m.rewardSkipTickets}` : ''}</div>
          ${m.canClaim
            ? `<button class="btn btn-sm" data-mission="${m.id}">受取</button>`
            : m.claimed
            ? '<span style="font-size:11px;color:var(--success)">✓</span>'
            : ''}
        </div>
      </div>
    `).join('');

    list.querySelectorAll<HTMLButtonElement>('button[data-mission]').forEach(btn => {
      btn.addEventListener('click', () => this.onClaimDailyMission?.(btn.dataset['mission']!));
    });
  }

  private updateMilestone(s: GameState) {
    const card    = document.getElementById('milestone-card');
    const content = document.getElementById('milestone-content');
    if (!card || !content) return;
    const next = currentMilestone(s);
    if (!next) { card.style.display = 'none'; return; }
    const achieved = isMilestoneAchieved(s, next);
    card.style.display = 'block';
    content.innerHTML = `
      <div class="achieve-item">
        <div class="achieve-info">
          <div class="achieve-title">${next.title}</div>
        </div>
        <div>
          <div class="achieve-reward">💎 ${next.rewardGems}</div>
          ${achieved
            ? `<button class="btn btn-sm" id="claim-milestone-btn" data-id="${next.id}">受取</button>`
            : '<span style="font-size:11px;color:var(--text-dim)">未達成</span>'}
        </div>
      </div>
    `;
    document.getElementById('claim-milestone-btn')?.addEventListener('click', e => {
      const id = (e.currentTarget as HTMLButtonElement).dataset['id']!;
      this.onClaimMilestone?.(id);
    });
  }
}
