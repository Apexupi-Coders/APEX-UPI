/**
 * SideNav — reusable left navigation for hash-routed dashboards.
 */

export const NAV_ITEMS = Object.freeze([
  { route: "executive", label: "Overview", abbr: "OV" },
  { route: "live", label: "Live Txns", abbr: "LT" },
  { route: "events", label: "Event Stream", abbr: "EV" },
  { route: "journey", label: "Journey", abbr: "JR" },
  { route: "kafka", label: "Kafka Center", abbr: "KF" },
  { route: "reconciliation", label: "Reconciliation", abbr: "RC" },
  { route: "ledger", label: "Ledger", abbr: "LG" },
  { route: "audit", label: "Audit", abbr: "AU" },
  { route: "errors", label: "Errors", abbr: "ER" },
  { route: "health", label: "Health", abbr: "HL" },
  { route: "architecture", label: "Architecture", abbr: "AR" },
  { route: "demo", label: "Demo Mode", abbr: "DM" },
]);

const DEFAULTS = Object.freeze({
  headerLabel: "Command Center",
  footerText: "Read-only observability layer. No mutations permitted.",
  activeRoute: "executive",
});

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderNavLink(item, activeRoute) {
  const isActive = item.route === activeRoute;
  const activeClass = isActive ? " sidenav__link--active" : "";
  const ariaCurrent = isActive ? ' aria-current="page"' : "";

  return (
    `<li>` +
    `<a href="#/${escapeHtml(item.route)}" class="sidenav__link${activeClass}" data-route="${escapeHtml(item.route)}"${ariaCurrent}>` +
    `<span class="sidenav__icon" aria-hidden="true">${escapeHtml(item.abbr)}</span>` +
    `<span>${escapeHtml(item.label)}</span>` +
    `</a>` +
    `</li>`
  );
}

/**
 * @param {object} options
 * @param {string} [options.headerLabel]
 * @param {string} [options.footerText]
 * @param {string} [options.activeRoute]
 * @param {typeof NAV_ITEMS} [options.items]
 */
export function renderSideNav(options = {}) {
  const config = { ...DEFAULTS, ...options };
  const items = options.items ?? NAV_ITEMS;
  const links = items.map((item) => renderNavLink(item, config.activeRoute)).join("");

  return (
    `<div class="sidenav__header">` +
    `<span class="sidenav__label">${escapeHtml(config.headerLabel)}</span>` +
    `</div>` +
    `<nav class="sidenav__nav scrollbar">` +
    `<ul class="sidenav__list">${links}</ul>` +
    `</nav>` +
    `<div class="sidenav__footer">` +
    `<p class="sidenav__footer-text">${escapeHtml(config.footerText)}</p>` +
    `</div>`
  );
}

export class SideNav {
  /**
   * @param {HTMLElement|string} root
   * @param {object} [options]
   */
  constructor(root, options = {}) {
    this.root = typeof root === "string" ? document.querySelector(root) : root;
    this.options = { ...DEFAULTS, ...options };
    this.activeRoute = this.options.activeRoute;
  }

  mount() {
    if (!this.root) {
      throw new Error("[SideNav] Root element not found");
    }

    this.root.classList.add("sidenav");
    if (!this.root.id) {
      this.root.id = "sidenav";
    }
    this.root.setAttribute("aria-label", "Main navigation");
    this.root.innerHTML = renderSideNav(this.options);
    return this;
  }

  /**
   * @param {string} routeName
   */
  setActiveRoute(routeName) {
    if (!this.root || this.activeRoute === routeName) {
      if (this.root) this.syncActiveLink(routeName);
      return;
    }

    this.activeRoute = routeName;
    this.syncActiveLink(routeName);
  }

  syncActiveLink(routeName) {
    const links = this.root.querySelectorAll(".sidenav__link[data-route]");
    links.forEach((link) => {
      const active = link.dataset.route === routeName;
      link.classList.toggle("sidenav__link--active", active);
      if (active) {
        link.setAttribute("aria-current", "page");
      } else {
        link.removeAttribute("aria-current");
      }
    });
  }

  destroy() {
    if (this.root) {
      this.root.innerHTML = "";
    }
  }
}

/**
 * @param {HTMLElement|string} selector
 * @param {object} [options]
 */
export function mountSideNav(selector, options = {}) {
  const nav = new SideNav(selector, options);
  nav.mount();
  return nav;
}

/**
 * Mobile drawer toggle for responsive sidenav.
 * @param {object} refs
 * @param {HTMLElement} refs.shell
 * @param {HTMLElement} refs.toggle
 * @param {HTMLElement} [refs.overlay]
 */
export function bindMobileNav({ shell, toggle, overlay }) {
  if (!shell || !toggle) return () => {};

  function close() {
    shell.classList.remove("sidenav-open");
    toggle.setAttribute("aria-expanded", "false");
  }

  function onToggleClick() {
    const open = shell.classList.toggle("sidenav-open");
    toggle.setAttribute("aria-expanded", open ? "true" : "false");
  }

  toggle.addEventListener("click", onToggleClick);
  overlay?.addEventListener("click", close);
  window.addEventListener("hashchange", close);

  return close;
}
