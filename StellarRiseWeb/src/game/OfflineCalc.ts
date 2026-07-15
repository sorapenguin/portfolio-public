// オフライン収益計算 — Kotlin GameRepository.calculateOfflineResult() の移植
import {
  GameState,
  totalAttackBase, prestigeCoinMultiplier, prestigeGemDropRate, prestigeOfflineHours,
  bossMultiplierFor, bossTypeFor, nextBossStageIn,
  ENEMIES_PER_MINUTE,
} from '../types/GameState.js';

export interface OfflineResult {
  minutes: number;
  minutesWon: number;
  coins: number;
  gems: number;
  stageBefore: number;
  stageAfter: number;
}

export function calculateOfflineResult(s: GameState, serverEpochMs: number): OfflineResult | null {
  const elapsedMinutes = Math.floor((serverEpochMs - s.lastSaveTime) / 60_000);
  if (elapsedMinutes < 30) return null;

  const maxMinutes = prestigeOfflineHours(s) * 60;
  const minutes    = Math.min(elapsedMinutes, maxMinutes);

  const atk     = totalAttackBase(s);
  const coinMul = prestigeCoinMultiplier(s);
  const gemRate = prestigeGemDropRate(s);

  let simStage   = s.stage;
  let totalCoins = 0;
  let totalGems  = 0;
  let minutesWon = 0;

  for (let m = 0; m < minutes; m++) {
    const bm = bossMultiplierFor(simStage);
    const hp = simStage * bm;
    if (atk <= hp) break;

    const nextBoss = bossTypeFor(simStage) !== null
      ? null
      : nextBossStageIn(simStage, ENEMIES_PER_MINUTE);
    simStage = nextBoss ?? simStage + ENEMIES_PER_MINUTE;

    totalCoins += Math.floor(ENEMIES_PER_MINUTE * hp * coinMul);
    totalGems  += Math.floor(ENEMIES_PER_MINUTE * gemRate);
    minutesWon++;
  }

  if (minutesWon === 0) return null;

  return {
    minutes,
    minutesWon,
    coins:      totalCoins,
    gems:       totalGems,
    stageBefore: s.stage,
    stageAfter:  simStage,
  };
}

export function collectOfflineEarnings(s: GameState, result: OfflineResult, doubled: boolean): GameState {
  const coins = doubled ? result.coins * 2 : result.coins;
  const newMilestone    = Math.floor(result.stageAfter / 100);
  const highestBossCleared = Math.floor(result.stageAfter / 100) * 100;

  let stonesEarned = 0;
  let bossIter = (Math.floor(s.maxBossStageCleared / 100) + 1) * 100;
  while (bossIter <= highestBossCleared) {
    if (bossIter % 10000 === 0)     stonesEarned += 10;
    else if (bossIter % 1000 === 0) stonesEarned += 3;
    else                            stonesEarned += 1;
    bossIter += 100;
  }

  return {
    ...s,
    coins:                s.coins + coins,
    gems:                 s.gems + result.gems,
    stage:                result.stageAfter,
    maxMilestoneReached:  Math.max(s.maxMilestoneReached, newMilestone),
    prestigeStones:       s.prestigeStones + stonesEarned,
    maxBossStageCleared:  Math.max(s.maxBossStageCleared, highestBossCleared),
    lastClearedBossStage: Math.max(s.lastClearedBossStage, highestBossCleared),
    totalCoinsEarned:     s.totalCoinsEarned + coins,
    totalEnemiesDefeated: s.totalEnemiesDefeated + result.minutesWon * ENEMIES_PER_MINUTE,
  };
}
