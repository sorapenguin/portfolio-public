// 武器システム — Kotlin MainViewModel.processSecond() / mergeWeapons() の移植
import {
  GameState,
  totalWeapons, isAutoMergeActive, starGenLevel,
  MAX_WEAPON_STAR, AUTO_MERGE_DURATION_MS, AUTO_MERGE_COOLDOWN_MS,
  AUTO_MERGE_DAILY_FREE, autoMergeFreeUsedToday, autoMergeOnCooldown,
  autoMergeFreeRemainingToday, weaponSlotExpandCost,
  maxAutoDeleteLevel, isStarUnlocked,
  todayString,
} from '../types/GameState.js';

// ─── 武器生成 ─────────────────────────────────────────────────────────────────

export function generateWeaponStar(s: GameState): number {
  const unlocked = Object.entries(s.starGenLevels)
    .filter(([, lv]) => lv > 0)
    .map(([star, lv]) => ({ star: Number(star), lv }))
    .sort((a, b) => b.star - a.star);

  for (const { star, lv } of unlocked) {
    if (Math.random() < lv / 100) return star;
  }
  return 1;
}

// ─── 毎秒処理（武器生成 + 自動合成 + オートデリート） ────────────────────────

export interface SecondTickResult {
  state: GameState;
  playSec: boolean;
}

export function processSecond(s: GameState, secondCount: number): SecondTickResult {
  let next = s;
  let playSec = false;

  // 武器生成: スロット未満なら1個追加
  if (totalWeapons(next) < next.weaponSlots) {
    const star = generateWeaponStar(next);
    const w = { ...next.weapons };
    w[star] = (w[star] ?? 0) + 1;
    next = { ...next, weapons: w };
  }

  // 自動合成: スロット満タン時に起動中なら合成
  if (isAutoMergeActive(next) && totalWeapons(next) >= next.weaponSlots) {
    const weapons = { ...next.weapons };
    mergeWeaponsMap(weapons);
    next = { ...next, weapons };
  }

  // オートデリート
  if (next.autoDeleteLevel > 0) {
    const toDelete = Object.entries(next.weapons).filter(([lv]) => Number(lv) <= next.autoDeleteLevel);
    if (toDelete.length > 0) {
      let iron = 0, silver = 0, gold = 0;
      for (const [lv, cnt] of toDelete) {
        const level = Number(lv);
        if (level >= 1  && level <= 10) iron   += cnt;
        else if (level <= 20)           silver += cnt;
        else                            gold   += cnt;
      }
      const newWeapons = Object.fromEntries(
        Object.entries(next.weapons).filter(([lv]) => Number(lv) > next.autoDeleteLevel)
      ) as Record<number, number>;
      next = {
        ...next,
        weapons:        newWeapons,
        ironFragments:  next.ironFragments + iron,
        silverFragments: next.silverFragments + silver,
        goldFragments:  next.goldFragments + gold,
      };
    }
  }

  // プレイ時間 (毎60秒)
  if (secondCount % 60 === 0) {
    const today = todayString();
    const isToday = next.dailyDate === today;
    const playSecs = isToday ? next.dailyPlaySeconds : 0;
    if (playSecs < 300) {
      next = {
        ...next,
        dailyDate:       today,
        dailyPlaySeconds: Math.min(300, playSecs + 60),
      };
      playSec = true;
    }
  }

  return { state: next, playSec };
}

// ─── 手動合成 ─────────────────────────────────────────────────────────────────

export function mergeWeapons(s: GameState): GameState {
  const weapons = { ...s.weapons };
  mergeWeaponsMap(weapons);
  const today = todayString();
  const isToday = s.dailyDate === today;
  const mergeCount = isToday ? s.dailyMergeCount : 0;
  return { ...s, weapons, dailyDate: today, dailyMergeCount: mergeCount + 1 };
}

export function mergeWeaponsMap(weapons: Record<number, number>): void {
  let changed = true;
  while (changed) {
    changed = false;
    const levels = Object.keys(weapons).map(Number).sort((a, b) => a - b);
    for (const level of levels) {
      if (level >= MAX_WEAPON_STAR) continue;
      const count = weapons[level] ?? 0;
      if (count >= 2) {
        weapons[level] = count - 2;
        if (weapons[level] === 0) delete weapons[level];
        weapons[level + 1] = (weapons[level + 1] ?? 0) + 1;
        changed = true;
        break;
      }
    }
  }
}

// ─── スロット拡張 ─────────────────────────────────────────────────────────────

export function expandWeaponSlots(s: GameState): GameState | null {
  if (s.weaponSlots >= 30) return null;
  const cost = weaponSlotExpandCost(s);
  if (s.coins < cost) return null;
  return { ...s, coins: s.coins - cost, weaponSlots: s.weaponSlots + 1 };
}

// ─── 自動合成（無料） ─────────────────────────────────────────────────────────

export function activateAutoMergeFree(s: GameState): GameState | null {
  if (autoMergeOnCooldown(s)) return null;
  const today = todayString();
  const usedToday = autoMergeFreeUsedToday(s, today);
  if (usedToday >= AUTO_MERGE_DAILY_FREE) return null;
  const now = Date.now();
  return {
    ...s,
    autoMergeEndTime:       now + AUTO_MERGE_DURATION_MS,
    autoMergeFreeUsesToday: usedToday + 1,
    autoMergeFreeLastDate:  today,
    autoMergeLastUsedTime:  now,
  };
}

// ─── ジェム合成（1ジェム = 1分分の武器生成） ─────────────────────────────────

export function applyGemSynthesis(s: GameState, minutes: number): GameState {
  if (s.gems < minutes * 10) return s;
  const weapons = { ...s.weapons };
  for (let i = 0; i < minutes * 60; i++) {
    const star = generateWeaponStar(s);
    weapons[star] = (weapons[star] ?? 0) + 1;
  }
  // スロット超過分を最低★から削除
  let total = Object.values(weapons).reduce((a, b) => a + b, 0);
  const overflow = total - s.weaponSlots;
  if (overflow > 0) {
    let toRemove = overflow;
    for (const star of Object.keys(weapons).map(Number).sort((a, b) => a - b)) {
      if (toRemove <= 0) break;
      const cnt = weapons[star] ?? 0;
      const remove = Math.min(cnt, toRemove);
      if (cnt - remove === 0) delete weapons[star];
      else weapons[star] = cnt - remove;
      toRemove -= remove;
    }
  }
  return { ...s, gems: s.gems - minutes * 10, weapons };
}

// ─── オートデリート設定 ───────────────────────────────────────────────────────

export function setAutoDeleteLevel(s: GameState, level: number): GameState {
  const clamped = Math.max(0, Math.min(level, maxAutoDeleteLevel(s)));
  return { ...s, autoDeleteLevel: clamped };
}
