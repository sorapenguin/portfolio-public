// 実績・マイルストーン・称号パネル — Kotlin AchievementFragment の移植
import {
  GameState, MILESTONES, ACHIEVEMENTS, TITLES,
  isMilestoneAchieved, achievementTimesEarned, achievementClaimable,
  earnedTitles,
} from '../types/GameState.js';

export class AchievementPanel {
  private getState: () => GameState;
  private onClaimMilestone:    ((id: string) => void) | null = null;
  private onClaimAchievement:  ((id: string) => void) | null = null;

  constructor(getState: () => GameState) { this.getState = getState; }

  setClaimMilestoneCallback(cb: (id: string) => void)   { this.onClaimMilestone = cb; }
  setClaimAchievementCallback(cb: (id: string) => void) { this.onClaimAchievement = cb; }

  init() {
    document.querySelectorAll<HTMLButtonElement>('.tab-btn[data-ach-tab]').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.dataset['achTab']!;
        document.querySelectorAll('.tab-btn[data-ach-tab]').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        ['milestone', 'achievement', 'title'].forEach(t => {
          const el = document.getElementById(`ach-tab-${t}`);
          if (el) el.style.display = t === tab ? '' : 'none';
        });
      });
    });
  }

  update(s: GameState) {
    this.renderMilestones(s);
    this.renderAchievements(s);
    this.renderTitles(s);
  }

  private renderMilestones(s: GameState) {
    const list = document.getElementById('milestone-list');
    if (!list) return;
    const claimed = new Set(s.milestonesClaimedIds);
    list.innerHTML = MILESTONES.map(m => {
      const achieved  = isMilestoneAchieved(s, m);
      const isClaimed = claimed.has(m.id);
      return `<div class="card" style="margin-bottom:6px">
        <div class="achieve-item" style="padding:0">
          <div class="achieve-info">
            <div class="achieve-title">${m.title}</div>
          </div>
          <div>
            <div class="achieve-reward">💎 ${m.rewardGems}</div>
            ${isClaimed
              ? '<span style="font-size:11px;color:var(--success)">✓ 受取済</span>'
              : achieved
              ? `<button class="btn btn-sm" data-claim-ms="${m.id}">受取</button>`
              : '<span style="font-size:11px;color:var(--text-dim)">未達成</span>'}
          </div>
        </div>
      </div>`;
    }).join('');
    list.querySelectorAll<HTMLButtonElement>('button[data-claim-ms]').forEach(btn => {
      btn.addEventListener('click', () => this.onClaimMilestone?.(btn.dataset['claimMs']!));
    });
  }

  private renderAchievements(s: GameState) {
    const list = document.getElementById('achievement-list');
    if (!list) return;
    list.innerHTML = ACHIEVEMENTS.map(def => {
      const earned    = achievementTimesEarned(s, def);
      const claimedN  = s.achievementsClaimed[def.id] ?? 0;
      const claimable = achievementClaimable(s, def);
      return `<div class="card" style="margin-bottom:6px">
        <div class="achieve-item" style="padding:0">
          <div class="achieve-info">
            <div class="achieve-title">${def.title}</div>
            <div class="achieve-desc">${def.description} | 達成: ${earned}回 / 受取済: ${claimedN}回</div>
          </div>
          <div>
            <div class="achieve-reward">💎 ${def.rewardGems}/回</div>
            ${claimable > 0
              ? `<button class="btn btn-sm" data-claim-ach="${def.id}">受取 ×${claimable}</button>`
              : '<span style="font-size:11px;color:var(--text-dim)">—</span>'}
          </div>
        </div>
      </div>`;
    }).join('');
    list.querySelectorAll<HTMLButtonElement>('button[data-claim-ach]').forEach(btn => {
      btn.addEventListener('click', () => this.onClaimAchievement?.(btn.dataset['claimAch']!));
    });
  }

  private renderTitles(s: GameState) {
    const list = document.getElementById('title-list');
    if (!list) return;
    const earned = earnedTitles(s);
    if (earned.length === 0) {
      list.innerHTML = '<div style="font-size:12px;color:var(--text-dim)">称号なし</div>';
      return;
    }
    list.innerHTML = earned.map(t => `
      <div class="achieve-item">
        <div class="achieve-info">
          <div class="achieve-title">【${t.name}】</div>
          <div class="achieve-desc">${t.description}</div>
        </div>
      </div>
    `).join('');
  }
}
