// 戦闘処理 — Kotlin MainViewModel.processBattle() の移植
import {
  GameState, BossType, WorldWeaponState,
  bossTypeFor, bossMultiplierFor, nextBossStageIn,
  totalAttack, enemyHp, prestigeCoinMultiplier, prestigeGemDropRate,
  isCraftCoinBoosted, isAttackBoosted, isCraftAttackBoosted,
  ENEMIES_PER_MINUTE, RECIPES,
  worldStageDisplay, worldStartStage, worldOf, MAX_WORLD,
  formatNumber, monsterNameFor,
} from '../types/GameState.js';

export interface BattleTickResult {
  won: boolean;
  logEntry: string;
  newState: GameState;
  worldTransition: number | null;
}

export function processBattle(s: GameState, minuteCount: number): BattleTickResult {
  const atk = totalAttack(s);
  const hp  = enemyHp(s);
  const stageBefore = s.stage;
  const bossT = bossTypeFor(stageBefore);
  const isBoss = bossT !== null;
  const worldStart = worldStartStage(s.currentWorld);
  const dispBefore = worldStageDisplay(s.currentWorld, stageBefore);

  const bossPrefix = bossT === BossType.LEGEND ? '★★★伝説ボス★★★ '
                   : bossT === BossType.MAJOR  ? '★★大ボス★★ '
                   : bossT === BossType.MINOR   ? '★中ボス★ '
                   : '';

  if (atk > hp) {
    // ── 勝利 ──────────────────────────────────────────────────────────────
    const stageAfter = isBoss
      ? stageBefore + ENEMIES_PER_MINUTE
      : (nextBossStageIn(stageBefore, ENEMIES_PER_MINUTE) ?? stageBefore + ENEMIES_PER_MINUTE);

    const baseCoins  = ENEMIES_PER_MINUTE * hp;
    const coinBoost  = isCraftCoinBoosted(s) ? 2.0 : 1.0;
    const battleCoins = Math.floor(baseCoins * prestigeCoinMultiplier(s) * coinBoost);

    let gemsEarned = 0;
    const gemRate = prestigeGemDropRate(s);
    for (let i = 0; i < ENEMIES_PER_MINUTE; i++) {
      if (Math.random() < gemRate) gemsEarned++;
    }

    const newMilestone    = Math.floor(stageAfter / 100);
    const newMaxMilestone = Math.max(s.maxMilestoneReached, newMilestone);

    const discoveredSet = new Set(s.discoveredRecipeIds);
    const newlyDiscovered = RECIPES
      .filter(r => r.unlockStage <= stageAfter && !discoveredSet.has(r.id))
      .map(r => r.id);

    // ボス処理
    let stonesEarned      = 0;
    let newLastCleared    = s.lastClearedBossStage;
    let newMaxBossCleared = s.maxBossStageCleared;
    let newMagicStone     = s.magicStoneFragments;
    let newAncientCore    = s.ancientDragonCore;
    let newStarCrystal    = s.starShatterCrystal;
    let overflowCoins     = 0;
    let overflowGems      = 0;

    if (bossT !== null) {
      newLastCleared = Math.max(s.lastClearedBossStage, stageBefore);

      if (stageBefore > s.maxBossStageCleared) {
        newMaxBossCleared = stageBefore;
        stonesEarned = bossT === BossType.LEGEND ? 10 : bossT === BossType.MAJOR ? 3 : 1;
      }

      if (stageBefore > s.lastClearedBossStage) {
        if (bossT === BossType.MINOR) {
          if (newMagicStone < 99) newMagicStone++;
          else overflowCoins += Math.max(100, s.maxMilestoneReached * 10);
        } else if (bossT === BossType.MAJOR) {
          if (newAncientCore < 99) newAncientCore++;
          else overflowCoins += Math.max(1_000, s.maxMilestoneReached * 100);
        } else {
          if (newStarCrystal < 99) newStarCrystal++;
          else overflowGems++;
        }
      }
    }

    const materialDropped = bossT !== null && stageBefore > s.lastClearedBossStage;
    const totalCoins      = battleCoins + overflowCoins;
    const dispAfter       = worldStageDisplay(s.currentWorld, stageAfter);

    const gemText      = gemsEarned > 0    ? `  ジェム+${gemsEarned}`   : '';
    const stoneText    = stonesEarned > 0  ? `  輝石+${stonesEarned}`   : '';
    const matText      = materialDropped ? (
      bossT === BossType.MINOR  ? '  魔石の欠片+1' :
      bossT === BossType.MAJOR  ? '  古竜の核+1'   :
                                  '  星砕きの結晶+1'
    ) : '';
    const recipeText   = newlyDiscovered.length > 0 ? '  【新レシピ解放！】' : '';
    const bossCleared  = isBoss ? ' 【ボス撃破！】' : '';
    const boostTag     = isAttackBoosted(s)      ? ' [強化中]' : '';
    const coinBoostTag = isCraftCoinBoosted(s)   ? ' [コインブースト中]' : '';
    const craftAtkTag  = isCraftAttackBoosted(s) ? ` [攻撃ブースト×${s.craftAttackBoostMultiplier.toFixed(1)}]` : '';

    const logEntry = `${minuteCount}分 ── ${bossPrefix}${dispBefore} → ${dispAfter}  敵${ENEMIES_PER_MINUTE}体撃破！コイン+${formatNumber(totalCoins)}${gemText}${stoneText}${matText}${recipeText}${bossCleared}${boostTag}${coinBoostTag}${craftAtkTag}`;

    const today  = new Date().toISOString().slice(0, 10);
    const isToday = s.dailyDate === today;
    const battleWins = isToday ? s.dailyBattleWinCount : 0;

    const newState: GameState = {
      ...s,
      stage:                stageAfter,
      coins:                s.coins + totalCoins,
      gems:                 s.gems + gemsEarned + overflowGems,
      prestigeStones:       s.prestigeStones + stonesEarned,
      maxMilestoneReached:  newMaxMilestone,
      lastClearedBossStage: newLastCleared,
      maxBossStageCleared:  newMaxBossCleared,
      magicStoneFragments:  newMagicStone,
      ancientDragonCore:    newAncientCore,
      starShatterCrystal:   newStarCrystal,
      totalEnemiesDefeated: s.totalEnemiesDefeated + ENEMIES_PER_MINUTE,
      totalCoinsEarned:     s.totalCoinsEarned + totalCoins,
      discoveredRecipeIds:  [...discoveredSet, ...newlyDiscovered],
      dailyDate:            today,
      dailyBattleWinCount:  battleWins + 1,
    };

    // ワールド移行チェック
    let worldTransition: number | null = null;
    const targetWorld = worldOf(stageAfter);
    if (targetWorld > s.currentWorld && targetWorld <= MAX_WORLD) {
      const isFirstVisit = !(s.currentWorld in s.worldWeaponStates) && targetWorld !== s.currentWorld;
      worldTransition = isFirstVisit ? targetWorld : null;
    }

    return { won: true, logEntry, newState, worldTransition };

  } else {
    // ── 敗北 ──────────────────────────────────────────────────────────────
    if (s.penaltyShieldActive) {
      const label = isBoss
        ? 'ボスに敗北 【シールド発動！落下防止】'
        : '攻撃力不足 【シールド発動！落下防止】';
      const logEntry = `${minuteCount}分 ── ${bossPrefix}${dispBefore}  ${label}`;
      return {
        won: false, logEntry, worldTransition: null,
        newState: { ...s, penaltyShieldActive: false },
      };
    }

    const raw = s.maxBossStageCleared < 10_000
      ? Math.max(s.lastClearedBossStage + 1, Math.floor(stageBefore / 2))
      : Math.max(s.lastClearedBossStage + 1, worldStart);
    const stageAfter = Math.max(raw, worldStart);
    const dispRaw = worldStageDisplay(s.currentWorld, stageAfter);
    const label = isBoss
      ? `ボスに敗北 (→ステージ${dispRaw})`
      : `攻撃力不足 (→ステージ${dispRaw})`;
    const logEntry = `${minuteCount}分 ── ${bossPrefix}${dispBefore} → ${dispRaw}  ${label}`;

    return {
      won: false, logEntry, worldTransition: null,
      newState: { ...s, stage: stageAfter, penaltyShieldActive: false },
    };
  }
}

export function enemyDisplayName(s: GameState): string {
  return monsterNameFor(s.stage);
}

export function enemyHpPercent(s: GameState, accumulatedDamage: number): number {
  const hp = enemyHp(s);
  if (hp <= 0) return 0;
  return Math.max(0, Math.min(100, (1 - accumulatedDamage / hp) * 100));
}
