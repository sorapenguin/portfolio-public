// ゲームループ — requestAnimationFrame ベース (Kotlin MainViewModel.startTicking() 相当)
// 毎秒: processSecond()
// 毎60秒: processBattle()
// 30秒ごと: SaveManager.sync()
import { GameState } from '../types/GameState.js';
import { processBattle, BattleTickResult } from './BattleEngine.js';
import { processSecond } from './WeaponSystem.js';
import { enterWorld } from './RebirthSystem.js';
import { worldOf, MAX_WORLD } from '../types/GameState.js';

export type StateUpdater = (updater: (s: GameState) => GameState) => void;
export type BattleLogPush = (entry: string) => void;
export type WorldTransitionHandler = (newWorldId: number) => void;

// Demo mode: battle fires every 5s instead of 60s for fast portfolio experience
const BATTLE_INTERVAL_SECS = 5;

export class GameLoop {
  private running = false;
  private rafId   = 0;
  private lastTickMs = 0;
  private elapsedMsAccum = 0;  // sub-second accumulator
  private secondCount = 0;
  private minuteCount = 0;
  private lastAutoSaveMs = 0;
  private onAutoSave: (() => void) | null = null;
  private onWorldTransition: WorldTransitionHandler | null = null;
  private onBattleResult: ((result: BattleTickResult) => void) | null = null;

  constructor(
    private getState: () => GameState,
    private updateState: StateUpdater,
    private pushLog: BattleLogPush,
  ) {}

  setAutoSaveCallback(cb: () => void) { this.onAutoSave = cb; }
  setWorldTransitionCallback(cb: WorldTransitionHandler) { this.onWorldTransition = cb; }
  setBattleResultCallback(cb: (r: BattleTickResult) => void) { this.onBattleResult = cb; }

  start() {
    if (this.running) return;
    this.running = true;
    this.lastTickMs = performance.now();
    this.rafId = requestAnimationFrame(this.tick.bind(this));
  }

  stop() {
    this.running = false;
    cancelAnimationFrame(this.rafId);
  }

  reset() {
    this.secondCount = 0;
    this.minuteCount = 0;
  }

  private tick(now: number) {
    if (!this.running) return;

    const delta = now - this.lastTickMs;
    this.lastTickMs = now;
    this.elapsedMsAccum += delta;

    // 1秒ごとに処理（最大3秒分まとめて処理してキャッチアップ）
    const maxCatchup = 3;
    let processed = 0;
    while (this.elapsedMsAccum >= 1000 && processed < maxCatchup) {
      this.elapsedMsAccum -= 1000;
      this.secondCount++;
      processed++;

      // processSecond
      this.updateState(s => {
        const { state } = processSecond(s, this.secondCount);
        return state;
      });

      // processBattle (BATTLE_INTERVAL_SECS ごと)
      if (this.secondCount % BATTLE_INTERVAL_SECS === 0) {
        this.minuteCount++;
        const s = this.getState();
        const result = processBattle(s, this.minuteCount);
        let finalState = result.newState;

        // ワールド移行
        if (result.worldTransition !== null) {
          const targetWorld = result.worldTransition;
          if (targetWorld > finalState.currentWorld && targetWorld <= MAX_WORLD) {
            finalState = enterWorld(finalState, targetWorld, finalState.stage);
            if (this.onWorldTransition) this.onWorldTransition(targetWorld);
          }
        }

        this.updateState(() => finalState);
        this.pushLog(result.logEntry);
        if (this.onBattleResult) this.onBattleResult(result);
      }
    }

    // 30秒ごと自動セーブ
    if (now - this.lastAutoSaveMs >= 30_000) {
      this.lastAutoSaveMs = now;
      if (this.onAutoSave) this.onAutoSave();
    }

    this.rafId = requestAnimationFrame(this.tick.bind(this));
  }

  getMinuteCount(): number { return this.minuteCount; }
  getSecondCount(): number { return this.secondCount; }
}
