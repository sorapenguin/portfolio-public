// 強化パネル — Kotlin EnhancementFragment の移植
import {
  GameState,
  isStarUnlocked, canUnlockStar, starGenLevel, starUpgradeCost, starUnlockCost,
  coinAttackBonus, coinAttackNextCost, coinAttackNextBonus,
  prestigeUpgradeLevel, prestigeUpgradeMax, prestigeUpgradeCost,
  prestigeAttackMultiplier, prestigeCoinMultiplier, prestigeOfflineHours, prestigeGemDropRate,
  PRESTIGE_ATTACK, PRESTIGE_COIN, PRESTIGE_OFFLINE, PRESTIGE_GEM_DROP,
  MAX_WEAPON_STAR, COIN_ATTACK_MAX_LEVEL,
  canRebirth, rebirthUnlocked, rebirthThreshold, rebirthMaxSkipTarget, rebirthSkipCost,
  worldDisplayName,
  formatNumber,
} from '../types/GameState.js';

export class EnhancementPanel {
  private getState: () => GameState;
  private onUnlockStar:         ((star: number) => void)  | null = null;
  private onUpgradeStar:        ((star: number) => void)  | null = null;
  private onCoinAttack:         (() => void)              | null = null;
  private onPrestigeUpgrade:    ((id: number) => void)    | null = null;
  private onRebirth:            (() => void)              | null = null;
  private onRebirthSkip:        (() => void)              | null = null;
  private onSwitchWorld:        ((w: number) => void)     | null = null;

  constructor(getState: () => GameState) { this.getState = getState; }

  setUnlockStarCallback(cb: (star: number) => void)  { this.onUnlockStar = cb; }
  setUpgradeStarCallback(cb: (star: number) => void) { this.onUpgradeStar = cb; }
  setCoinAttackCallback(cb: () => void)              { this.onCoinAttack = cb; }
  setPrestigeUpgradeCallback(cb: (id: number) => void) { this.onPrestigeUpgrade = cb; }
  setRebirthCallback(cb: () => void)                 { this.onRebirth = cb; }
  setRebirthSkipCallback(cb: () => void)             { this.onRebirthSkip = cb; }
  setSwitchWorldCallback(cb: (w: number) => void)    { this.onSwitchWorld = cb; }

  init() {
    document.querySelectorAll<HTMLButtonElement>('.tab-btn[data-enhance-tab]').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.dataset['enhanceTab']!;
        document.querySelectorAll('.tab-btn[data-enhance-tab]').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        ['star', 'prestige', 'rebirth'].forEach(t => {
          const el = document.getElementById(`enhance-tab-${t}`);
          if (el) el.style.display = t === tab ? '' : 'none';
        });
      });
    });

    document.getElementById('coin-atk-btn')?.addEventListener('click', () => this.onCoinAttack?.());
    document.getElementById('rebirth-btn')?.addEventListener('click', () => this.onRebirth?.());
    document.getElementById('rebirth-skip-btn')?.addEventListener('click', () => this.onRebirthSkip?.());
  }

  update(s: GameState) {
    this.renderCoinAttack(s);
    this.renderStarGenList(s);
    this.renderPrestigeUpgrades(s);
    this.renderRebirthTab(s);
  }

  private renderCoinAttack(s: GameState) {
    const el = (id: string) => document.getElementById(id);
    const lvEl = el('coin-atk-level');
    const bonusEl = el('coin-atk-bonus');
    const costEl  = el('coin-atk-cost');
    const btn     = el('coin-atk-btn') as HTMLButtonElement | null;
    if (lvEl)    lvEl.textContent    = s.coinAttackLevel.toString();
    if (bonusEl) bonusEl.textContent = formatNumber(coinAttackBonus(s));
    const maxed = s.coinAttackLevel >= COIN_ATTACK_MAX_LEVEL;
    const cost  = coinAttackNextCost(s);
    if (costEl)  costEl.textContent  = maxed ? 'MAX' : `コスト: ${formatNumber(cost)}`;
    if (btn) btn.disabled = maxed || s.coins < cost;
  }

  private renderStarGenList(s: GameState) {
    const list = document.getElementById('star-gen-list');
    if (!list) return;
    // 1〜MAX_WEAPON_STAR まで。解放済み + 解放可能のみ表示（上限は現在の最高+5か30まで）
    const maxShow = Math.min(MAX_WEAPON_STAR, Math.max(5, ...Object.keys(s.starGenLevels).map(Number)) + 5);
    let html = '<div class="card-title">星生成確率アップグレード</div>';
    for (let star = 1; star <= maxShow; star++) {
      const unlocked = isStarUnlocked(s, star);
      const canUnlock = canUnlockStar(s, star);
      const lv = starGenLevel(s, star);
      if (!unlocked && !canUnlock) continue;
      if (unlocked) {
        const maxLv = 50;
        const cost = starUpgradeCost(star, lv);
        const canUp = lv < maxLv && s.gems >= cost;
        html += `<div class="star-upgrade-row">
          <span class="star-name">★${star}</span>
          <span class="star-level">Lv ${lv}/50</span>
          <button class="btn btn-sm${canUp ? '' : ''}" data-upstar="${star}" ${lv >= maxLv ? 'disabled' : s.gems < cost ? 'disabled style="opacity:0.5"' : ''}>
            ${lv >= maxLv ? 'MAX' : `強化 ${cost}💎`}
          </button>
        </div>`;
      } else {
        const cost = starUnlockCost(star);
        html += `<div class="star-upgrade-row">
          <span class="star-name">★${star}</span>
          <span class="star-level" style="color:var(--text-dim)">未解放</span>
          <button class="btn btn-sm btn-outline" data-unlockstar="${star}" ${s.gems < cost ? 'disabled' : ''}>
            解放 ${cost}💎
          </button>
        </div>`;
      }
    }
    list.innerHTML = html;
    list.querySelectorAll<HTMLButtonElement>('button[data-upstar]').forEach(btn => {
      btn.addEventListener('click', () => this.onUpgradeStar?.(Number(btn.dataset['upstar'])));
    });
    list.querySelectorAll<HTMLButtonElement>('button[data-unlockstar]').forEach(btn => {
      btn.addEventListener('click', () => this.onUnlockStar?.(Number(btn.dataset['unlockstar'])));
    });
  }

  private renderPrestigeUpgrades(s: GameState) {
    const stonesEl = document.getElementById('prestige-stones-val');
    const list     = document.getElementById('prestige-upgrade-list');
    if (stonesEl) stonesEl.textContent = s.prestigeStones.toString();
    if (!list) return;

    const upgrades = [
      { id: PRESTIGE_ATTACK,   name: '攻撃力強化',  desc: '+5%/Lv',  unit: '%', value: (l: number) => `+${(l * 5)}%` },
      { id: PRESTIGE_COIN,     name: 'コイン強化',   desc: '+10%/Lv', unit: '%', value: (l: number) => `+${(l * 10)}%` },
      { id: PRESTIGE_OFFLINE,  name: 'オフライン時間', desc: '+1h/Lv', unit: 'h', value: (l: number) => `${8 + l}h` },
      { id: PRESTIGE_GEM_DROP, name: 'ジェムドロップ', desc: '+1%/Lv', unit: '%', value: (l: number) => `${(5 + l)}%` },
    ];

    list.innerHTML = upgrades.map(u => {
      const lv   = prestigeUpgradeLevel(s, u.id);
      const max  = prestigeUpgradeMax(u.id);
      const cost = prestigeUpgradeCost(s, u.id);
      const can  = lv < max && s.prestigeStones >= cost;
      return `<div class="prestige-row">
        <div style="flex:1">
          <div>${u.name}</div>
          <div style="font-size:11px;color:var(--text-dim)">${u.desc} → 現在: ${u.value(lv)}</div>
        </div>
        <span class="prestige-level">Lv ${lv}/${max}</span>
        <button class="btn btn-sm" data-prestige="${u.id}" ${lv >= max ? 'disabled' : !can ? 'disabled style="opacity:0.5"' : ''}>
          ${lv >= max ? 'MAX' : `${cost}輝石`}
        </button>
      </div>`;
    }).join('');

    list.querySelectorAll<HTMLButtonElement>('button[data-prestige]').forEach(btn => {
      btn.addEventListener('click', () => this.onPrestigeUpgrade?.(Number(btn.dataset['prestige'])));
    });
  }

  private renderRebirthTab(s: GameState) {
    const info       = document.getElementById('rebirth-info');
    const rebirthBtn = document.getElementById('rebirth-btn') as HTMLButtonElement | null;
    const skipBtn    = document.getElementById('rebirth-skip-btn') as HTMLButtonElement | null;

    if (info) {
      if (!rebirthUnlocked(s)) {
        info.textContent = `転生は Stage1000 の大ボス撃破で解放されます。（現在の最高ボスステージ: ${s.maxBossStageCleared}）`;
      } else {
        const threshold = rebirthThreshold(s);
        info.innerHTML = `転生条件: Stage ${threshold.toLocaleString()} 到達<br>現在: ${s.stage.toLocaleString()}<br><br>転生するとステージ・コインをリセットして再挑戦できます。武器・輝石・アップグレードは引き継がれます。`;
      }
    }
    if (rebirthBtn) rebirthBtn.disabled = !canRebirth(s);
    if (skipBtn) {
      const target = rebirthMaxSkipTarget(s);
      const cost   = target > 0 ? rebirthSkipCost(s, target) : 0;
      skipBtn.disabled = !rebirthUnlocked(s) || target <= 0;
      skipBtn.textContent = target > 0 ? `スキップ (${target.toLocaleString()}まで / ${cost}💎)` : 'スキップ不可';
    }

    // ワールド切替リスト
    const wlist = document.getElementById('world-switch-list');
    if (!wlist) return;
    const savedWorlds = Object.keys(s.worldWeaponStates).map(Number);
    if (savedWorlds.length === 0) {
      wlist.innerHTML = '<div style="font-size:12px;color:var(--text-dim)">訪問済みワールドがありません</div>';
      return;
    }
    wlist.innerHTML = savedWorlds.map(w => `
      <div class="setting-row">
        <span>${worldDisplayName(w)}</span>
        <button class="btn btn-sm btn-outline" data-world="${w}" ${w === s.currentWorld ? 'disabled' : ''}>
          ${w === s.currentWorld ? '現在' : '移動'}
        </button>
      </div>
    `).join('');
    wlist.querySelectorAll<HTMLButtonElement>('button[data-world]').forEach(btn => {
      btn.addEventListener('click', () => this.onSwitchWorld?.(Number(btn.dataset['world'])));
    });
  }
}
