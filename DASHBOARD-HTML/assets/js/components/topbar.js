/**
 * TopBar — reusable sticky header with brand, search, and status.
 */

const ICONS = Object.freeze({
  menu: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>`,
  search: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`,
});

const DEFAULTS = Object.freeze({
  title: "APEX Operations",
  subtitle: "UPI Payment Observability",
  searchPlaceholder: "Search transactions, IDs, references…",
  searchEnabled: true,
  statusLabel: "Live",
  statusLive: true,
  statusTitle: "All systems operational",
  roleBadge: "Operator",
  menuButtonId: "sidenav-toggle",
  searchInputId: "topbar-search",
});

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderSearchBlock(config) {
  if (!config.searchEnabled) {
    return `<div class="topbar__center"></div>`;
  }

  return (
    `<div class="topbar__center">` +
    `<form class="topbar__search" role="search" data-topbar-search>` +
    `${ICONS.search}` +
    `<label class="sr-only" for="${escapeHtml(config.searchInputId)}">Search dashboard</label>` +
    `<input` +
    ` type="search"` +
    ` class="topbar__search-input"` +
    ` id="${escapeHtml(config.searchInputId)}"` +
    ` name="q"` +
    ` placeholder="${escapeHtml(config.searchPlaceholder)}"` +
    ` autocomplete="off"` +
    ` spellcheck="false"` +
    `/>` +
    `</form>` +
    `</div>`
  );
}

function renderStatusBlock(config) {
  const dotClass = config.statusLive ? "topbar__status-dot" : "topbar__status-dot topbar__status-dot--offline";

  return (
    `<div class="topbar__actions">` +
    `<div class="topbar__status" title="${escapeHtml(config.statusTitle)}" data-topbar-status>` +
    `<span class="${dotClass}" aria-hidden="true"></span>` +
    `<span data-topbar-status-label>${escapeHtml(config.statusLabel)}</span>` +
    `</div>` +
    (config.roleBadge
      ? `<span class="topbar__badge" data-topbar-role>${escapeHtml(config.roleBadge)}</span>`
      : "") +
    `</div>`
  );
}

/**
 * @param {object} [options]
 */
export function renderTopBar(options = {}) {
  const config = { ...DEFAULTS, ...options };

  return (
    `<button` +
    ` class="topbar__menu-btn"` +
    ` id="${escapeHtml(config.menuButtonId)}"` +
    ` type="button"` +
    ` aria-label="Toggle navigation menu"` +
    ` aria-expanded="false"` +
    ` aria-controls="sidenav"` +
    `>` +
    `${ICONS.menu}` +
    `</button>` +
    `<div class="topbar__brand">` +
    `<div class="topbar__logo" role="img" aria-label="APEX logo"></div>` +
    `<div class="topbar__title-group">` +
    `<h1 class="topbar__title" data-topbar-title>${escapeHtml(config.title)}</h1>` +
    `<span class="topbar__subtitle" data-topbar-subtitle>${escapeHtml(config.subtitle)}</span>` +
    `</div>` +
    `</div>` +
    renderSearchBlock(config) +
    renderStatusBlock(config)
  );
}

export class TopBar {
  /**
   * @param {HTMLElement|string} root
   * @param {object} [options]
   */
  constructor(root, options = {}) {
    this.root = typeof root === "string" ? document.querySelector(root) : root;
    this.options = { ...DEFAULTS, ...options };
    this._searchHandler = null;
    this._debounceTimer = null;
    this._onSearchSubmit = this._onSearchSubmit.bind(this);
    this._onSearchInput = this._onSearchInput.bind(this);
  }

  mount() {
    if (!this.root) {
      throw new Error("[TopBar] Root element not found");
    }

    this.root.classList.add("topbar");
    this.root.setAttribute("role", "banner");
    this.root.innerHTML = renderTopBar(this.options);
    this._bindSearch();
    return this;
  }

  _bindSearch() {
    const form = this.root.querySelector("[data-topbar-search]");
    const input = this.root.querySelector(`#${this.options.searchInputId}`);

    if (!form || !input) return;

    form.addEventListener("submit", this._onSearchSubmit);
    input.addEventListener("input", this._onSearchInput);
  }

  _onSearchSubmit(event) {
    event.preventDefault();
    const input = this.root.querySelector(`#${this.options.searchInputId}`);
    if (input && typeof this._searchHandler === "function") {
      this._searchHandler(input.value.trim(), { source: "submit" });
    }
  }

  _onSearchInput(event) {
    if (typeof this._searchHandler !== "function") return;

    clearTimeout(this._debounceTimer);
    const value = event.target.value.trim();
    this._debounceTimer = setTimeout(() => {
      this._searchHandler(value, { source: "input" });
    }, 300);
  }

  /**
   * @param {(query: string, meta: { source: string }) => void} handler
   */
  onSearch(handler) {
    this._searchHandler = handler;
    return this;
  }

  /**
   * @param {object} status
   * @param {string} [status.label]
   * @param {boolean} [status.live]
   * @param {string} [status.title]
   */
  setStatus({ label, live, title } = {}) {
    const statusEl = this.root.querySelector("[data-topbar-status]");
    const labelEl = this.root.querySelector("[data-topbar-status-label]");
    const dotEl = this.root.querySelector(".topbar__status-dot");

    if (label !== undefined && labelEl) {
      labelEl.textContent = label;
    }

    if (title !== undefined && statusEl) {
      statusEl.title = title;
    }

    if (live !== undefined && dotEl) {
      dotEl.classList.toggle("topbar__status-dot--offline", !live);
    }
  }

  /**
   * @param {object} branding
   * @param {string} [branding.title]
   * @param {string} [branding.subtitle]
   */
  setBranding({ title, subtitle } = {}) {
    const titleEl = this.root.querySelector("[data-topbar-title]");
    const subtitleEl = this.root.querySelector("[data-topbar-subtitle]");

    if (title !== undefined && titleEl) {
      titleEl.textContent = title;
    }

    if (subtitle !== undefined && subtitleEl) {
      subtitleEl.textContent = subtitle;
    }
  }

  getSearchValue() {
    const input = this.root.querySelector(`#${this.options.searchInputId}`);
    return input ? input.value.trim() : "";
  }

  setSearchValue(value) {
    const input = this.root.querySelector(`#${this.options.searchInputId}`);
    if (input) {
      input.value = value;
    }
  }

  destroy() {
    clearTimeout(this._debounceTimer);
    const form = this.root?.querySelector("[data-topbar-search]");
    const input = this.root?.querySelector(`#${this.options.searchInputId}`);
    form?.removeEventListener("submit", this._onSearchSubmit);
    input?.removeEventListener("input", this._onSearchInput);
    if (this.root) {
      this.root.innerHTML = "";
    }
  }
}

/**
 * @param {HTMLElement|string} selector
 * @param {object} [options]
 */
export function mountTopBar(selector, options = {}) {
  const bar = new TopBar(selector, options);
  bar.mount();
  return bar;
}
