/**
 * Hash router — loads HTML partials from /dashboards into #outlet.
 * Routes: #/executive, #/live, … #/demo
 */

const ROUTES = Object.freeze({
  executive: {
    title: "Executive Overview",
    partial: "dashboards/executive.html",
  },
  live: {
    title: "Live Transaction Monitor",
    partial: "dashboards/live.html",
  },
  events: {
    title: "Real-Time Event Stream",
    partial: "dashboards/events.html",
  },
  journey: {
    title: "Transaction Journey",
    partial: "dashboards/journey.html",
  },
  kafka: {
    title: "Kafka Monitoring Center",
    partial: "dashboards/kafka.html",
  },
  reconciliation: {
    title: "Reconciliation Dashboard",
    partial: "dashboards/reconciliation.html",
  },
  ledger: {
    title: "Ledger Dashboard",
    partial: "dashboards/ledger.html",
  },
  audit: {
    title: "Audit Dashboard",
    partial: "dashboards/audit.html",
  },
  errors: {
    title: "Error Intelligence Center",
    partial: "dashboards/errors.html",
  },
  health: {
    title: "Service Health Center",
    partial: "dashboards/health.html",
  },
  architecture: {
    title: "Architecture Flow",
    partial: "dashboards/architecture.html",
  },
  demo: {
    title: "Demo Mode",
    partial: "dashboards/demo.html",
  },
});

const ROUTE_NAMES = Object.keys(ROUTES);

let activeRouter = null;

/**
 * Parse location.hash into a route name, or null if invalid.
 * Accepts #/executive, #executive, or bare hash variants.
 */
export function parseHash(hash) {
  const raw = (hash ?? window.location.hash).replace(/^#/, "").replace(/^\//, "").trim();
  if (!raw) return null;
  const segment = raw.split(/[?#]/)[0].toLowerCase();
  return ROUTE_NAMES.includes(segment) ? segment : null;
}

export function getRouteConfig(name) {
  return ROUTES[name] ?? null;
}

export function getRouteNames() {
  return ROUTE_NAMES.slice();
}

export function navigate(name, { replace = false } = {}) {
  const route = ROUTES[name];
  if (!route) {
    console.warn("[router] Unknown route:", name);
    return;
  }
  const hash = `#/${name}`;
  if (replace) {
    window.location.replace(hash);
  } else if (window.location.hash !== hash) {
    window.location.hash = hash;
  } else if (activeRouter) {
    activeRouter.resolve();
  }
}

function buildPlaceholder(routeName, config) {
  return (
    `<div class="main-content__placeholder glass" data-route="${routeName}">` +
    `<h2 class="main-content__placeholder-title">${config.title}</h2>` +
    `<p class="main-content__placeholder-text">` +
    `Dashboard partial <span class="text-accent text-mono">${config.partial}</span> is not yet populated.` +
    `</p>` +
    `<span class="badge badge--info">Read-only</span>` +
    `</div>`
  );
}

function buildError(message) {
  return (
    `<div class="main-content__placeholder glass">` +
    `<h2 class="main-content__placeholder-title">Unable to load page</h2>` +
    `<p class="main-content__placeholder-text">${message}</p>` +
    `<span class="badge badge--failed">Error</span>` +
    `</div>`
  );
}

function buildLoader() {
  return (
    `<div class="main-content__placeholder glass" aria-busy="true">` +
    `<p class="main-content__placeholder-text text-secondary">Loading…</p>` +
    `</div>`
  );
}

export class Router {
  constructor(options = {}) {
    this.outlet =
      typeof options.outlet === "string"
        ? document.querySelector(options.outlet)
        : options.outlet;
    this.defaultRoute = options.defaultRoute ?? "executive";
    this.onRouteChange = options.onRouteChange ?? null;
    this.cache = options.cache !== false;
    this._partialCache = new Map();
    this.currentRoute = null;
    this._activeController = null;
    this._boundResolve = this.resolve.bind(this);
  }

  start() {
    if (!this.outlet) {
      throw new Error("[router] Outlet element not found");
    }

    activeRouter = this;

    window.addEventListener("hashchange", this._boundResolve);
    this.resolve();
  }

  stop() {
    window.removeEventListener("hashchange", this._boundResolve);
    this.unmountController();
    if (activeRouter === this) {
      activeRouter = null;
    }
  }

  async unmountController() {
    if (this._activeController?.unmount) {
      try {
        this._activeController.unmount();
      } catch (err) {
        console.error("[router] Controller unmount failed:", err);
      }
    }
    this._activeController = null;
  }

  async mountController(routeName, outlet) {
    await this.unmountController();

    try {
      const mod = await import(`../dashboards/${routeName}.js`);
      const controller = mod.default ?? mod;

      if (typeof controller.mount === "function") {
        this._activeController = controller;
        await controller.mount(outlet);
      }
    } catch (err) {
      /* Dashboard controller is optional per route */
    }
  }

  resolve() {
    let routeName = parseHash();

    if (!routeName) {
      navigate(this.defaultRoute, { replace: true });
      return;
    }

    this.load(routeName);
  }

  async load(routeName) {
    const config = ROUTES[routeName];
    if (!config) {
      navigate(this.defaultRoute, { replace: true });
      return;
    }

    if (this.currentRoute === routeName) {
      this.syncSidenav(routeName);
      return;
    }

    await this.unmountController();

    this.currentRoute = routeName;
    this.syncSidenav(routeName);
    this.setTitle(config.title);
    this.outlet.innerHTML = buildLoader();

    try {
      const html = await this.fetchPartial(routeName, config);
      this.outlet.innerHTML = html.trim() ? html : buildPlaceholder(routeName, config);
      await this.mountController(routeName, this.outlet);
    } catch (err) {
      console.error("[router] Failed to load partial:", config.partial, err);
      this.outlet.innerHTML = buildError(
        `Could not load <span class="text-mono">${config.partial}</span>. Check that the file exists and you are serving over HTTP.`
      );
    }

    if (typeof this.onRouteChange === "function") {
      this.onRouteChange(routeName, config);
    }
  }

  async fetchPartial(routeName, config) {
    if (this.cache && this._partialCache.has(routeName)) {
      return this._partialCache.get(routeName);
    }

    const response = await fetch(config.partial, {
      method: "GET",
      headers: { Accept: "text/html" },
      cache: "no-cache",
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const html = await response.text();

    if (this.cache) {
      this._partialCache.set(routeName, html);
    }

    return html;
  }

  syncSidenav(routeName) {
    const links = document.querySelectorAll(".sidenav__link[data-route]");
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

  setTitle(pageTitle) {
    document.title = `${pageTitle} · APEX Operations`;
  }

  clearCache(routeName) {
    if (routeName) {
      this._partialCache.delete(routeName);
    } else {
      this._partialCache.clear();
    }
  }
}
