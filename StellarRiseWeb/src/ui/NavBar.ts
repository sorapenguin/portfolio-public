// ナビゲーションバー
export type PanelId = 'home' | 'weapon' | 'enhancement' | 'recipe' | 'achievement' | 'settings';

export class NavBar {
  private current: PanelId = 'home';
  private onChangeCallback: ((id: PanelId) => void) | null = null;

  init() {
    const buttons = document.querySelectorAll<HTMLButtonElement>('.nav-btn[data-panel]');
    buttons.forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset['panel'] as PanelId;
        this.navigate(id);
      });
    });
  }

  navigate(id: PanelId) {
    if (this.current === id) return;
    this.current = id;

    // パネル切り替え
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    document.getElementById(`panel-${id}`)?.classList.add('active');

    // ナビボタン切り替え
    document.querySelectorAll<HTMLButtonElement>('.nav-btn[data-panel]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset['panel'] === id);
    });

    if (this.onChangeCallback) this.onChangeCallback(id);
  }

  getCurrent(): PanelId { return this.current; }

  onChange(cb: (id: PanelId) => void) { this.onChangeCallback = cb; }
}
