// 武器パネル — Kotlin WeaponFragment の移植
import {
  GameState, starAttack, totalWeapons, weaponSlotExpandCost,
  isAutoMergeActive, autoMergeFreeRemainingToday, autoMergeOnCooldown,
  autoMergeCooldownRemainingMs,
  maxAutoDeleteLevel, formatNumber, todayString, weaponStarColor, worldInLoop,
} from '../types/GameState.js';

type Callback = () => void;

function autoMergeRemainingMs(s: GameState): number {
  return Math.max(0, s.autoMergeEndTime - Date.now());
}

export class WeaponPanel {
  private getState: () => GameState;
  private onMerge:           Callback | null = null;
  private onExpandSlots:     Callback | null = null;
  private onAutoMergeFree:   Callback | null = null;
  private onAutoDeleteLevel: ((lv: number) => void) | null = null;

  constructor(getState: () => GameState) {
    this.getState = getState;
  }

  setMergeCallback(cb: Callback)          { this.onMerge = cb; }
  setExpandSlotsCallback(cb: Callback)    { this.onExpandSlots = cb; }
  setAutoMergeFreeCallback(cb: Callback)  { this.onAutoMergeFree = cb; }
  setAutoDeleteLevelCallback(cb: (lv: number) => void) { this.onAutoDeleteLevel = cb; }

  init() {
    // サブタブ切り替え
    document.querySelectorAll<HTMLButtonElement>('.tab-btn[data-weapon-tab]').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.dataset['weaponTab']!;
        document.querySelectorAll('.tab-btn[data-weapon-tab]').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        ['slots', 'merge', 'auto'].forEach(t => {
          const el = document.getElementById(`weapon-tab-${t}`);
          if (el) el.style.display = t === tab ? '' : 'none';
        });
      });
    });

    document.getElementById('expand-slots-btn')?.addEventListener('click', () => this.onExpandSlots?.());
    document.getElementById('merge-once-btn')?.addEventListener('click', () => this.onMerge?.());
    document.getElementById('auto-merge-free-btn')?.addEventListener('click', () => this.onAutoMergeFree?.());
    document.getElementById('auto-delete-off-btn')?.addEventListener('click', () => this.onAutoDeleteLevel?.(0));
  }

  update(s: GameState) {
    this.renderWeaponGrid(s);
    this.renderMergeTab(s);
    this.renderAutoTab(s);
  }

  private renderWeaponGrid(s: GameState) {
    const grid = document.getElementById('weapon-grid');
    const count = document.getElementById('weapon-slot-count');
    const cost  = document.getElementById('expand-slots-cost');
    if (!grid) return;

    const used = totalWeapons(s);
    if (count) count.textContent = `${used}/${s.weaponSlots}`;
    if (cost)  cost.textContent  = s.weaponSlots < 30 ? `コスト: ${formatNumber(weaponSlotExpandCost(s))}` : '最大';

    const color = weaponStarColor(worldInLoop(s.currentWorld));
    const slots: string[] = [];

    // 武器を star 順に展開
    const entries: Array<{ star: number; atk: number }> = [];
    for (const [lv, cnt] of Object.entries(s.weapons)) {
      for (let i = 0; i < cnt; i++) entries.push({ star: Number(lv), atk: starAttack(Number(lv)) });
    }
    entries.sort((a, b) => b.star - a.star);

    for (let i = 0; i < s.weaponSlots; i++) {
      const w = entries[i];
      if (w) {
        slots.push(`<div class="weapon-slot">
          <span class="star" style="color:${color}">★${w.star}</span>
          <span class="atk">${formatNumber(w.atk)}</span>
        </div>`);
      } else {
        slots.push('<div class="weapon-slot empty"><span style="color:#555">空</span></div>');
      }
    }
    grid.innerHTML = slots.join('');
  }

  private renderMergeTab(s: GameState) {
    const status = document.getElementById('auto-merge-status');
    const freeBtn = document.getElementById('auto-merge-free-btn') as HTMLButtonElement | null;
    if (!status) return;

    const today   = todayString();
    const active  = isAutoMergeActive(s);
    const cooldown = autoMergeOnCooldown(s);
    const freeLeft = autoMergeFreeRemainingToday(s, today);

    if (active) {
      const rem = Math.ceil(autoMergeRemainingMs(s) / 1000);
      status.textContent = `起動中 (残り${rem}秒)`;
      status.style.color = 'var(--secondary)';
    } else if (cooldown) {
      const rem = Math.ceil(autoMergeCooldownRemainingMs(s) / 1000);
      status.textContent = `クールダウン中 (残り${rem}秒)`;
      status.style.color = 'var(--text-dim)';
    } else {
      status.textContent = `無料残り: ${freeLeft}回 / ${3}回`;
      status.style.color = 'var(--text)';
    }
    if (freeBtn) freeBtn.disabled = active || cooldown || freeLeft === 0;
  }

  private renderAutoTab(s: GameState) {
    const val  = document.getElementById('auto-delete-value');
    const btns = document.getElementById('auto-delete-level-btns');
    const iron   = document.getElementById('frags-iron');
    const silver = document.getElementById('frags-silver');
    const gold   = document.getElementById('frags-gold');

    if (val)    val.textContent    = s.autoDeleteLevel === 0 ? 'オフ' : `★${s.autoDeleteLevel}以下`;
    if (iron)   iron.textContent   = s.ironFragments.toString();
    if (silver) silver.textContent = s.silverFragments.toString();
    if (gold)   gold.textContent   = s.goldFragments.toString();

    if (!btns) return;
    const max = maxAutoDeleteLevel(s);
    if (max === 0) { btns.innerHTML = ''; return; }
    btns.innerHTML = Array.from({ length: max }, (_, i) => i + 1)
      .map(lv => `<button class="btn btn-sm${s.autoDeleteLevel === lv ? '' : ' btn-outline'}" data-del-lv="${lv}">★${lv}</button>`)
      .join(' ');
    btns.querySelectorAll<HTMLButtonElement>('button[data-del-lv]').forEach(btn => {
      btn.addEventListener('click', () => this.onAutoDeleteLevel?.(Number(btn.dataset['delLv'])));
    });
  }
}
