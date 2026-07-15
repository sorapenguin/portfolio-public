// Kotlin → TypeScript 移植
// Long       → number  (JS number は 53bit 整数まで安全 ≈ 9 quadrillion)
// Map<Int,Int> → Record<number,number>
// Set<String>  → string[]  (JSON互換のため配列。内部では Set<string> に変換して使う)
// Float        → number

export enum BossType {
  MINOR  = 'MINOR',
  MAJOR  = 'MAJOR',
  LEGEND = 'LEGEND',
}

export enum GameMode {
  EASY   = 'EASY',
  NORMAL = 'NORMAL',
  HARD_1 = 'HARD_1',
  HARD_2 = 'HARD_2',
}

export interface WorldWeaponState {
  weapons: Record<number, number>;
  ironFragments: number;
  silverFragments: number;
  goldFragments: number;
  stage: number;
  lastClearedBossStage: number;
  coinAttackLevel: number;
}

export interface GameState {
  coins: number;
  gems: number;
  lastSaveTime: number;
  weapons: Record<number, number>;
  weaponSlots: number;
  stage: number;
  autoDeleteLevel: number;
  starGenLevels: Record<number, number>;
  coinAttackLevel: number;
  prestigeStones: number;
  prestigeUpgrades: Record<number, number>;
  maxMilestoneReached: number;
  achievementsClaimed: Record<string, number>;
  totalEnemiesDefeated: number;
  totalCoinsEarned: number;
  gemAdWatchedToday: number;
  gemAdLastDate: string;
  lastGemAdTime: number;
  lastCoinAdTime: number;
  attackBoostEndTime: number;
  penaltyShieldActive: boolean;
  lastAttackBoostAdTime: number;
  lastShieldAdTime: number;
  autoMergeEndTime: number;
  autoMergeFreeUsesToday: number;
  autoMergeFreeLastDate: string;
  autoMergeLastUsedTime: number;
  tutorialShown: boolean;
  ironFragments: number;
  silverFragments: number;
  goldFragments: number;
  discoveredRecipeIds: string[];
  craftCoinBoostEndTime: number;
  craftAttackBoostEndTime: number;
  craftAttackBoostMultiplier: number;
  magicStoneFragments: number;
  ancientDragonCore: number;
  starShatterCrystal: number;
  lastClearedBossStage: number;
  maxBossStageCleared: number;
  dailyDate: string;
  dailyMergeCount: number;
  dailyPlaySeconds: number;
  dailyAdWatchCount: number;
  dailyMissionsClaimed: string;
  skipTickets: number;
  loginStreak: number;
  lastLoginDate: string;
  dailyStarDoorCount: number;
  lastStarDoorDate: string;
  milestonesClaimedIds: string[];
  dailyBattleWinCount: number;
  currentWorld: number;
  worldWeaponStates: Record<number, WorldWeaponState>;
}

// ─── Constants (= Kotlin companion object) ───────────────────────────────────

export const ENEMIES_PER_MINUTE     = 60;
export const STAGE_PENALTY          = 200;
export const GEM_DROP_CHANCE        = 0.05;
export const COIN_ATTACK_MAX_LEVEL  = 62;
export const SKIP_TICKET_MAX        = 20;
export const DAILY_STAR_DOOR_MAX    = 5;
export const GEM_AD_DAILY_LIMIT     = 5;
export const GEM_AD_REWARD          = 10;
export const COIN_AD_COOLDOWN_MS    = 10 * 60 * 1000;
export const AUTO_MERGE_DURATION_MS = 3 * 60 * 1000;
export const AUTO_MERGE_COOLDOWN_MS = 10 * 60 * 1000;
export const AUTO_MERGE_DAILY_FREE  = 3;
export const MAX_WEAPON_STAR        = 99;
export const ATTACK_BOOST_DURATION_MS    = 10 * 60 * 1000;
export const ATTACK_BOOST_AD_COOLDOWN_MS = 15 * 60 * 1000;
export const SHIELD_AD_COOLDOWN_MS       = 30 * 60 * 1000;
export const MAX_WORLD = 10;

export const PRESTIGE_ATTACK   = 1;
export const PRESTIGE_COIN     = 2;
export const PRESTIGE_OFFLINE  = 3;
export const PRESTIGE_GEM_DROP = 4;

// ─── World definitions ───────────────────────────────────────────────────────

export interface WorldDef { id: number; name: string; }
export const WORLDS: WorldDef[] = [
  { id: 1, name: '星明り界' },
  { id: 2, name: '星雲界' },
  { id: 3, name: '銀河核界' },
  { id: 4, name: '超銀河界' },
  { id: 5, name: '天の極界' },
];

export function worldInLoop(worldId: number): number {
  return ((worldId - 1) % 5) + 1;
}

export function gameModeOf(worldId: number): GameMode {
  if (worldId <= 5)  return GameMode.EASY;
  if (worldId <= 10) return GameMode.NORMAL;
  if (worldId <= 15) return GameMode.HARD_1;
  return GameMode.HARD_2;
}

export function gameModeHpMultiplier(worldId: number): number {
  return 1.0; // all modes 1.0 per current Kotlin definition
}

export function weaponStarColor(loopPos: number): string {
  switch (loopPos) {
    case 1: return '#e0e0e0'; // 星明り界: 白
    case 2: return '#64b5f6'; // 星雲界:   水色
    case 3: return '#ce93d8'; // 銀河核界: 紫
    case 4: return '#ffd54f'; // 超銀河界: 金
    case 5: return '#f48fb1'; // 天の極界: ローズゴールド
    default: return '#ffffff';
  }
}

export function worldOf(stage: number): number {
  return Math.floor(stage / 1_000_000) + 1;
}

export function worldStartStage(worldId: number): number {
  return worldId <= 1 ? 1 : (worldId - 1) * 1_000_000;
}

export function worldDisplayName(worldId: number): string {
  const loopIdx = (worldId - 1) % 5;
  const worldName = WORLDS[loopIdx]?.name ?? '？？？界';
  const mode = gameModeOf(worldId);
  return mode === GameMode.EASY ? worldName : `${gameModeDisplayName(mode)}｜${worldName}`;
}

export function gameModeDisplayName(mode: GameMode): string {
  switch (mode) {
    case GameMode.EASY:   return '星明り編';
    case GameMode.NORMAL: return '星神編';
    case GameMode.HARD_1: return '混沌編';
    case GameMode.HARD_2: return '虚無編';
  }
}

export function worldStageDisplay(worldId: number, stage: number): number {
  return stage - (worldId - 1) * 1_000_000;
}

// ─── Boss / Stage helpers ────────────────────────────────────────────────────

export function bossTypeFor(stage: number): BossType | null {
  if (stage % 10000 === 0) return BossType.LEGEND;
  if (stage % 1000  === 0) return BossType.MAJOR;
  if (stage % 100   === 0) return BossType.MINOR;
  return null;
}

export function bossMultiplierFor(stage: number): number {
  if (stage % 10000 === 0) return 15;
  if (stage % 1000  === 0) return 7;
  if (stage % 100   === 0) return 3;
  return 1;
}

export function nextBossStageIn(stage: number, advance: number): number | null {
  const next = (Math.floor(stage / 100) + 1) * 100;
  return next <= stage + advance ? next : null;
}

// ─── Weapon helpers ──────────────────────────────────────────────────────────

// ★n の攻撃力: 10 × 2.2^(n-1), iterative (= Kotlin)
export function starAttack(level: number): number {
  let atk = 10;
  for (let i = 1; i < level; i++) atk = Math.floor(atk * 2.2);
  return atk;
}

export function totalWeapons(s: GameState): number {
  return Object.values(s.weapons).reduce((a, b) => a + b, 0);
}

// ─── Coin Attack ─────────────────────────────────────────────────────────────

// 累計ボーナス: 2^n - 1 (n = coinAttackLevel)
export function coinAttackBonus(s: GameState): number {
  return s.coinAttackLevel === 0 ? 0 : Math.pow(2, s.coinAttackLevel) - 1;
}
export function coinAttackNextCost(s: GameState): number {
  return Math.pow(2, s.coinAttackLevel);
}
export function coinAttackNextBonus(s: GameState): number {
  return Math.pow(2, s.coinAttackLevel);
}

// ─── Prestige ────────────────────────────────────────────────────────────────

export function prestigeUpgradeLevel(s: GameState, id: number): number {
  return s.prestigeUpgrades[id] ?? 0;
}
export function prestigeUpgradeMax(id: number): number {
  switch (id) {
    case PRESTIGE_ATTACK:   return 20;
    case PRESTIGE_COIN:     return 10;
    case PRESTIGE_OFFLINE:  return 8;
    case PRESTIGE_GEM_DROP: return 10;
    default:                return 0;
  }
}
export function prestigeUpgradeCost(s: GameState, id: number): number {
  const lv = prestigeUpgradeLevel(s, id);
  return id === PRESTIGE_OFFLINE ? (lv + 1) * 2 : lv + 1;
}
export function prestigeAttackMultiplier(s: GameState): number {
  return 1.0 + 0.05 * prestigeUpgradeLevel(s, PRESTIGE_ATTACK);
}
export function prestigeCoinMultiplier(s: GameState): number {
  return 1.0 + 0.10 * prestigeUpgradeLevel(s, PRESTIGE_COIN);
}
export function prestigeOfflineHours(s: GameState): number {
  return 8 + prestigeUpgradeLevel(s, PRESTIGE_OFFLINE);
}
export function prestigeGemDropRate(s: GameState): number {
  return GEM_DROP_CHANCE + 0.01 * prestigeUpgradeLevel(s, PRESTIGE_GEM_DROP);
}

// ─── Attack calculation ───────────────────────────────────────────────────────

export function isAttackBoosted(s: GameState): boolean {
  return Date.now() < s.attackBoostEndTime;
}
export function isCraftAttackBoosted(s: GameState): boolean {
  return Date.now() < s.craftAttackBoostEndTime;
}
export function isCraftCoinBoosted(s: GameState): boolean {
  return Date.now() < s.craftCoinBoostEndTime;
}
export function isAutoMergeActive(s: GameState): boolean {
  return Date.now() < s.autoMergeEndTime;
}

export function totalAttack(s: GameState): number {
  const base = Object.entries(s.weapons).reduce(
    (sum, [lv, cnt]) => sum + starAttack(Number(lv)) * cnt, 0
  ) + coinAttackBonus(s);
  const adBoostMul   = isAttackBoosted(s) ? 2.0 : 1.0;
  const craftBoostMul = isCraftAttackBoosted(s) ? s.craftAttackBoostMultiplier : 1.0;
  return Math.floor(base * prestigeAttackMultiplier(s) * adBoostMul * craftBoostMul);
}

export function totalAttackBase(s: GameState): number {
  const base = Object.entries(s.weapons).reduce(
    (sum, [lv, cnt]) => sum + starAttack(Number(lv)) * cnt, 0
  ) + coinAttackBonus(s);
  return Math.floor(base * prestigeAttackMultiplier(s));
}

export function enemyHp(s: GameState): number {
  return Math.floor(s.stage * bossMultiplierFor(s.stage) * gameModeHpMultiplier(s.currentWorld));
}

// ─── Weapon slot cost ─────────────────────────────────────────────────────────

export function weaponSlotExpandCost(s: GameState): number {
  const n = s.weaponSlots;
  if (n < 10)  return (n - 3) * 100;
  if (n < 20)  return (n - 7) * 1_000;
  if (n < 35)  return (n - 15) * 20_000;
  return (n - 30) * 1_000_000;
}

// ─── Star gen ────────────────────────────────────────────────────────────────

export function isStarUnlocked(s: GameState, star: number): boolean {
  return (s.starGenLevels[star] ?? 0) > 0;
}
export function starGenLevel(s: GameState, star: number): number {
  return s.starGenLevels[star] ?? 0;
}
export function canUnlockStar(s: GameState, star: number): boolean {
  if (star < 2 || isStarUnlocked(s, star)) return false;
  return star === 2 ? true : starGenLevel(s, star - 1) >= 10;
}
export function starUpgradeCost(star: number, currentLevel: number): number {
  return (currentLevel + 1) * star * (currentLevel < 10 ? 1 : 2);
}
export function starUnlockCost(star: number): number {
  return star * 100;
}

// ─── Rebirth ─────────────────────────────────────────────────────────────────

export function rebirthUnlocked(s: GameState): boolean {
  return s.maxBossStageCleared >= 1000;
}
export function rebirthThreshold(s: GameState): number {
  return s.maxBossStageCleared >= 10000 ? s.maxBossStageCleared + 1000 : 1000;
}
export function canRebirth(s: GameState): boolean {
  return s.maxBossStageCleared >= 1000 && s.stage >= rebirthThreshold(s);
}
export function rebirthMaxSkipTarget(s: GameState): number {
  const raw = Math.max(0, (Math.floor(s.maxBossStageCleared / 100) - 1) * 100);
  const wStart = worldStartStage(s.currentWorld);
  return raw < wStart ? 0 : raw;
}
export function rebirthSkipCost(s: GameState, targetStage: number): number {
  const wStart = worldStartStage(s.currentWorld);
  return Math.max(1, Math.floor((targetStage - wStart) / 100) + 1);
}

// ─── Auto-merge ───────────────────────────────────────────────────────────────

export function autoMergeFreeUsedToday(s: GameState, today: string): number {
  return s.autoMergeFreeLastDate === today ? s.autoMergeFreeUsesToday : 0;
}
export function autoMergeFreeRemainingToday(s: GameState, today: string): number {
  return Math.max(0, AUTO_MERGE_DAILY_FREE - autoMergeFreeUsedToday(s, today));
}
export function autoMergeOnCooldown(s: GameState): boolean {
  return Date.now() - s.autoMergeLastUsedTime < AUTO_MERGE_COOLDOWN_MS;
}
export function autoMergeCooldownRemainingMs(s: GameState): number {
  return Math.max(0, AUTO_MERGE_COOLDOWN_MS - (Date.now() - s.autoMergeLastUsedTime));
}

// ─── Star door ───────────────────────────────────────────────────────────────

export function starDoorUsedToday(s: GameState, today: string): number {
  return s.lastStarDoorDate === today ? s.dailyStarDoorCount : 0;
}
export function canUseStarDoor(s: GameState, today: string): boolean {
  return starDoorUsedToday(s, today) < DAILY_STAR_DOOR_MAX;
}
export function skipTicketsAfterAdd(s: GameState, amount: number): number {
  return Math.min(s.skipTickets + amount, SKIP_TICKET_MAX);
}

// ─── Login bonus ──────────────────────────────────────────────────────────────

export function loginBonusTickets(streak: number): number {
  const day = ((streak - 1) % 7) + 1;
  return [3, 5, 6].includes(day) ? 2 : day === 7 ? 3 : 1;
}
export function loginBonusGems(streak: number): number {
  return ((streak - 1) % 7) + 1 === 7 ? 2 : 0;
}

// ─── Daily missions ───────────────────────────────────────────────────────────

export interface DailyMission {
  id: string;
  title: string;
  progress: number;
  target: number;
  reward: number;
  claimed: boolean;
  rewardSkipTickets: number;
  completed: boolean;
  canClaim: boolean;
  progressText: string;
}

export function getDailyMissions(s: GameState, today: string): DailyMission[] {
  const isToday   = s.dailyDate === today;
  const battleWins = isToday ? s.dailyBattleWinCount : 0;
  const playSec    = isToday ? s.dailyPlaySeconds    : 0;
  const adCount    = isToday ? s.dailyStarDoorCount  : 0;
  const claimedSet = new Set(
    isToday && s.dailyMissionsClaimed ? s.dailyMissionsClaimed.split(',') : []
  );

  let adMission: DailyMission;
  if (claimedSet.has('ad5')) {
    adMission = makeMission('ad10', '星の扉を10回使う', Math.min(adCount, 10), 10, 15, claimedSet, 3);
  } else if (claimedSet.has('ad1')) {
    adMission = makeMission('ad5', '星の扉を5回使う', Math.min(adCount, 5), 5, 8, claimedSet, 2);
  } else {
    adMission = makeMission('ad1', '星の扉を1回使う', Math.min(adCount, 1), 1, 3, claimedSet, 1);
  }

  return [
    makeMission('battle5', 'バトルに5回勝利', Math.min(battleWins, 5), 5, 5, claimedSet),
    makeMission('play5m',  '5分プレイ', Math.min(playSec, 300), 300, 5, claimedSet),
    adMission,
  ];
}

function makeMission(
  id: string, title: string, progress: number, target: number, reward: number,
  claimedSet: Set<string>, rewardSkipTickets = 0
): DailyMission {
  const claimed = claimedSet.has(id);
  const completed = progress >= target;
  const text = id === 'play5m' ? `${Math.floor(progress / 60)} / 5 分` : `${progress} / ${target} 回`;
  return { id, title, progress, target, reward, claimed, rewardSkipTickets, completed, canClaim: completed && !claimed, progressText: text };
}

// ─── Achievements & Milestones ────────────────────────────────────────────────

export interface AchievementDef {
  id: string;
  title: string;
  description: string;
  threshold: number;
  rewardGems: number;
  statKey: string;
  oneTime: boolean;
}

export interface MilestoneDef {
  id: string;
  title: string;
  rewardGems: number;
  navigateTo: string;
}

export const MILESTONES: MilestoneDef[] = [
  { id: 'stage_100',     title: 'Stage 100到達',          rewardGems: 5,   navigateTo: 'home' },
  { id: 'star_2',        title: '★2武器の生成を解放',      rewardGems: 5,   navigateTo: 'enhancement' },
  { id: 'stage_500',     title: 'Stage 500到達',          rewardGems: 10,  navigateTo: 'home' },
  { id: 'star_3',        title: '★3武器の生成を解放',      rewardGems: 10,  navigateTo: 'enhancement' },
  { id: 'stage_1000',    title: '大ボス初撃破 Stage1000', rewardGems: 20,  navigateTo: 'home' },
  { id: 'stage_5000',    title: 'Stage 5000到達',         rewardGems: 20,  navigateTo: 'home' },
  { id: 'star_5',        title: '★5武器の生成を解放',      rewardGems: 15,  navigateTo: 'enhancement' },
  { id: 'boss_legend',   title: '伝説ボス到達 Stage10000', rewardGems: 50, navigateTo: 'home' },
  { id: 'stage_50000',   title: 'Stage 50000到達',        rewardGems: 30,  navigateTo: 'home' },
  { id: 'star_10',       title: '★10武器の生成を解放',     rewardGems: 20,  navigateTo: 'enhancement' },
  { id: 'stage_100000',  title: 'Stage 100000到達',       rewardGems: 50,  navigateTo: 'home' },
  { id: 'stage_200000',  title: 'Stage 200000到達',       rewardGems: 50,  navigateTo: 'home' },
  { id: 'star_15',       title: '★15武器の生成を解放',     rewardGems: 30,  navigateTo: 'enhancement' },
  { id: 'stage_300000',  title: 'Stage 300000到達',       rewardGems: 50,  navigateTo: 'home' },
  { id: 'star_20',       title: '★20武器の生成を解放',     rewardGems: 50,  navigateTo: 'enhancement' },
  { id: 'stage_500000',  title: 'Stage 500000到達',       rewardGems: 80,  navigateTo: 'home' },
  { id: 'star_30',       title: '★30武器の生成を解放',     rewardGems: 80,  navigateTo: 'enhancement' },
  { id: 'stage_1000000', title: 'Stage 1000000到達',      rewardGems: 100, navigateTo: 'home' },
  { id: 'star_50',       title: '★50武器の生成を解放',     rewardGems: 100, navigateTo: 'enhancement' },
  { id: 'stage_5000000', title: 'Stage 5000000到達',      rewardGems: 150, navigateTo: 'home' },
  { id: 'star_99',       title: '★99武器の生成を解放',     rewardGems: 200, navigateTo: 'enhancement' },
];

export const ACHIEVEMENTS: AchievementDef[] = [
  { id: 'kill_1k',  title: '1000体撃破',   description: '敵1000体撃破ごと',     threshold: 1_000, rewardGems: 5,  statKey: 'enemies', oneTime: false },
  { id: 'stage_ms', title: 'ステージ到達', description: 'ステージ100の倍数ごと', threshold: 100,  rewardGems: 10, statKey: 'stage',   oneTime: false },
];

export function isMilestoneAchieved(s: GameState, def: MilestoneDef): boolean {
  switch (def.id) {
    case 'stage_100':     return s.maxMilestoneReached >= 1;
    case 'star_2':        return isStarUnlocked(s, 2);
    case 'stage_500':     return s.maxMilestoneReached >= 5;
    case 'star_3':        return isStarUnlocked(s, 3);
    case 'stage_1000':    return s.maxBossStageCleared >= 1000;
    case 'stage_5000':    return s.maxMilestoneReached >= 50;
    case 'star_5':        return isStarUnlocked(s, 5);
    case 'boss_legend':   return s.maxBossStageCleared >= 10000;
    case 'stage_50000':   return s.maxMilestoneReached >= 500;
    case 'star_10':       return isStarUnlocked(s, 10);
    case 'stage_100000':  return s.maxMilestoneReached >= 1000;
    case 'stage_200000':  return s.maxMilestoneReached >= 2000;
    case 'star_15':       return isStarUnlocked(s, 15);
    case 'stage_300000':  return s.maxMilestoneReached >= 3000;
    case 'star_20':       return isStarUnlocked(s, 20);
    case 'stage_500000':  return s.maxMilestoneReached >= 5000;
    case 'star_30':       return isStarUnlocked(s, 30);
    case 'stage_1000000': return s.maxMilestoneReached >= 10000;
    case 'star_50':       return isStarUnlocked(s, 50);
    case 'stage_5000000': return s.maxMilestoneReached >= 50000;
    case 'star_99':       return isStarUnlocked(s, 99);
    default:              return false;
  }
}

export function currentMilestone(s: GameState): MilestoneDef | undefined {
  const claimed = new Set(s.milestonesClaimedIds);
  return MILESTONES.find(m => !claimed.has(m.id));
}

export function achievementTimesEarned(s: GameState, def: AchievementDef): number {
  let times = 0;
  switch (def.statKey) {
    case 'enemies': times = Math.floor(s.totalEnemiesDefeated / def.threshold); break;
    case 'stage':   times = Math.floor((s.maxMilestoneReached * 100) / def.threshold); break;
    case 'coins':   times = Math.floor(s.totalCoinsEarned / def.threshold); break;
  }
  return def.oneTime ? Math.min(times, 1) : times;
}
export function achievementClaimable(s: GameState, def: AchievementDef): number {
  return Math.max(0, achievementTimesEarned(s, def) - (s.achievementsClaimed[def.id] ?? 0));
}

// ─── Titles ───────────────────────────────────────────────────────────────────

export interface TitleDef {
  id: string;
  name: string;
  description: string;
  condition: (s: GameState) => boolean;
}

export const TITLES: TitleDef[] = [
  { id: 'newcomer',   name: '新米冒険者',   description: 'ゲームを開始した',               condition: () => true },
  { id: 'stage1000',  name: '伝説の戦士',   description: 'Stage1000の大ボスを撃破した',    condition: s => s.maxBossStageCleared >= 1000 },
  { id: 'stage10000', name: '星砕きの英雄', description: 'Stage10000の伝説ボスを撃破した', condition: s => s.maxBossStageCleared >= 10_000 },
  { id: 'rebirth1',   name: '転生者',       description: '転生を初めて行った',             condition: s => rebirthUnlocked(s) },
  { id: 'star50',     name: '至高の鍛冶師', description: '★50武器の生成を解放した',        condition: s => isStarUnlocked(s, 50) },
  { id: 'star99',     name: '星の頂点',     description: '★99武器の生成を解放した',        condition: s => isStarUnlocked(s, 99) },
  { id: 'streak7',    name: '7日の勇者',    description: '7日連続ログインした',            condition: s => s.loginStreak >= 7 },
  { id: 'streak30',   name: '星の常連',     description: '30日連続ログインした',           condition: s => s.loginStreak >= 30 },
  { id: 'enemy100k',  name: '大量討伐者',   description: '敵を10万体討伐した',            condition: s => s.totalEnemiesDefeated >= 100_000 },
  { id: 'enemy1m',    name: '殲滅の申し子', description: '敵を100万体討伐した',           condition: s => s.totalEnemiesDefeated >= 1_000_000 },
];

export function earnedTitles(s: GameState): TitleDef[] {
  return TITLES.filter(t => t.condition(s));
}

// ─── Monster encyclopedia ─────────────────────────────────────────────────────

export interface MonsterDef { stageFrom: number; stageTo: number; name: string; }
export const MONSTERS: MonsterDef[] = [
  { stageFrom: 1,         stageTo: 99,              name: 'スライム' },
  { stageFrom: 100,       stageTo: 499,             name: 'ゴブリン' },
  { stageFrom: 500,       stageTo: 999,             name: 'オーク戦士' },
  { stageFrom: 1_000,     stageTo: 4_999,           name: '砂漠のゴーレム' },
  { stageFrom: 5_000,     stageTo: 9_999,           name: '炎の精霊' },
  { stageFrom: 10_000,    stageTo: 49_999,          name: '氷竜の眷属' },
  { stageFrom: 50_000,    stageTo: 99_999,          name: '深海の海神兵' },
  { stageFrom: 100_000,   stageTo: 499_999,         name: '天空の守護者' },
  { stageFrom: 500_000,   stageTo: 999_999,         name: '星の巨人' },
  { stageFrom: 1_000_000, stageTo: Number.MAX_SAFE_INTEGER, name: '混沌の使者' },
];

export function monsterNameFor(stage: number): string {
  const dispStage = worldStageDisplay(worldOf(stage), stage);
  return MONSTERS.find(m => dispStage >= m.stageFrom && dispStage <= m.stageTo)?.name ?? '謎の敵';
}

// ─── Coin ad reward ───────────────────────────────────────────────────────────

export function coinAdReward(s: GameState): number {
  return Math.max(1000, s.maxMilestoneReached * 100 * 10);
}
export function maxAutoDeleteLevel(s: GameState): number {
  const maxLv = Math.max(...Object.keys(s.weapons).map(Number), 0);
  return Math.max(0, maxLv - 1);
}

// ─── Recipes ─────────────────────────────────────────────────────────────────

export enum Material {
  IRON_FRAGMENT   = 'IRON_FRAGMENT',
  SILVER_FRAGMENT = 'SILVER_FRAGMENT',
  GOLD_FRAGMENT   = 'GOLD_FRAGMENT',
  COIN            = 'COIN',
  GEM             = 'GEM',
  MAGIC_STONE     = 'MAGIC_STONE',
  ANCIENT_CORE    = 'ANCIENT_CORE',
  STAR_CRYSTAL    = 'STAR_CRYSTAL',
}

export const MATERIAL_LABELS: Record<Material, string> = {
  [Material.IRON_FRAGMENT]:   '鉄の欠片',
  [Material.SILVER_FRAGMENT]: '銀の欠片',
  [Material.GOLD_FRAGMENT]:   '金の欠片',
  [Material.COIN]:            'コイン',
  [Material.GEM]:             'ジェム',
  [Material.MAGIC_STONE]:     '魔石の欠片',
  [Material.ANCIENT_CORE]:    '古竜の核',
  [Material.STAR_CRYSTAL]:    '星砕きの結晶',
};

export interface MaterialReq { material: Material; amount: number; }

export type RecipeResult =
  | { type: 'AddWeapon'; starLevel: number }
  | { type: 'CoinBoost'; durationMs: number }
  | { type: 'AttackBoost'; multiplier: number; durationMs: number };

export interface Recipe {
  id: string;
  name: string;
  materials: MaterialReq[];
  result: RecipeResult;
  unlockStage: number;
  hint: string;
}

export const RECIPES: Recipe[] = [
  {
    id: 'sword_iron', name: '鉄の攻撃ブースト',
    materials: [{ material: Material.IRON_FRAGMENT, amount: 10 }],
    result: { type: 'AttackBoost', multiplier: 1.5, durationMs: 5 * 60 * 1000 },
    unlockStage: 10,
    hint: 'オートデリートで★1〜10のウェポンを削除すると鉄の欠片が入手できます',
  },
  {
    id: 'sword_silver', name: '銀の攻撃ブースト',
    materials: [
      { material: Material.SILVER_FRAGMENT, amount: 5 },
      { material: Material.COIN, amount: 1_000 },
    ],
    result: { type: 'AttackBoost', multiplier: 2.0, durationMs: 10 * 60 * 1000 },
    unlockStage: 50,
    hint: 'オートデリートで★11〜20のウェポンを削除すると銀の欠片が入手できます',
  },
  {
    id: 'coin_boost', name: 'コインブースト',
    materials: [
      { material: Material.GOLD_FRAGMENT, amount: 3 },
      { material: Material.GEM, amount: 5 },
    ],
    result: { type: 'CoinBoost', durationMs: 10 * 60 * 1000 },
    unlockStage: 100,
    hint: 'オートデリートで★21以上のウェポンを削除すると金の欠片が入手できます',
  },
  {
    id: 'sword_legend', name: '黄金の攻撃ブースト',
    materials: [
      { material: Material.IRON_FRAGMENT, amount: 10 },
      { material: Material.SILVER_FRAGMENT, amount: 10 },
      { material: Material.GOLD_FRAGMENT, amount: 10 },
    ],
    result: { type: 'AttackBoost', multiplier: 3.0, durationMs: 15 * 60 * 1000 },
    unlockStage: 200,
    hint: '全素材を使う最強の攻撃ブースト。★21以上のオートデリートで金の欠片が集まります',
  },
  {
    id: 'boss_minor_atk', name: '魔石強化',
    materials: [{ material: Material.MAGIC_STONE, amount: 5 }],
    result: { type: 'AttackBoost', multiplier: 2.0, durationMs: 10 * 60 * 1000 },
    unlockStage: 200,
    hint: '中ボス（100の倍数ステージ）を倒すと魔石の欠片が手に入ります',
  },
  {
    id: 'boss_major_atk', name: '古竜強化',
    materials: [{ material: Material.ANCIENT_CORE, amount: 3 }],
    result: { type: 'AttackBoost', multiplier: 4.0, durationMs: 20 * 60 * 1000 },
    unlockStage: 1000,
    hint: '大ボス（1000の倍数ステージ）を倒すと古竜の核が手に入ります',
  },
  {
    id: 'boss_legend_atk', name: '星砕き強化',
    materials: [{ material: Material.STAR_CRYSTAL, amount: 1 }],
    result: { type: 'AttackBoost', multiplier: 6.0, durationMs: 30 * 60 * 1000 },
    unlockStage: 10000,
    hint: '伝説ボス（10000の倍数ステージ）を倒すと星砕きの結晶が手に入ります',
  },
];

export function fragmentAmount(s: GameState, material: Material): number {
  switch (material) {
    case Material.IRON_FRAGMENT:   return s.ironFragments;
    case Material.SILVER_FRAGMENT: return s.silverFragments;
    case Material.GOLD_FRAGMENT:   return s.goldFragments;
    case Material.COIN:            return Math.min(s.coins, Number.MAX_SAFE_INTEGER);
    case Material.GEM:             return s.gems;
    case Material.MAGIC_STONE:     return s.magicStoneFragments;
    case Material.ANCIENT_CORE:    return s.ancientDragonCore;
    case Material.STAR_CRYSTAL:    return s.starShatterCrystal;
  }
}

export function canCraftRecipe(s: GameState, recipe: Recipe): boolean {
  return recipe.materials.every(req => fragmentAmount(s, req.material) >= req.amount);
}

export function recipeResultDescription(recipe: Recipe): string {
  const r = recipe.result;
  if (r.type === 'AddWeapon')   return `★${r.starLevel}ウェポンを1個追加`;
  if (r.type === 'CoinBoost')   return `コイン獲得量×2 (${r.durationMs / 60_000}分間)`;
  if (r.type === 'AttackBoost') return `攻撃力×${r.multiplier.toFixed(1)} (${r.durationMs / 60_000}分間)`;
  return '';
}

// ─── Number formatting ─────────────────────────────────────────────────────────

export function formatNumber(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(2)}B`;
  if (n >= 1_000_000)     return `${(n / 1_000_000).toFixed(2)}M`;
  if (n >= 1_000)         return `${(n / 1_000).toFixed(2)}K`;
  return n.toLocaleString();
}

// ─── Today / Yesterday ───────────────────────────────────────────────────────

export function todayString(): string {
  return new Date().toISOString().slice(0, 10);
}
export function yesterdayString(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return d.toISOString().slice(0, 10);
}

// ─── Default state ────────────────────────────────────────────────────────────

export function createDefaultGameState(): GameState {
  return {
    coins: 0, gems: 0, lastSaveTime: Date.now(),
    weapons: {}, weaponSlots: 5, stage: 1,
    autoDeleteLevel: 0, starGenLevels: { 1: 1 },
    coinAttackLevel: 0, prestigeStones: 0, prestigeUpgrades: {},
    maxMilestoneReached: 0, achievementsClaimed: {},
    totalEnemiesDefeated: 0, totalCoinsEarned: 0,
    gemAdWatchedToday: 0, gemAdLastDate: '', lastGemAdTime: 0, lastCoinAdTime: 0,
    attackBoostEndTime: 0, penaltyShieldActive: false,
    lastAttackBoostAdTime: 0, lastShieldAdTime: 0,
    autoMergeEndTime: 0, autoMergeFreeUsesToday: 0,
    autoMergeFreeLastDate: '', autoMergeLastUsedTime: 0,
    tutorialShown: false,
    ironFragments: 0, silverFragments: 0, goldFragments: 0,
    discoveredRecipeIds: [],
    craftCoinBoostEndTime: 0, craftAttackBoostEndTime: 0, craftAttackBoostMultiplier: 1.0,
    magicStoneFragments: 0, ancientDragonCore: 0, starShatterCrystal: 0,
    lastClearedBossStage: 0, maxBossStageCleared: 0,
    dailyDate: '', dailyMergeCount: 0, dailyPlaySeconds: 0,
    dailyAdWatchCount: 0, dailyMissionsClaimed: '',
    skipTickets: 0, loginStreak: 0, lastLoginDate: '',
    dailyStarDoorCount: 0, lastStarDoorDate: '',
    milestonesClaimedIds: [], dailyBattleWinCount: 0,
    currentWorld: 1, worldWeaponStates: {},
  };
}
