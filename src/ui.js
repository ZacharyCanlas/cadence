/**
 * Small DOM builders.
 *
 * The option lists are rendered from the data modules rather than written out
 * in the markup, so adding a sound or an accent pattern is a one-line change in
 * one place and the two can never drift apart.
 */

export const $ = (id) => document.getElementById(id);

export function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value !== null && value !== undefined) node.setAttribute(key, value);
  }
  for (const child of [].concat(children)) {
    if (child) node.append(child);
  }
  return node;
}

/**
 * A row of mutually exclusive options, keyboard navigable with the arrow keys
 * the way a native radio group is.
 *
 * @param {HTMLElement} container
 * @param {{value: any, label: string, sub?: string}[]} items
 * @param {(value: any) => void} onChange
 */
export function segmented(container, items, onChange) {
  container.replaceChildren();
  const buttons = items.map((item) =>
    el(
      'button',
      {
        type: 'button',
        class: 'option',
        role: 'radio',
        'aria-checked': 'false',
        'data-value': String(item.value),
        onclick: () => onChange(item.value),
      },
      [
        el('span', { class: 'option-label', text: item.label }),
        item.sub ? el('span', { class: 'option-sub', text: item.sub }) : null,
      ],
    ),
  );
  container.append(...buttons);

  container.addEventListener('keydown', (event) => {
    const offset = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
    if (!offset) return;
    const current = buttons.findIndex((button) => button.getAttribute('aria-checked') === 'true');
    const next = buttons[(current + offset + buttons.length) % buttons.length];
    event.preventDefault();
    next.focus();
    onChange(items[buttons.indexOf(next)].value);
  });

  return {
    setValue(value) {
      buttons.forEach((button, index) => {
        const active = items[index].value === value;
        button.setAttribute('aria-checked', String(active));
        // Only the selected option stays in the tab order, which is how a
        // radio group is meant to behave.
        button.tabIndex = active ? 0 : -1;
      });
    },
  };
}

/**
 * An on/off control. Uses a button with `role="switch"` rather than a checkbox
 * so the whole row is one large tap target.
 */
export function toggle({ label, hint, checked = false, disabled = false, onChange }) {
  const state = el('span', { class: 'switch-state', text: checked ? 'On' : 'Off' });
  const button = el(
    'button',
    {
      type: 'button',
      class: 'switch',
      role: 'switch',
      'aria-checked': String(checked),
      disabled: disabled ? '' : null,
      onclick: () => onChange(button.getAttribute('aria-checked') !== 'true'),
    },
    [
      el('span', { class: 'switch-text' }, [
        el('span', { class: 'switch-label', text: label }),
        hint ? el('span', { class: 'switch-hint', text: hint }) : null,
      ]),
      state,
    ],
  );

  return {
    node: button,
    set(value, { hint: nextHint, disabled: nextDisabled } = {}) {
      button.setAttribute('aria-checked', String(value));
      state.textContent = value ? 'On' : 'Off';
      if (nextHint !== undefined) {
        const hintNode = button.querySelector('.switch-hint');
        if (hintNode) hintNode.textContent = nextHint;
      }
      if (nextDisabled !== undefined) button.disabled = nextDisabled;
    },
  };
}

/** A status line in the diagnostics panel. */
export function check({ tone = 'idle', title, detail }) {
  return el('li', { class: 'check', 'data-tone': tone }, [
    el('span', { class: 'dot', 'aria-hidden': 'true' }),
    el('span', { class: 'check-text' }, [
      el('b', { text: title }),
      detail ? el('span', { class: 'check-detail', text: detail }) : null,
    ]),
  ]);
}

export function formatClock(seconds) {
  const whole = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(whole / 60);
  return `${minutes}:${String(whole % 60).padStart(2, '0')}`;
}

export function formatSteps(count) {
  return `${count.toLocaleString('en-US')} ${count === 1 ? 'step' : 'steps'}`;
}
