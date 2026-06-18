/** Architecture Flow — static visualization, no API */

let root = null;
let animTimer = null;

function animateFlow() {
  const nodes = root?.querySelectorAll("[data-flow-node]");
  if (!nodes?.length) return;

  let idx = 0;
  nodes.forEach((n) => n.classList.remove("architecture__node--active"));

  function tick() {
    nodes.forEach((n) => n.classList.remove("architecture__node--active"));
    nodes[idx % nodes.length].classList.add("architecture__node--active");
    idx += 1;
  }

  tick();
  animTimer = window.setInterval(tick, 1200);
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='architecture']") ?? container;
  animateFlow();
  return root;
}

export function unmount() {
  if (animTimer) clearInterval(animTimer);
  animTimer = null;
  root = null;
}

export default { mount, unmount };
