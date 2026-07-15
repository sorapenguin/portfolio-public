// 戦闘アニメーション Canvas 描画
import {
  GameState, totalAttack, enemyHp, bossTypeFor, BossType,
  worldStageDisplay, worldDisplayName, monsterNameFor, weaponStarColor, worldInLoop,
  formatNumber,
} from '../types/GameState.js';

interface Particle {
  x: number; y: number;
  vx: number; vy: number;
  life: number; maxLife: number;
  size: number; color: string;
}

export class BattleCanvas {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private particles: Particle[] = [];
  private flashAlpha = 0;
  private enemyShake = 0;
  private lastRafId  = 0;

  constructor(canvasId: string) {
    this.canvas = document.getElementById(canvasId) as HTMLCanvasElement;
    this.ctx    = this.canvas.getContext('2d')!;
    this.resize();
    window.addEventListener('resize', () => this.resize());
  }

  private resize() {
    const parent = this.canvas.parentElement;
    if (!parent) return;
    const w = Math.min(parent.clientWidth - 16, 460);
    this.canvas.width  = w;
    this.canvas.height = Math.round(w * 0.39);
  }

  // 武器攻撃エフェクト（毎秒トリガー）
  spawnAttackParticles(s: GameState) {
    const starColor = weaponStarColor(worldInLoop(s.currentWorld));
    const cx = this.canvas.width * 0.65;
    const cy = this.canvas.height * 0.45;
    for (let i = 0; i < 8; i++) {
      const angle = (Math.PI * 2 * i) / 8 + Math.random() * 0.5;
      const speed = 1.5 + Math.random() * 2.5;
      this.particles.push({
        x: cx, y: cy,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed - 1,
        life: 1, maxLife: 1,
        size: 3 + Math.random() * 4,
        color: starColor,
      });
    }
  }

  // ボス撃破フラッシュ
  triggerBossFlash() {
    this.flashAlpha = 0.6;
    this.enemyShake = 8;
  }

  // メインレンダー（requestAnimationFrame から呼ばれる）
  render(s: GameState, accumulatedDmg: number) {
    const { width: w, height: h } = this.canvas;
    const ctx = this.ctx;

    // 背景
    ctx.fillStyle = '#0d0d1a';
    ctx.fillRect(0, 0, w, h);

    // 星背景（簡易）
    ctx.fillStyle = 'rgba(255,255,255,0.4)';
    for (let i = 0; i < 20; i++) {
      const sx = ((i * 73 + 11) % w);
      const sy = ((i * 53 + 7)  % h);
      ctx.fillRect(sx, sy, 1, 1);
    }

    const dispStage = worldStageDisplay(s.currentWorld, s.stage);
    const worldName = worldDisplayName(s.currentWorld);
    const bossT     = bossTypeFor(s.stage);
    const isBoss    = bossT !== null;
    const monName   = isBoss ? `[${bossT === BossType.LEGEND ? '伝説ボス' : bossT === BossType.MAJOR ? '大ボス' : '中ボス'}]` : monsterNameFor(s.stage);

    // World / Stage
    ctx.font = `bold ${Math.round(w * 0.033)}px sans-serif`;
    ctx.fillStyle = '#bb86fc';
    ctx.textAlign = 'left';
    ctx.fillText(`${worldName}  Stage ${dispStage.toLocaleString()}`, 8, 18);

    // 敵描画エリア (右側)
    const ex = w * 0.65 + (this.enemyShake > 0 ? (Math.random() - 0.5) * this.enemyShake : 0);
    const ey = h * 0.42;

    // 敵シルエット
    const size = isBoss ? Math.round(w * 0.15) : Math.round(w * 0.10);
    const bossColor = bossT === BossType.LEGEND ? '#ffd54f'
                    : bossT === BossType.MAJOR  ? '#ce93d8'
                    : bossT === BossType.MINOR  ? '#ef9a9a'
                    : '#64b5f6';
    ctx.fillStyle = bossColor;
    ctx.beginPath();
    ctx.arc(ex, ey, size, 0, Math.PI * 2);
    ctx.fill();

    // 敵名
    ctx.font = `${Math.round(w * 0.030)}px sans-serif`;
    ctx.fillStyle = '#e0e0e0';
    ctx.textAlign = 'center';
    ctx.fillText(monName, ex, ey - size - 6);

    // HP バー
    const hp    = enemyHp(s);
    const pct   = hp > 0 ? Math.max(0, 1 - accumulatedDmg / hp) : 0;
    const barW  = Math.round(w * 0.28);
    const barX  = ex - barW / 2;
    const barY  = ey + size + 8;
    const hpColor = pct > 0.5 ? '#81c784' : pct > 0.25 ? '#ffb74d' : '#ef5350';
    ctx.fillStyle = '#333';
    ctx.fillRect(barX, barY, barW, 6);
    ctx.fillStyle = hpColor;
    ctx.fillRect(barX, barY, Math.round(barW * pct), 6);

    // HP テキスト
    ctx.font = `${Math.round(w * 0.026)}px sans-serif`;
    ctx.fillStyle = '#aaa';
    ctx.textAlign = 'center';
    ctx.fillText(`HP: ${formatNumber(Math.max(0, hp - accumulatedDmg))} / ${formatNumber(hp)}`, ex, barY + 16);

    // ATK / EHP 左側
    const atk = totalAttack(s);
    ctx.textAlign = 'left';
    ctx.font = `${Math.round(w * 0.028)}px sans-serif`;
    ctx.fillStyle = '#e0e0e0';
    ctx.fillText(`ATK: ${formatNumber(atk)}`, 8, h - 28);
    const canWin = atk > hp;
    ctx.fillStyle = canWin ? '#81c784' : '#ef5350';
    ctx.fillText(canWin ? '勝利可能' : '攻撃力不足', 8, h - 12);

    // パーティクル
    ctx.save();
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i]!;
      p.x  += p.vx; p.y += p.vy;
      p.vy += 0.08; // gravity
      p.life -= 0.04;
      if (p.life <= 0) { this.particles.splice(i, 1); continue; }
      ctx.globalAlpha = p.life;
      ctx.fillStyle   = p.color;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size * p.life, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();

    // ボスフラッシュ
    if (this.flashAlpha > 0) {
      ctx.fillStyle = `rgba(255,255,255,${this.flashAlpha})`;
      ctx.fillRect(0, 0, w, h);
      this.flashAlpha -= 0.05;
    }

    if (this.enemyShake > 0) this.enemyShake -= 0.5;
  }
}
