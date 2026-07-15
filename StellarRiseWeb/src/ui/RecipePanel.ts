// レシピパネル — Kotlin WeaponFragment (レシピタブ) の移植
import {
  GameState, RECIPES, canCraftRecipe, recipeResultDescription,
  fragmentAmount, MATERIAL_LABELS,
  formatNumber,
} from '../types/GameState.js';

export class RecipePanel {
  private getState: () => GameState;
  private onCraft: ((id: string) => void) | null = null;

  constructor(getState: () => GameState) { this.getState = getState; }

  setCraftCallback(cb: (id: string) => void) { this.onCraft = cb; }

  init() {}

  update(s: GameState) {
    this.renderMaterials(s);
    this.renderRecipes(s);
  }

  private renderMaterials(s: GameState) {
    const set = (id: string, v: string) => {
      const el = document.getElementById(id);
      if (el) el.textContent = v;
    };
    set('mat-iron',    s.ironFragments.toString());
    set('mat-silver',  s.silverFragments.toString());
    set('mat-gold',    s.goldFragments.toString());
    set('mat-magic',   s.magicStoneFragments.toString());
    set('mat-ancient', s.ancientDragonCore.toString());
    set('mat-crystal', s.starShatterCrystal.toString());
  }

  private renderRecipes(s: GameState) {
    const list = document.getElementById('recipe-list');
    if (!list) return;

    const discovered = new Set(s.discoveredRecipeIds);
    list.innerHTML = RECIPES.map(r => {
      const isDiscovered = discovered.has(r.id);
      const craftable    = isDiscovered && canCraftRecipe(s, r);
      const matText = r.materials.map(req => {
        const have = fragmentAmount(s, req.material);
        const ok   = have >= req.amount;
        return `<span style="color:${ok ? 'var(--success)' : 'var(--danger)'}">${MATERIAL_LABELS[req.material]} ×${req.amount} (${have})</span>`;
      }).join(' / ');

      return `<div class="recipe-item${isDiscovered ? '' : ' recipe-locked'}">
        <div class="recipe-name">${r.name}</div>
        <div class="recipe-result">${recipeResultDescription(r)}</div>
        ${isDiscovered
          ? `<div class="recipe-materials">${matText}</div>
             <button class="btn btn-sm" data-craft="${r.id}" ${craftable ? '' : 'disabled'}>クラフト</button>`
          : `<div class="recipe-materials" style="color:var(--text-dim);font-size:11px">
               Stage ${r.unlockStage.toLocaleString()} で解放 / ${r.hint}
             </div>`
        }
      </div>`;
    }).join('');

    list.querySelectorAll<HTMLButtonElement>('button[data-craft]').forEach(btn => {
      btn.addEventListener('click', () => this.onCraft?.(btn.dataset['craft']!));
    });
  }
}
