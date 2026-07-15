// エントリーポイント — Kotlin IdleGameApp + MainActivity 相当
import {
  GameState, createDefaultGameState,
  isStarUnlocked, starGenLevel, canUnlockStar,
  weaponSlotExpandCost, prestigeUpgradeLevel, prestigeUpgradeMax, prestigeUpgradeCost,
  coinAttackNextCost, COIN_ATTACK_MAX_LEVEL,
  canCraftRecipe, RECIPES, Material,
  getDailyMissions, currentMilestone, isMilestoneAchieved, ACHIEVEMENTS, MILESTONES,
  achievementClaimable,
  canUseStarDoor, starDoorUsedToday, DAILY_STAR_DOOR_MAX, skipTicketsAfterAdd,
  canRebirth, rebirthUnlocked, rebirthMaxSkipTarget, rebirthSkipCost,
  loginBonusTickets, loginBonusGems, yesterdayString, todayString,
  totalAttack, worldDisplayName,
  formatNumber,
} from './types/GameState.js';
import { GameLoop } from './game/GameLoop.js';
import { processSecond, mergeWeapons, expandWeaponSlots, activateAutoMergeFree, setAutoDeleteLevel } from './game/WeaponSystem.js';
import { calculateOfflineResult, collectOfflineEarnings, OfflineResult } from './game/OfflineCalc.js';
import { rebirth, performRebirthSkip, enterWorld, switchWorld } from './game/RebirthSystem.js';
import { loadAndMerge, saveLocal, syncToApi, registerBeforeUnload, loadLocal } from './storage/SaveManager.js';
import {
  isLoggedIn, login, register, clearAuth, deleteAccount,
  getToken, getServerTime,
} from './api/GameApiClient.js';
import { NavBar } from './ui/NavBar.js';
import { HomePanel } from './ui/HomePanel.js';
import { WeaponPanel } from './ui/WeaponPanel.js';
import { EnhancementPanel } from './ui/EnhancementPanel.js';
import { RecipePanel } from './ui/RecipePanel.js';
import { AchievementPanel } from './ui/AchievementPanel.js';
import { SettingsPanel } from './ui/SettingsPanel.js';

// ─── Global state ─────────────────────────────────────────────────────────────

let state: GameState = createDefaultGameState();
const battleLog: string[] = [];
let pendingOffline: OfflineResult | null = null;
let gameStarted = false;
let updateIntervalId = 0;

function getState(): GameState { return state; }
function updateState(updater: (s: GameState) => GameState) {
  state = updater(state);
}

// ─── UI instances ─────────────────────────────────────────────────────────────

const navbar      = new NavBar();
const homePanel   = new HomePanel(getState);
const weaponPanel = new WeaponPanel(getState);
const enhPanel    = new EnhancementPanel(getState);
const recipePanel = new RecipePanel(getState);
const achPanel    = new AchievementPanel(getState);
const settingsPanel = new SettingsPanel(getState);

// ─── Game loop ────────────────────────────────────────────────────────────────

const gameLoop = new GameLoop(getState, updateState, (entry) => {
  battleLog.unshift(entry);
  if (battleLog.length > 20) battleLog.pop();
  homePanel.pushLog(entry, battleLog);
});

// ─── Toast utility ────────────────────────────────────────────────────────────

let toastTimer = 0;
function showToast(msg: string) {
  const el = document.getElementById('toast')!;
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => el.classList.remove('show'), 2500);
}

// ─── Dialog utility ───────────────────────────────────────────────────────────

function showDialog(
  title: string, body: string,
  actions: Array<{ label: string; danger?: boolean; onClick: () => void }>
) {
  const overlay = document.getElementById('dialog-overlay')!;
  const titleEl = document.getElementById('dialog-title')!;
  const bodyEl  = document.getElementById('dialog-body')!;
  const actEl   = document.getElementById('dialog-actions')!;
  titleEl.textContent = title;
  bodyEl.textContent  = body;
  actEl.innerHTML = '';
  actions.forEach(a => {
    const btn = document.createElement('button');
    btn.className = `btn btn-sm${a.danger ? ' btn-danger' : ''}`;
    btn.textContent = a.label;
    btn.addEventListener('click', () => {
      overlay.classList.add('hidden');
      a.onClick();
    });
    actEl.appendChild(btn);
  });
  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'btn btn-sm btn-outline';
  cancelBtn.textContent = 'キャンセル';
  cancelBtn.addEventListener('click', () => overlay.classList.add('hidden'));
  actEl.appendChild(cancelBtn);
  overlay.classList.remove('hidden');
}

// ─── World banner ─────────────────────────────────────────────────────────────

function showWorldBanner(worldId: number) {
  const banner = document.getElementById('world-banner')!;
  banner.textContent = `⭐ 新ワールド解放: ${worldDisplayName(worldId)} ⭐`;
  banner.classList.remove('hidden');
  setTimeout(() => banner.classList.add('hidden'), 4000);
}

// ─── Login / Auth ─────────────────────────────────────────────────────────────

function showLoginOverlay(mode: 'login' | 'register') {
  const overlay  = document.getElementById('login-overlay')!;
  const usernameInput = document.getElementById('login-username') as HTMLInputElement;
  const passInput = document.getElementById('login-password') as HTMLInputElement;
  const submitBtn = document.getElementById('login-submit-btn') as HTMLButtonElement;
  const errEl     = document.getElementById('login-error')!;

  overlay.classList.remove('hidden');
  errEl.textContent = '';
  submitBtn.textContent = mode === 'login' ? 'ログイン' : '新規登録';
  usernameInput.style.display = mode === 'login' ? '' : 'none';

  submitBtn.onclick = async () => {
    errEl.textContent = '';
    submitBtn.disabled = true;
    const pw = passInput.value;
    if (pw.length < 6) { errEl.textContent = 'パスワードは6文字以上'; submitBtn.disabled = false; return; }
    if (mode === 'login') {
      const un = usernameInput.value.trim();
      if (!un) { errEl.textContent = 'ユーザー名を入力してください'; submitBtn.disabled = false; return; }
      const result = await login(un, pw);
      if (!result) { errEl.textContent = 'ログインに失敗しました'; submitBtn.disabled = false; return; }
    } else {
      const result = await register(pw);
      if (!result) { errEl.textContent = '登録に失敗しました'; submitBtn.disabled = false; return; }
    }
    overlay.classList.add('hidden');
    await initGameAfterLogin();
  };
}

function initLoginButtons() {
  document.getElementById('tab-login-btn')?.addEventListener('click', () => showLoginOverlay('login'));
  document.getElementById('tab-register-btn')?.addEventListener('click', () => showLoginOverlay('register'));
  document.getElementById('tab-guest-btn')?.addEventListener('click', () => {
    document.getElementById('login-overlay')!.classList.add('hidden');
    // Game is already running in demo mode
  });
}

// ─── ゲーム初期化 ─────────────────────────────────────────────────────────────

async function initGameAfterLogin() {
  const { state: loaded, serverEpochMs } = await loadAndMerge();
  state = loaded;
  saveLocal(state);
  checkLoginBonus();

  if (serverEpochMs) {
    const offline = calculateOfflineResult(state, serverEpochMs);
    if (offline) {
      pendingOffline = offline;
      homePanel.showOfflineReward(offline);
    }
  }

  startGame();
  updateAllPanels();
}

function checkLoginBonus() {
  const today = todayString();
  if (state.lastLoginDate === today) return;
  const prev = state.lastLoginDate;
  const newStreak = prev === yesterdayString() ? state.loginStreak + 1 : 1;
  const tickets = loginBonusTickets(newStreak);
  const gems    = loginBonusGems(newStreak);
  state = {
    ...state,
    loginStreak:   newStreak,
    lastLoginDate: today,
    skipTickets:   skipTicketsAfterAdd(state, tickets),
    gems:          state.gems + gems,
  };
  saveLocal(state);
  showToast(`ログイン${newStreak}日連続！ スキップ券×${tickets}${gems > 0 ? ` 💎×${gems}` : ''}`);
}

function startGame() {
  if (gameStarted) return;
  gameStarted = true;

  // UI 初期化
  navbar.init();
  homePanel.init();
  weaponPanel.init();
  enhPanel.init();
  recipePanel.init();
  achPanel.init();
  settingsPanel.init();

  // コールバック設定
  wireCallbacks();

  // ゲームループ開始
  gameLoop.setAutoSaveCallback(() => {
    saveLocal(state);
    syncToApi(state);
  });
  gameLoop.setWorldTransitionCallback(worldId => {
    showWorldBanner(worldId);
  });
  gameLoop.start();

  // 定期UI更新 (500ms)
  clearInterval(updateIntervalId);
  updateIntervalId = setInterval(updateAllPanels, 500);

  // タブ閉じ時セーブ
  registerBeforeUnload(getState);
}

function wireCallbacks() {
  // Home
  homePanel.setOfflineCallback((doubled) => {
    if (!pendingOffline) return;
    state = collectOfflineEarnings(state, pendingOffline, doubled);
    pendingOffline = null;
    saveLocal(state);
    homePanel.hideOfflineReward();
    showToast('オフライン報酬を受け取りました');
  });
  homePanel.setMilestoneCallback((id) => {
    const def = MILESTONES.find(m => m.id === id);
    if (!def || !isMilestoneAchieved(state, def)) return;
    const claimed = new Set(state.milestonesClaimedIds);
    if (claimed.has(id)) return;
    state = { ...state, gems: state.gems + def.rewardGems, milestonesClaimedIds: [...claimed, id] };
    saveLocal(state);
    showToast(`マイルストーン達成！ 💎×${def.rewardGems}`);
  });
  homePanel.setDailyMissionCallback((id) => {
    const today = todayString();
    const missions = getDailyMissions(state, today);
    const m = missions.find(x => x.id === id);
    if (!m?.canClaim) return;
    const isToday = state.dailyDate === today;
    const currentClaimed = isToday && state.dailyMissionsClaimed
      ? state.dailyMissionsClaimed.split(',') : [];
    currentClaimed.push(id);
    state = {
      ...state,
      gems: state.gems + m.reward,
      skipTickets: m.rewardSkipTickets > 0 ? skipTicketsAfterAdd(state, m.rewardSkipTickets) : state.skipTickets,
      dailyDate: today,
      dailyMissionsClaimed: currentClaimed.join(','),
    };
    saveLocal(state);
    showToast(`ミッション達成！ 💎×${m.reward}${m.rewardSkipTickets > 0 ? ` 券×${m.rewardSkipTickets}` : ''}`);
  });

  // Weapon
  weaponPanel.setMergeCallback(() => {
    state = mergeWeapons(state);
    saveLocal(state);
  });
  weaponPanel.setExpandSlotsCallback(() => {
    const result = expandWeaponSlots(state);
    if (!result) { showToast('コインが不足しています'); return; }
    state = result;
    saveLocal(state);
    showToast(`スロット拡張 → ${state.weaponSlots}`);
  });
  weaponPanel.setAutoMergeFreeCallback(() => {
    const result = activateAutoMergeFree(state);
    if (!result) { showToast('使用できません（クールダウン中 or 使用済み）'); return; }
    state = result;
    saveLocal(state);
    showToast('自動合成を3分間起動しました');
  });
  weaponPanel.setAutoDeleteLevelCallback((lv) => {
    state = setAutoDeleteLevel(state, lv);
  });

  // Enhancement
  enhPanel.setUnlockStarCallback((star) => {
    if (!canUnlockStar(state, star)) return;
    const cost = star * 100;
    if (state.gems < cost) { showToast('ジェムが不足しています'); return; }
    const newLevels = { ...state.starGenLevels, [star]: 1 };
    state = { ...state, gems: state.gems - cost, starGenLevels: newLevels };
    saveLocal(state);
    showToast(`★${star} 解放！`);
  });
  enhPanel.setUpgradeStarCallback((star) => {
    if (!isStarUnlocked(state, star)) return;
    const lv = starGenLevel(state, star);
    if (lv >= 50) return;
    const cost = (lv + 1) * star * (lv < 10 ? 1 : 2);
    if (state.gems < cost) { showToast('ジェムが不足しています'); return; }
    const newLevels = { ...state.starGenLevels, [star]: lv + 1 };
    state = { ...state, gems: state.gems - cost, starGenLevels: newLevels };
    saveLocal(state);
  });
  enhPanel.setCoinAttackCallback(() => {
    if (state.coinAttackLevel >= COIN_ATTACK_MAX_LEVEL) return;
    const cost = coinAttackNextCost(state);
    if (state.coins < cost) { showToast('コインが不足しています'); return; }
    state = { ...state, coins: state.coins - cost, coinAttackLevel: state.coinAttackLevel + 1 };
    saveLocal(state);
  });
  enhPanel.setPrestigeUpgradeCallback((id) => {
    const lv = prestigeUpgradeLevel(state, id);
    if (lv >= prestigeUpgradeMax(id)) return;
    const cost = prestigeUpgradeCost(state, id);
    if (state.prestigeStones < cost) { showToast('輝石が不足しています'); return; }
    const newUpgrades = { ...state.prestigeUpgrades, [id]: lv + 1 };
    state = { ...state, prestigeStones: state.prestigeStones - cost, prestigeUpgrades: newUpgrades };
    saveLocal(state);
    showToast(`転生強化 Lv${lv + 1}`);
  });
  enhPanel.setRebirthCallback(() => {
    if (!canRebirth(state)) return;
    showDialog('転生する', '転生するとステージとコインがリセットされます。武器・輝石・アップグレードは保持されます。', [
      { label: '転生する', danger: true, onClick: () => {
        const next = rebirth(state);
        if (!next) return;
        state = next;
        saveLocal(state);
        gameLoop.reset();
        battleLog.length = 0;
        showToast('転生しました！');
      }},
    ]);
  });
  enhPanel.setRebirthSkipCallback(() => {
    const target = rebirthMaxSkipTarget(state);
    const cost   = rebirthSkipCost(state, target);
    if (state.gems < cost) { showToast('ジェムが不足しています'); return; }
    const next = performRebirthSkip(state);
    if (!next) return;
    state = next;
    saveLocal(state);
    showToast(`Stage ${target.toLocaleString()} へジャンプ！`);
  });
  enhPanel.setSwitchWorldCallback((w) => {
    const next = switchWorld(state, w);
    if (!next) return;
    state = next;
    saveLocal(state);
    gameLoop.reset();
    battleLog.length = 0;
    showToast(`ワールド移動`);
  });

  // Recipe
  recipePanel.setCraftCallback((id) => {
    const recipe = RECIPES.find(r => r.id === id);
    if (!recipe) return;
    if (!new Set(state.discoveredRecipeIds).has(id)) return;
    if (!canCraftRecipe(state, recipe)) { showToast('素材が不足しています'); return; }
    // 素材消費
    let next = { ...state };
    for (const req of recipe.materials) {
      switch (req.material) {
        case Material.IRON_FRAGMENT:   next = { ...next, ironFragments: next.ironFragments - req.amount }; break;
        case Material.SILVER_FRAGMENT: next = { ...next, silverFragments: next.silverFragments - req.amount }; break;
        case Material.GOLD_FRAGMENT:   next = { ...next, goldFragments: next.goldFragments - req.amount }; break;
        case Material.COIN:            next = { ...next, coins: next.coins - req.amount }; break;
        case Material.GEM:             next = { ...next, gems: next.gems - req.amount }; break;
        case Material.MAGIC_STONE:     next = { ...next, magicStoneFragments: next.magicStoneFragments - req.amount }; break;
        case Material.ANCIENT_CORE:    next = { ...next, ancientDragonCore: next.ancientDragonCore - req.amount }; break;
        case Material.STAR_CRYSTAL:    next = { ...next, starShatterCrystal: next.starShatterCrystal - req.amount }; break;
      }
    }
    // 結果適用
    const r = recipe.result;
    const now = Date.now();
    if (r.type === 'CoinBoost') {
      next = { ...next, craftCoinBoostEndTime: now + r.durationMs };
    } else if (r.type === 'AttackBoost') {
      next = { ...next, craftAttackBoostEndTime: now + r.durationMs, craftAttackBoostMultiplier: r.multiplier };
    } else if (r.type === 'AddWeapon') {
      const w = { ...next.weapons };
      w[r.starLevel] = (w[r.starLevel] ?? 0) + 1;
      next = { ...next, weapons: w };
    }
    state = next;
    saveLocal(state);
    showToast(`${recipe.name} をクラフトしました！`);
  });

  // Achievement
  achPanel.setClaimMilestoneCallback((id) => {
    const def = MILESTONES.find(m => m.id === id);
    if (!def || !isMilestoneAchieved(state, def)) return;
    const claimed = new Set(state.milestonesClaimedIds);
    if (claimed.has(id)) return;
    state = { ...state, gems: state.gems + def.rewardGems, milestonesClaimedIds: [...claimed, id] };
    saveLocal(state);
    showToast(`💎×${def.rewardGems} 獲得！`);
  });
  achPanel.setClaimAchievementCallback((id) => {
    const def = ACHIEVEMENTS.find(a => a.id === id);
    if (!def) return;
    const claimable = achievementClaimable(state, def);
    if (claimable <= 0) return;
    const newClaimed = { ...state.achievementsClaimed, [id]: (state.achievementsClaimed[id] ?? 0) + claimable };
    state = { ...state, gems: state.gems + def.rewardGems * claimable, achievementsClaimed: newClaimed };
    saveLocal(state);
    showToast(`実績報酬 💎×${def.rewardGems * claimable}！`);
  });

  // Settings
  settingsPanel.setStarDoorCallback(() => {
    const today = todayString();
    if (!canUseStarDoor(state, today)) return;
    const used = starDoorUsedToday(state, today);
    state = {
      ...state,
      skipTickets:       skipTicketsAfterAdd(state, 1),
      dailyStarDoorCount: used + 1,
      lastStarDoorDate:  today,
    };
    saveLocal(state);
    showToast('スキップ券を1枚獲得！');
  });
  settingsPanel.setCloudSaveCallback(() => {
    if (!isLoggedIn()) {
      showLoginOverlay('login');
    } else {
      syncToApi(state).then(() => showToast('クラウドセーブ完了'));
    }
  });
  settingsPanel.setLogoutCallback(() => {
    showDialog('ログアウト', 'ログアウトしますか？ゲストモードに切り替わります。', [
      { label: 'ログアウト', danger: true, onClick: () => {
        clearAuth();
        showToast('ログアウトしました');
        settingsPanel.update(state);
      }},
    ]);
  });
  settingsPanel.setDeleteAccountCallback(() => {
    showDialog('アカウント削除', 'アカウントを完全に削除します。この操作は取り消せません。', [
      { label: '削除する', danger: true, onClick: async () => {
        const ok = await deleteAccount();
        if (ok) showToast('アカウントを削除しました');
        else showToast('削除に失敗しました');
      }},
    ]);
  });
  settingsPanel.setResetGameCallback(() => {
    showDialog('データリセット', 'すべてのゲームデータを削除します。この操作は取り消せません。', [
      { label: 'リセット', danger: true, onClick: () => {
        state = createDefaultGameState();
        saveLocal(state);
        gameLoop.reset();
        battleLog.length = 0;
        showToast('データをリセットしました');
      }},
    ]);
  });
}

function updateAllPanels() {
  homePanel.update(state);
  if (navbar.getCurrent() === 'weapon')      weaponPanel.update(state);
  if (navbar.getCurrent() === 'enhancement') enhPanel.update(state);
  if (navbar.getCurrent() === 'recipe')      recipePanel.update(state);
  if (navbar.getCurrent() === 'achievement') achPanel.update(state);
  if (navbar.getCurrent() === 'settings')    settingsPanel.update(state);
}

// ─── Bootstrap ────────────────────────────────────────────────────────────────

async function main() {
  initLoginButtons();

  // Portfolio demo: always auto-start without requiring login
  document.getElementById('login-overlay')!.classList.add('hidden');

  if (isLoggedIn()) {
    await initGameAfterLogin();
  } else {
    const localJson = localStorage.getItem('sr_gamestate');
    if (localJson) {
      state = loadLocal();
    } else {
      // First-time visitor: demo-friendly starting state
      state = {
        ...createDefaultGameState(),
        starGenLevels: { 1: 10 },  // 10% per sec — fills weapon slots in ~10s
        coins: 500,
        gems: 5,
      };
    }
    checkLoginBonus();
    saveLocal(state);
    startGame();
  }
}

main().catch(console.error);
