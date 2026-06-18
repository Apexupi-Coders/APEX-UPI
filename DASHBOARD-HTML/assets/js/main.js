/**
 * APEX Operations Dashboard — bootstrap entry point.
 * Mounts shell components and starts the hash router.
 */

import "./mock/mockApi.js";
import { Router, parseHash } from "./core/router.js";
import { mountSideNav, bindMobileNav } from "./components/sidenav.js";
import { mountTopBar } from "./components/topbar.js";

function bootstrap() {
  const shell = document.getElementById("app-shell");
  const sidenavRoot = document.getElementById("sidenav");
  const topbarRoot = document.getElementById("topbar");
  const outlet = document.getElementById("outlet");

  if (!outlet) {
    console.error("[main] #outlet not found — router cannot start");
    return;
  }

  const initialRoute = parseHash() ?? "executive";

  const sidenav = sidenavRoot
    ? mountSideNav(sidenavRoot, { activeRoute: initialRoute })
    : null;

  const topbar = topbarRoot ? mountTopBar(topbarRoot) : null;

  topbar?.onSearch((query, meta) => {
    document.dispatchEvent(
      new CustomEvent("apex:search", { detail: { query, meta } })
    );
  });

  bindMobileNav({
    shell,
    toggle: document.getElementById("sidenav-toggle"),
    overlay: document.getElementById("sidenav-overlay"),
  });

  const router = new Router({
    outlet,
    defaultRoute: "executive",
    cache: true,
    onRouteChange(routeName, config) {
      outlet.dataset.route = routeName;
      outlet.dataset.title = config.title;
      sidenav?.setActiveRoute(routeName);
      topbar?.setBranding({ subtitle: config.title });
    },
  });

  router.start();
}

bootstrap();
