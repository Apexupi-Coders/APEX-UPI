# Theme — Dark Navy

## Color Tokens

Defined in `assets/css/core/variables.css`:

| Token | Value | Usage |
|---|---|---|
| `--color-bg-deep` | `#060b14` | Page background |
| `--color-accent` | `#8b5cf6` | Purple accent |
| `--color-highlight` | `#22d3ee` | Cyan highlights |
| `--color-success` | `#34d399` | SUCCESS / UP |
| `--color-error` | `#f87171` | FAILED / DOWN |
| `--color-pending` | `#fb923c` | PENDING |

## Glassmorphism

```css
background: rgba(15, 23, 41, 0.72);
backdrop-filter: blur(16px);
border: 1px solid rgba(148, 163, 184, 0.12);
border-radius: 0.75rem;
```

Utility classes: `.glass`, `.glass--strong`, `.glass--accent`, `.glass--highlight`

## Typography

- Sans: Segoe UI, system-ui
- Mono: Cascadia Code, Consolas (transaction IDs, references)
