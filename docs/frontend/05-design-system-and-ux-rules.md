# VinFast QC Copilot Frontend — Design System and UX Rules

**Document:** `docs/frontend/05-design-system-and-ux-rules.md`  
**Status:** Reviewed and implementation-ready  
**Applies to:** `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document defines the visual and interaction rules for the VinFast QC Copilot frontend.

It covers:

- the exact visual direction;
- light and dark color tokens;
- application shell and navigation states;
- hover, active, selected, pressed, focus, and disabled behavior;
- typography and iconography;
- spacing, borders, radii, and shadows;
- buttons, inputs, tables, cards, menus, tabs, and scrollbars;
- loading, empty, error, and permission states;
- accessibility and responsive behavior;
- TestRun, dataset, evaluator, target, review, and export UX.

This document is normative for MVP implementation. When a component is not explicitly described, it must follow the closest existing component pattern rather than introduce a new visual language.

---

## 2. Product design direction

VinFast QC Copilot is an internal operational application, not a marketing website.

The canonical direction is:

```text
ChatGPT:
  calm application shell
  quiet surfaces
  restrained controls
  content-first hierarchy

Notion:
  warm neutral palette
  compact navigation
  subtle hover states
  off-black text
  flat surfaces

Jira:
  strong project context
  clear selected navigation
  practical information density
  table and workflow usability
  blue used as an operational accent
```

The combined result must feel:

```text
neutral
warm rather than blue-heavy
flat rather than card-heavy
compact but readable
predictable
professional
safe for sensitive configuration
optimized for inspection and comparison
```

### 2.1 Canonical visual formula

Use:

```text
Notion/ChatGPT warm-neutral foundation
+ Jira-style operational hierarchy
+ a small amount of Jira blue for focus, links, and selected context
```

Do not create three competing visual themes.

### 2.2 What to adopt from the provided references

Adopt:

```text
warm monochrome surfaces
off-black text instead of absolute black
muted pastel semantic colors
crisp 1 px borders
small 6–10 px radii
almost no decorative shadow
plain and specific product language
no emojis
motion based on transform and opacity only
clear button contrast
```

Do not adopt:

```text
AIDA marketing-page structure
cinematic hero sections
massive py-32 or py-48 section spacing
randomized layouts
GSAP scroll pinning or scrubbing
infinite marquees
floating glass navigation
editorial serif hero headings
marketing bento grids
ambient animated blobs
scroll-entry animation on every content block
```

Those patterns belong to campaign or portfolio pages, not a dense internal QC application.

### 2.3 Explicit visual bans

Do not use:

```text
gradients
glassmorphism
neon colors
large colored page backgrounds
heavy drop shadows
large pill-shaped cards or buttons
excessive rounded containers
emoji as interface icons
decorative stock photography
animated background effects
marketing-style hero sections
multiple icon libraries
zebra-striped data tables by default
```

---

## 3. Brand usage

Use the approved VinFast logo asset.

Rules:

1. Preserve the original aspect ratio.
2. Do not redraw, stretch, recolor, outline, or add shadows.
3. Use an approved light-background or dark-background variant.
4. Keep the logo visually secondary to application content.
5. Do not use the logo as a decorative watermark.
6. Do not create an unapproved VinFast gradient or branded background.
7. The logo area must not become a large marketing header.

The product name displayed in the shell:

```text
VinFast QC Copilot
```

---

## 4. Theme support

Required themes:

```text
Light
Dark
System
```

Default:

```text
Light
```

Theme preference may be stored locally because it is non-sensitive.

Recommended key:

```text
vfqc.theme
```

Allowed values:

```text
light
dark
system
```

Theme changes apply without page reload.

The `System` option follows `prefers-color-scheme` and reacts to operating-system changes while selected.

---

## 5. Canonical color system

Use semantic CSS variables. Feature code must not scatter raw hex values.

The values below define the intended appearance. They may be converted to OKLCH for implementation, but the visible result must stay equivalent.

### 5.1 Light theme

| Token | Value | Intended use |
|---|---|---|
| `--background` | `#FFFFFF` | Main application canvas |
| `--sidebar` | `#F7F7F5` | Left navigation |
| `--topbar` | `rgba(255,255,255,0.92)` | Sticky context bar |
| `--surface` | `#FFFFFF` | Cards, dialogs, inputs |
| `--surface-subtle` | `#FAFAF9` | Table headers, secondary panels |
| `--surface-hover` | `#F1F1EF` | General hover background |
| `--surface-pressed` | `#E8E8E5` | Pointer-down state |
| `--surface-selected` | `#E9E9E7` | Selected navigation or neutral selection |
| `--surface-selected-accent` | `#E9F2FF` | Selected data row or blue-accent context |
| `--foreground` | `#202020` | Main text |
| `--foreground-secondary` | `#5F5E5B` | Secondary text |
| `--foreground-muted` | `#787774` | Metadata and placeholders |
| `--foreground-disabled` | `#A8A8A5` | Disabled content |
| `--border` | `#E7E7E5` | Default divider and border |
| `--border-strong` | `#D6D6D3` | Hovered controls and stronger separation |
| `--primary` | `#202020` | Primary button |
| `--primary-hover` | `#343434` | Primary button hover |
| `--primary-active` | `#111111` | Primary button pressed |
| `--primary-foreground` | `#FFFFFF` | Text on primary button |
| `--accent` | `#0C66E4` | Focus, links, selected indicators |
| `--accent-hover` | `#0055CC` | Accent hover |
| `--accent-soft` | `#E9F2FF` | Soft selected background |
| `--focus-ring` | `#579DFF` | Keyboard focus ring |
| `--overlay` | `rgba(15,15,15,0.42)` | Dialog overlay |

### 5.2 Dark theme

| Token | Value | Intended use |
|---|---|---|
| `--background` | `#191919` | Main application canvas |
| `--sidebar` | `#202020` | Left navigation |
| `--topbar` | `rgba(25,25,25,0.92)` | Sticky context bar |
| `--surface` | `#232323` | Cards, dialogs, inputs |
| `--surface-subtle` | `#292929` | Secondary panels |
| `--surface-hover` | `#2F2F2F` | General hover background |
| `--surface-pressed` | `#383838` | Pointer-down state |
| `--surface-selected` | `#343434` | Selected navigation |
| `--surface-selected-accent` | `rgba(87,157,255,0.16)` | Selected data row |
| `--foreground` | `#ECECEC` | Main text |
| `--foreground-secondary` | `#C2C2C2` | Secondary text |
| `--foreground-muted` | `#A1A1A1` | Metadata and placeholders |
| `--foreground-disabled` | `#696969` | Disabled content |
| `--border` | `#353535` | Default divider and border |
| `--border-strong` | `#484848` | Hovered controls |
| `--primary` | `#ECECEC` | Primary button |
| `--primary-hover` | `#FFFFFF` | Primary button hover |
| `--primary-active` | `#D8D8D8` | Primary button pressed |
| `--primary-foreground` | `#171717` | Text on primary button |
| `--accent` | `#579DFF` | Focus, links, selected indicators |
| `--accent-hover` | `#85B8FF` | Accent hover |
| `--accent-soft` | `rgba(87,157,255,0.16)` | Soft selected background |
| `--focus-ring` | `#85B8FF` | Keyboard focus ring |
| `--overlay` | `rgba(0,0,0,0.64)` | Dialog overlay |

### 5.3 Semantic status colors

Use muted backgrounds. Never fill a large panel with a saturated status color.

#### Light

| Meaning | Background | Text | Border |
|---|---|---|---|
| Success / passed / completed | `#EDF3EC` | `#346538` | `#D5E5D3` |
| Warning / queued / cancel requested | `#FBF3DB` | `#7F5F01` | `#EBDDAD` |
| Danger / run failed / execution error | `#FDEBEC` | `#9F2F2D` | `#F4CCCF` |
| Information / running | `#E9F2FF` | `#0C66E4` | `#C7DCFF` |
| Judge-specific | `#F3E8FF` | `#6E3CBC` | `#E2CCFF` |
| Neutral / cancelled / draft | `#F1F1EF` | `#5F5E5B` | `#E1E1DE` |

#### Dark

| Meaning | Background | Text |
|---|---|---|
| Success | `rgba(91,171,111,0.16)` | `#A5D6AF` |
| Warning | `rgba(246,196,83,0.15)` | `#F6C453` |
| Danger | `rgba(232,92,92,0.15)` | `#FFB4B4` |
| Information | `rgba(87,157,255,0.16)` | `#85B8FF` |
| Judge-specific | `rgba(179,126,235,0.16)` | `#D7B7FF` |
| Neutral | `#2F2F2F` | `#C2C2C2` |

### 5.4 Color usage rules

1. Neutral color is the default.
2. Blue is an operational accent, not a page-background color.
3. Primary actions use near-black in light mode and near-white in dark mode.
4. Status meaning must not rely on color alone.
5. Do not use the same red treatment for a testcase evaluation failure and a run-level system failure without explicit labels.
6. Do not use green for generic primary buttons.
7. Do not use a colored border around every card.
8. Preserve at least WCAG AA contrast for text and controls.

---

## 6. Typography

The application uses one sans-serif UI family, not an editorial serif.

Preferred stack:

```css
font-family:
  "Geist Sans",
  "SF Pro Text",
  -apple-system,
  BlinkMacSystemFont,
  "Segoe UI",
  sans-serif;
```

If `Geist Sans` is not bundled, use the system stack without introducing a remote runtime font dependency.

Monospace stack:

```css
font-family:
  "Geist Mono",
  "SFMono-Regular",
  Consolas,
  "Liberation Mono",
  monospace;
```

Use monospace for:

```text
IDs
JSON
cURL
HTTP headers
paths
event names
trace IDs
code-like values
```

Recommended hierarchy:

| Role | Size | Weight | Line height |
|---|---:|---:|---:|
| Page title | 24 px | 650 | 1.25 |
| Dense page title | 22 px | 650 | 1.25 |
| Section title | 18 px | 600 | 1.35 |
| Card title | 15 px | 600 | 1.4 |
| Body | 14 px | 400 | 1.5 |
| Dense table body | 13 px | 400 | 1.45 |
| Label | 13 px | 500 | 1.4 |
| Caption/meta | 12 px | 400–500 | 1.4 |
| Code | 12–13 px | 400 | 1.5 |

Rules:

1. Use off-black, never `#000000`, for normal text.
2. Do not use serif fonts in the product shell.
3. Do not use uppercase for long labels.
4. Use sentence case for headings and actions.
5. Use negative letter spacing only on page titles, at most `-0.02em`.
6. Truncate only when a full-value affordance exists.
7. Do not use giant marketing headings.
8. Page titles should normally remain one line on desktop and at most two lines on smaller screens.

---

## 7. Iconography

The project standard uses Lucide.

Rules:

1. Use Lucide only; do not mix Lucide, Phosphor, Heroicons, and Radix icon sets.
2. Default icon size is `16 px`.
3. Navigation and prominent controls may use `18 px`.
4. Default stroke width is `1.75`.
5. Use filled icons only when the chosen Lucide icon provides an intentional filled state.
6. Icons inherit text color.
7. Icon-only buttons require an accessible name.
8. Do not use emojis as interface icons.
9. Do not place icons on every label merely for decoration.

---

## 8. Spacing, radius, border, and elevation

Use a 4 px base grid.

```text
4, 8, 12, 16, 20, 24, 32, 40, 48
```

### 8.1 Density

```text
navigation:
  compact

tables:
  compact by default

forms:
  comfortable

detail panels:
  comfortable

JSON and code:
  compact
```

### 8.2 Radius

| Component | Radius |
|---|---:|
| Navigation item | 6 px |
| Button | 6 px |
| Input/select | 6 px |
| Card | 8 px |
| Popover/menu | 8 px |
| Dialog/drawer panel | 10 px |
| Status badge | 9999 px, allowed only for small badges |
| Large page container | No decorative radius |

Do not use pill shapes for primary buttons, cards, dialogs, or navigation containers.

### 8.3 Borders

Default:

```css
border: 1px solid var(--border);
```

Hovered interactive surfaces may use:

```css
border-color: var(--border-strong);
```

Do not double-border nested components without a clear structural reason.

### 8.4 Shadows

Default cards and panels:

```text
no shadow
```

Popover/dialog only:

```css
box-shadow:
  0 8px 28px rgba(0, 0, 0, 0.10);
```

Dark mode popover/dialog may use:

```css
box-shadow:
  0 12px 32px rgba(0, 0, 0, 0.32);
```

Clickable card hover may use at most:

```css
box-shadow:
  0 2px 8px rgba(0, 0, 0, 0.04);
```

Never use Tailwind `shadow-md`, `shadow-lg`, or `shadow-xl` as an unexplained default.

---

## 9. Interaction state model

Every interactive component must define these states:

```text
default
hover
focus-visible
pressed
selected, when applicable
disabled
loading, when applicable
```

### 9.1 Meaning of each state

```text
hover:
  temporary pointer indication

focus-visible:
  keyboard navigation indication

pressed:
  pointer-down or active-key indication

selected:
  persistent application state, route, row, tab, or option

disabled:
  unavailable action

loading:
  action accepted and in progress
```

Do not style hover and selected identically.

### 9.2 Global interaction rules

1. Hover changes color or border, not layout.
2. Navigation and table rows do not move on hover.
3. Buttons may scale to `0.98` only while pressed.
4. Clickable cards may gain a subtle border/shadow but do not zoom.
5. Focus-visible uses a 2 px ring with 2 px offset.
6. Mouse click must not permanently leave a focus ring unless the browser considers it focus-visible.
7. Disabled controls have reduced contrast and no hover response.
8. Do not remove native focus behavior without an accessible replacement.
9. Only genuinely interactive elements use `cursor: pointer`.
10. Animations stop or reduce under `prefers-reduced-motion`.

### 9.3 Motion timing

```text
hover color:
  120 ms

border and background:
  140 ms

popover and menu:
  160 ms

dialog and drawer:
  180–220 ms

button pressed:
  80–100 ms
```

Preferred easing:

```css
cubic-bezier(0.2, 0, 0, 1)
```

Use only `opacity` and `transform` for animated movement. The documented sidebar
width transition in Section 39.5 is the only layout-property exception.

---

## 10. Application shell

Desktop shell:

```text
left sidebar
top context bar
main content
optional right detail drawer
```

Canonical dimensions:

```text
sidebar expanded:
  256 px

sidebar collapsed:
  64 px

top context bar:
  48 px

main page horizontal padding:
  24 px at desktop
  16 px at tablet
```

Sidebar:

```text
background: var(--sidebar)
border-right: 1 px solid var(--border)
no shadow
```

Top bar:

```text
background: var(--topbar)
border-bottom: 1 px solid var(--border)
backdrop-filter: blur(8px), only when sticky
no decorative shadow
```

Use full-width content for:

```text
results
comparison matrix
dataset editor
logs
```

Use a constrained width, normally `720–880 px`, for:

```text
settings
create/edit forms
confirmation steps
```

Do not center every operational page in a narrow marketing column.

---

## 11. Sidebar and navigation behavior

### 11.1 Navigation item geometry

```text
height:
  34 px

horizontal padding:
  8 px

icon-to-label gap:
  8 px

radius:
  6 px

label size:
  13–14 px
```

### 11.2 Navigation item states

#### Default

```text
background:
  transparent

text:
  var(--foreground-secondary)

icon:
  var(--foreground-muted)
```

#### Hover

```text
background:
  var(--surface-hover)

text:
  var(--foreground)

icon:
  var(--foreground)
```

Hover must not:

```text
translate
scale
add a heavy shadow
change item height
```

#### Selected route

```text
background:
  var(--surface-selected)

text:
  var(--foreground)

font-weight:
  500

indicator:
  inset 2 px left line using var(--accent)
```

The indicator is intentionally small. Do not turn the entire selected item blue.

#### Pressed

```text
background:
  var(--surface-pressed)
```

#### Focus-visible

```text
outline:
  2 px solid var(--focus-ring)

outline-offset:
  1 px
```

#### Disabled

```text
text:
  var(--foreground-disabled)

background:
  transparent

cursor:
  default
```

### 11.3 Collapsed sidebar

1. Keep the same hover and selected background.
2. Center the icon.
3. Show the accessible label through `aria-label`.
4. A tooltip appears after a short delay, approximately `400–600 ms`.
5. The selected accent indicator remains visible.
6. Do not show a tooltip immediately while the pointer merely crosses the sidebar.

### 11.4 Workspace and project switchers

Default:

```text
transparent background
1 px transparent border
```

Hover:

```text
background: var(--surface-hover)
border-color: var(--border)
```

Open:

```text
background: var(--surface-pressed)
border-color: var(--border-strong)
```

The switcher must show:

```text
current name
scope icon or avatar
chevron
```

Do not use a large floating pill.

### 11.5 Navigation content

Global navigation contains:

```text
workspace switcher
projects
personal secrets
personal providers
admin, when allowed
user menu
```

Project navigation contains:

```text
Tổng quan
Thiết lập
Chatbot Target
Dataset
Evaluator
TestRun
Cài đặt
```

Project context remains visible throughout project routes.

---

## 12. Page structure

Recommended order:

```text
breadcrumb
title row
description or metadata
primary action
secondary actions
filters or tabs
main content
```

The title row should normally be `48–64 px` tall depending on metadata.

The primary action belongs near the title, not in a floating bottom corner.

Avoid multiple visually equal primary actions.

Do not add:

```text
SECTION 01
MODULE 02
QUESTION 05
```

as decorative meta-labels.

---

## 13. Buttons

### 13.1 Geometry

```text
default height:
  32 px

comfortable form action:
  36 px

compact table action:
  28 px

horizontal padding:
  12 px

radius:
  6 px

icon gap:
  6 px
```

### 13.2 Primary button

Light:

```text
default:
  background #202020
  text #FFFFFF

hover:
  background #343434

pressed:
  background #111111
  transform scale(0.98)
```

Dark:

```text
default:
  background #ECECEC
  text #171717

hover:
  background #FFFFFF

pressed:
  background #D8D8D8
  transform scale(0.98)
```

No shadow.

### 13.3 Secondary button

```text
default:
  background var(--surface-hover)
  color var(--foreground)
  border 1 px solid transparent

hover:
  background var(--surface-pressed)

pressed:
  background var(--surface-selected)
```

### 13.4 Outline button

```text
default:
  background var(--surface)
  border var(--border)

hover:
  background var(--surface-hover)
  border-color var(--border-strong)
```

### 13.5 Ghost and icon buttons

```text
default:
  transparent

hover:
  var(--surface-hover)

pressed:
  var(--surface-pressed)
```

Icon button hit area must be at least `32 × 32 px`, even when the icon is `16 px`.

### 13.6 Destructive button

Light:

```text
background:
  #C9372C

hover:
  #AE2A19

text:
  #FFFFFF
```

Dark:

```text
background:
  #E2483D

hover:
  #F15B50

text:
  #FFFFFF
```

Use a destructive solid button only in a confirmed destructive context. Otherwise use an outline or ghost destructive action.

### 13.7 Disabled and loading

Disabled:

```text
background:
  var(--surface-hover)

text:
  var(--foreground-disabled)

border:
  var(--border)

opacity:
  1

cursor:
  not-allowed only when that convention is used consistently;
  otherwise default
```

Disabled buttons:

1. Do not respond to hover or pressed states.
2. Do not rely only on opacity.
3. Keep the label readable.
4. Explain a non-obvious disabled reason through nearby help text or a tooltip.

Loading:

1. Keep width and height stable.
2. Show a `14–16 px` spinner before the label for normal buttons.
3. For icon-only buttons, replace the icon with a centered spinner.
4. Keep the action label when space allows, for example `Đang lưu…`.
5. Prevent duplicate submission.
6. Use `aria-busy="true"` and disable interaction.
7. Do not show both a spinner and an animated ellipsis unless the pattern is explicitly needed.
8. On completion, return to the default state without a bounce or success animation that shifts layout.

### 13.8 Hover-in and hover-out behavior

All button visual changes use symmetric transitions:

```text
hover-in:
  120 ms

hover-out:
  120 ms

pressed:
  80–100 ms

focus ring:
  immediate or <= 80 ms
```

Rules:

1. Hover-out begins immediately when the pointer leaves.
2. Do not add a delay before restoring the default state.
3. Do not animate width, height, padding, or font weight.
4. A primary button may darken or lighten, but must not translate vertically.
5. The `scale(0.98)` effect is allowed only during `:active`, not during hover.
6. Touch devices must not depend on hover for essential feedback.

### 13.9 Button content rules

Button content order:

```text
optional leading icon
label
optional trailing icon
```

Examples:

```text
Plus icon + Tạo Dataset
Download icon + Export XLSX
Label + ChevronDown cho nút menu
```

Rules:

1. Do not place both leading and trailing icons without a clear reason.
2. Use an ellipsis only when the action opens a dialog or requires additional input, for example `Di chuyển…`.
3. Do not add an ellipsis merely because the request is asynchronous.
4. Labels stay stable between default and hover states.

---

## 14. Inputs, selects, and comboboxes

### 14.1 Geometry

```text
height:
  36 px

radius:
  6 px

horizontal padding:
  10–12 px

background:
  var(--surface)

border:
  1 px solid var(--border)
```

### 14.2 States

Hover:

```text
border-color:
  var(--border-strong)
```

Focus-visible:

```text
border-color:
  var(--accent)

box-shadow:
  0 0 0 3px color-mix(in srgb, var(--focus-ring) 22%, transparent)
```

Invalid:

```text
border-color:
  semantic danger text color

focus ring:
  danger color at low opacity
```

Disabled:

```text
background:
  var(--surface-subtle)

text:
  var(--foreground-disabled)
```

Placeholder:

```text
color:
  var(--foreground-muted)
```

### 14.3 Form rules

1. Use a single column for complex forms.
2. Use two columns only for short related fields.
3. Use full width for JSON, cURL, headers, and mappings.
4. Each field has a visible label.
5. Help text appears below the label or field.
6. Errors appear directly below the field.
7. Focus the first invalid field after submission.
8. Preserve values after backend validation errors.
9. Do not use floating labels.
10. Do not use placeholder text as the only label.

### 14.4 Text input

Default text input:

```text
height:
  36 px

text:
  14 px, var(--foreground)

placeholder:
  var(--foreground-muted)

caret:
  var(--accent)
```

Behavior:

1. Hover changes only the border color.
2. Focus changes border and adds the focus ring.
3. Typed text must not shift when an icon appears.
4. Leading and trailing icon slots are fixed-width.
5. Read-only inputs use the normal text color with a subtle background and no misleading hover state.
6. A successful value does not need a permanent green border unless confirmation is important.
7. Browser autofill must be restyled to match the theme rather than showing a bright yellow or blue fill.

### 14.5 Search input

Search fields use a dedicated pattern:

```text
leading icon:
  Search, 16 px

height:
  36 px in pages and filters
  32 px in compact table toolbars

clear button:
  appears only when a value exists
  28–32 px hit area
```

States:

```text
default:
  surface background
  normal border
  muted search icon

hover:
  strong border
  icon becomes foreground-secondary

focus:
  accent border and focus ring
  icon becomes foreground

has-value:
  clear button visible

loading:
  replace the trailing clear area with a small spinner,
  or place the spinner before the clear button when both are required
```

Behavior:

1. Use placeholder text such as `Tìm Project` or `Tìm kết quả`, not only `Tìm kiếm` when context is unclear.
2. Pressing `Escape` clears the query when the field has focus and a value exists.
3. The clear button keeps focus in the search field.
4. Debounce remote search by approximately `250–350 ms`.
5. Local filtering may update immediately.
6. Do not show a full-page loader during search.
7. Keep previous results visible while a remote search request is in flight when the query cache permits it.
8. When no matches exist, show `Không có kết quả cho “…”` instead of the generic empty-state copy.
9. Search-result count changes should not cause the input width to shift.
10. Do not animate every result row on each keystroke.

### 14.6 Password input

Password input geometry follows the normal input.

Required trailing control:

```text
hidden state:
  Eye icon
  accessible label: Hiện mật khẩu

visible state:
  EyeOff icon
  accessible label: Ẩn mật khẩu
```

Rules:

1. The reveal button is an icon button inside the input trailing slot.
2. The reveal button uses ghost hover and pressed states.
3. Toggling visibility must not move focus away from the password input.
4. Toggling visibility must not move the caret or clear the value.
5. The password value is never copied to logs, analytics, query strings, or storage.
6. Caps Lock detection may show a small inline warning when supported.
7. Login password fields do not show a strength meter.
8. Registration and reset-password forms may show strength guidance only when it reflects verified backend rules.
9. Browser password-manager and autocomplete attributes must be correct:

```text
login:
  autocomplete="current-password"

register/reset:
  autocomplete="new-password"
```

10. Error text appears below the field and does not replace the label.

### 14.7 Secret and API-key input

Secret values use password-style masking but are not normal account passwords.

Rules:

1. Use labels such as `API key` or `Secret value`.
2. Do not offer browser password-manager autocomplete.
3. Use `autocomplete="off"` or the most appropriate standards-compliant value supported by the form.
4. Provide reveal only during initial entry or rotation when justified.
5. After successful save, clear the value and show only a masked preview such as `••••••••` or the backend-provided safe preview.
6. Never place an existing secret value back into the input.
7. Copy actions must exist only before submission and require an explicit user action.

### 14.8 Textarea and code-like editors

Normal textarea:

```text
minimum height:
  96 px

resize:
  vertical

padding:
  10–12 px
```

Long notes may use a character counter only when a verified limit exists.

Code-like content such as cURL, JSON, prompt, and rubric uses:

```text
monospace font
surface-subtle background
1 px border
6 px radius
minimum height 160 px
horizontal scrolling when wrapping is disabled
```

Rules:

1. Do not auto-format or modify raw cURL without an explicit action.
2. Provide line wrapping for prose-like rubric text.
3. Keep JSON parsing errors near the editor.
4. Do not use a heavyweight editor dependency without approval.
5. Focus and error states match normal inputs.

### 14.9 Select and combobox

Select trigger follows input geometry.

```text
leading content:
  optional icon/avatar

trailing content:
  ChevronDown, 16 px
```

Trigger states:

```text
hover:
  border-strong

open:
  accent border and focus ring
  chevron may rotate 180 degrees over 140 ms

selected value:
  foreground

placeholder:
  foreground-muted
```

Combobox behavior:

1. Use searchable comboboxes for long resource lists.
2. The search input stays at the top of the popover.
3. Selected items show a Check icon, not only a colored background.
4. Disabled options remain readable and include a reason when needed.
5. Empty result text is contextual.
6. Keyboard navigation supports arrows, Enter, Escape, Home, and End where the component library supports them.
7. Closing the popover restores focus to the trigger.
8. Do not clear the current selection merely because a remote option refresh fails.

### 14.10 Number input

Rules:

1. Right-align values only when the form benefits from numeric scanning; otherwise use normal left alignment.
2. Use visible increment/decrement controls only when they are genuinely useful.
3. Reject invalid characters without silently changing the intended value.
4. Display units outside the editable value when possible, for example `ms`, `%`, or `rows`.
5. Wheel scrolling must not unexpectedly change a focused numeric value in critical forms.
6. `min`, `max`, and `step` must match verified backend constraints.

### 14.11 Checkbox, radio, and switch

Checkbox:

```text
control size:
  16 px

radius:
  4 px

checked:
  accent background
  white check mark
```

Radio:

```text
control size:
  16 px

selected:
  accent outer ring
  centered accent dot
```

Switch:

```text
width:
  32–36 px

height:
  18–20 px

thumb:
  14–16 px
```

Rules:

1. The label is clickable.
2. Hover may tint the label and strengthen the control border.
3. Focus-visible uses the shared ring.
4. Disabled controls remain legible.
5. Use a switch only for an immediate binary setting.
6. Use a checkbox when the value is part of a form submission or acknowledgement.
7. Do not use a switch for destructive or delayed server operations without confirmation.

### 14.12 File upload and drop zone

Default upload control:

```text
border:
  1 px dashed var(--border-strong)

background:
  var(--surface-subtle)

radius:
  8 px
```

Hover:

```text
background:
  var(--surface-hover)

border-color:
  var(--accent)
```

Drag-over:

```text
background:
  var(--accent-soft)

border-color:
  var(--accent)
```

Rules:

1. Provide both click-to-browse and drag-and-drop.
2. Show accepted file type and verified size/row constraints.
3. Display selected filename and safe metadata.
4. Allow removal before submission.
5. Show upload or parsing progress only when measurable.
6. Invalid files show an inline error without clearing a previously valid selection unless necessary.
7. Do not use a large decorative illustration.

### 14.13 Input hover-out and error recovery

Hover-out:

```text
border returns to default over 140 ms
background returns over 120 ms
no delay
```

Blur after valid input:

```text
remove focus ring immediately or within 80 ms
preserve the entered value
```

Blur after invalid input:

```text
keep the invalid border and error text
remove only the focus ring
```

When the user corrects a field:

1. Clear purely client-side format errors as soon as the value becomes valid.
2. Keep backend business errors until the field changes or the form is resubmitted.
3. Do not flash a green success border for every corrected field.
4. Avoid layout jumps by reserving reasonable error-message space in dense forms where errors are common.

### 14.14 Sensitive fields

For password, secret, API key, and raw cURL:

1. Do not persist drafts.
2. Do not prefill plaintext.
3. Clear plaintext after successful create or rotate.
4. Do not include plaintext in error telemetry.
5. Reveal controls must be explicit.
6. Explain that a stored secret cannot be viewed again.

---

## 15. Menus, popovers, and tooltips

### 15.1 Menu

```text
background:
  var(--surface)

border:
  1 px solid var(--border)

radius:
  8 px

padding:
  4 px

shadow:
  only the approved popover shadow
```

Menu item:

```text
height:
  32 px

radius:
  5 px

padding:
  8 px
```

Menu item hover:

```text
background:
  var(--surface-hover)
```

Selected option:

```text
background:
  var(--accent-soft)

color:
  var(--foreground)
```

Destructive option hover:

```text
background:
  semantic danger background

color:
  semantic danger text
```

### 15.2 Tooltip

Tooltips are for short supplementary text.

Light and dark:

```text
background:
  high-contrast foreground color

text:
  inverse foreground

radius:
  5 px

font-size:
  12 px

maximum width:
  280 px
```

Do not hide essential instructions only in tooltips.

---

## 16. Cards and panels

Default non-clickable card:

```text
background:
  var(--surface)

border:
  1 px solid var(--border)

radius:
  8 px

shadow:
  none
```

Clickable card hover:

```text
background:
  var(--surface-subtle)

border-color:
  var(--border-strong)

shadow:
  0 2px 8px rgba(0,0,0,0.04)
```

Clickable card pressed:

```text
background:
  var(--surface-hover)
```

Cards must not:

```text
scale on hover
use a large colored header
use gradients
use large decorative icons
look like marketing bento tiles
```

Do not wrap every section in a card. Use cards only when a boundary represents a meaningful group.

---

## 17. Tables

Tables are primary operational components.

### 17.1 Structure

```text
header height:
  36–40 px

row height:
  40–44 px

cell horizontal padding:
  12 px

header background:
  var(--surface-subtle)

row background:
  transparent

divider:
  1 px solid var(--border)
```

Avoid zebra stripes by default.

### 17.2 Row states

Hover:

```text
background:
  var(--surface-subtle)
```

Selected:

```text
background:
  var(--surface-selected-accent)

indicator:
  inset 2 px left line using var(--accent)
```

Focused row or cell:

```text
2 px focus ring or clear cell outline
```

Disabled row:

```text
muted text
no action hover
```

A row that is not clickable may still use a very subtle hover for scanning, but it must not use `cursor: pointer`.

### 17.3 Table behavior

Required where applicable:

```text
sticky header
server-side pagination
sorting
filtering
row actions
empty state
loading skeleton
horizontal scrolling
column alignment
```

Rules:

1. Keep identity columns sticky in wide comparison views.
2. Right-align numeric values.
3. Preserve row height.
4. Use row menus for secondary actions.
5. Keep filters and page state in the URL.
6. Do not show total-page pagination when total count is unavailable.

MVP pagination:

```text
Trước
Trang N
Sau
Kích thước trang
```

---

## 18. Tabs

Tabs use an underline pattern rather than filled pills.

Default:

```text
color:
  var(--foreground-muted)

background:
  transparent
```

Hover:

```text
color:
  var(--foreground)

background:
  var(--surface-hover), only when the tab has a bounded hit area
```

Selected:

```text
color:
  var(--foreground)

font-weight:
  500

border-bottom:
  2 px solid var(--accent)
```

Focus-visible:

```text
2 px focus ring
```

Tabs should be URL-addressable when they represent meaningful views.

Do not use tabs as sequential wizard steps.

---

## 19. Scrollbars

Use subtle platform-respecting scrollbars.

WebKit recommendation:

```text
width:
  8 px

track:
  transparent

thumb light:
  #C8C8C5

thumb light hover:
  #A8A8A5

thumb dark:
  #4B4B4B

thumb dark hover:
  #626262

radius:
  9999 px
```

Rules:

1. Do not hide scrollbars on operational tables.
2. Do not use a bright accent-colored scrollbar.
3. Horizontal scrolling areas need a visible affordance.
4. Preserve native scrolling and keyboard access.

---

## 20. Status presentation

Use a shared status component.

A status badge contains:

```text
label
semantic background
semantic text color
optional small icon
optional border
```

Badge geometry:

```text
height:
  22–24 px

horizontal padding:
  7–8 px

font size:
  12 px

font weight:
  500
```

Small pill shape is allowed for status badges.

### 20.1 TestRun statuses

| Status | Label | Semantic color |
|---|---|---|
| CREATED | Đã tạo | Neutral |
| QUEUED | Đang chờ | Warning |
| RUNNING | Đang chạy | Information |
| CANCEL_REQUESTED | Đã yêu cầu hủy | Warning |
| COMPLETED | Hoàn tất | Success |
| FAILED | TestRun thất bại | Danger |
| CANCELLED | Đã hủy | Neutral |

### 20.2 Testcase result statuses

| Status | Label | Semantic color |
|---|---|---|
| PASSED | Đạt | Success |
| FAILED | Không đạt | Muted red or warning-danger treatment |
| ERROR | Lỗi thực thi | Danger |
| JUDGE_ERROR | Lỗi LLM Judge | Judge-specific purple |
| TIMEOUT | Quá thời gian | Warning or danger |

Important:

```text
Testcase FAILED:
  the evaluation did not pass

TestRun FAILED:
  the run itself failed to execute successfully
```

The labels must remain explicit even when the colors are similar.

### 20.3 Exact pass, fail, and operational colors

Light theme:

```text
Passed / Completed:
  background #EDF3EC
  text #346538
  border #D5E5D3
  icon #3F7D44

Evaluation failed:
  background #FDEBEC
  text #9F2F2D
  border #F4CCCF
  icon #B63A36

Execution error / Run failed:
  background #FCE8E6
  text #AE2A19
  border #F2C2BD
  icon #C9372C

Running:
  background #E9F2FF
  text #0C66E4
  border #C7DCFF
  icon #0C66E4

Queued / Timeout / Cancellation requested:
  background #FBF3DB
  text #7F5F01
  border #EBDDAD
  icon #956400

Judge error:
  background #F3E8FF
  text #6E3CBC
  border #E2CCFF
  icon #7E4CC9

Cancelled / Draft / Created:
  background #F1F1EF
  text #5F5E5B
  border #E1E1DE
  icon #787774
```

Dark theme uses the semantic values in Section 5.3 and preserves the same meaning.

### 20.4 Status hover and selected behavior

A passive badge is not interactive and has no hover state.

A clickable status filter or chip uses:

```text
hover:
  slightly strengthen the semantic background and border
  keep text color stable

pressed:
  strengthen background one additional step

selected:
  semantic background
  2 px focus/selection outline using the semantic text color at low opacity
  optional Check icon
```

Rules:

1. Do not invert status badges to saturated solid colors on hover.
2. Do not animate badges with bounce or pulse.
3. `RUNNING` may show a small spinner or static activity icon; it must not flash.
4. `QUEUED` may use a Clock icon.
5. `PASSED` may use CheckCircle.
6. `FAILED` may use XCircle.
7. `ERROR` may use CircleAlert.
8. `JUDGE_ERROR` may use BrainCircuit or an approved equivalent from Lucide.
9. Icons are optional when space is constrained, but text labels remain mandatory.

### 20.5 Metric colors

Summary metric cards and table totals use neutral text by default.

Use semantic colors only for the value or a small icon:

```text
Passed count:
  success text

Failed count:
  danger text

Error count:
  danger text with explicit Error label

Pass rate:
  neutral text by default
  success color only when the product defines a meaningful threshold
```

Do not turn entire metric cards green or red.

---

## 21. Loading states

Loading components must match the scope and duration of the operation.

### 21.1 Loader selection matrix

| Situation | Required pattern |
|---|---|
| Initial page data | Page skeleton |
| Table initial data | Header plus row skeletons |
| Card/panel initial data | Component-shaped skeleton |
| Button mutation | Inline spinner inside button |
| Small icon action | Icon spinner |
| Background refetch | Keep content; subtle progress or refresh indicator |
| Unknown short wait | Spinner |
| Known measurable task | Determinate progress bar |
| File parse/import | Progress when measurable, otherwise staged status |
| SSE reconnect | Connection status plus small spinner; keep data |
| Full session bootstrap | Centered compact app loader or shell skeleton |

### 21.2 Spinner

Canonical spinner:

```text
size small:
  14 px

size default:
  16 px

size page:
  24 px maximum for normal app loading

stroke:
  2 px

color:
  currentColor or var(--foreground-muted)

rotation:
  700–900 ms linear infinite
```

Rules:

1. Use one spinner style throughout the app.
2. Avoid large branded loading animations.
3. Add visible text when the wait may exceed approximately two seconds.
4. A spinner does not replace an available progress value.
5. Under reduced motion, the spinner may rotate more slowly or use a static accessible loading indicator while retaining text.

### 21.3 Skeleton colors and animation

Light:

```text
base:
  #ECECEA

highlight:
  #F6F6F4
```

Dark:

```text
base:
  #2C2C2C

highlight:
  #383838
```

Animation:

```text
duration:
  1.4–1.8 s

style:
  subtle left-to-right shimmer or opacity breathing

opacity range:
  small; no bright flash
```

Reduced motion:

```text
static skeleton or very subtle opacity change
```

### 21.4 Skeleton geometry

Text skeleton:

```text
height:
  10–14 px

radius:
  4 px

last line:
  45–70% width
```

Table skeleton:

```text
preserve real row height
show 5–8 rows depending on viewport
vary cell widths slightly but deterministically
```

Card skeleton:

```text
match final title, metadata, and content blocks
```

Rules:

1. Skeletons mirror the final layout.
2. Do not render a generic rectangle for every page.
3. Avoid random widths that change between renders and cause visual noise.
4. Do not show skeletons for background refetch when real data already exists.
5. Replace the skeleton with content using a short opacity transition, approximately `120–160 ms`, without moving layout.

### 21.5 Progress bars

Determinate progress:

```text
track light:
  #ECECEA

track dark:
  #333333

fill running:
  var(--accent)

fill success:
  success text color

fill danger:
  danger text color only when the operation itself failed

height:
  6–8 px

radius:
  9999 px, allowed for the bar only
```

Indeterminate progress:

1. Use a moving segment only when no percentage is available.
2. Duration approximately `1.2–1.6 s`.
3. Do not use gradients; use a solid accent segment moving over a neutral track.
4. Stop the animation under reduced motion and show a static segment with loading text.

### 21.6 Page and background loading rules

1. Keep the application shell visible.
2. Avoid full-page blocking spinners after session bootstrap.
3. Keep existing data during background refetch.
4. Use inline mutation progress.
5. A button loading state keeps its width.
6. Realtime reconnecting must not clear TestRun data.
7. Use `aria-busy` on the smallest meaningful region.
8. Use an `aria-live="polite"` message for important long-running status changes, not every loading tick.
9. Do not overlay an opaque page loader for ordinary query refetches.
10. When a route transition has cached data, render cached data immediately and refresh in place.

### 21.7 Loading completion and error transition

Success completion:

```text
spinner/skeleton fades out over 120–160 ms
content fades in over 120–160 ms
no scale or bounce
```

Failure completion:

```text
loader stops
inline or page error replaces the loading region
layout remains stable where practical
```

Do not keep a skeleton visible behind an error message.

---

## 22. Empty states

An empty state contains:

```text
plain title
one short explanation
one valid next action, when permitted
```

Examples:

```text
Chưa có Chatbot Target
Thêm Chatbot Target trước khi tạo TestRun.

Chưa có Dataset version đang hoạt động
Tạo hoặc publish Dataset version.

Chưa có Evaluator
Tạo Evaluator để xác định điều kiện đạt và không đạt.
```

For a VIEWER:

```text
show a read-only explanation
do not present an unavailable create action as the only next step
```

Avoid large illustrations and marketing copy.

---

## 23. Error states

### 23.1 Inline errors

Use for:

```text
panel fetch failure
form validation
mutation failure
```

### 23.2 Page errors

Use for:

```text
resource not found
permission denied
critical route failure
```

### 23.3 Error content

Include:

```text
clear title
plain-language detail
safe retry action
reference ID when available
```

Do not show:

```text
stack trace
raw exception
token
cookie
sensitive request body
```

Error alerts use a pale semantic background, not a full saturated red panel.

### 23.4 Toasts and notifications

The complete mandatory Toast decision matrix, language rules, duration rules,
deduplication rules, examples, and agent checklist are defined in:

```text
docs/frontend/08-ui-language-toast-and-agent-rules.md
```

This section summarizes the visual contract.

Use Toast only for transient feedback that does not require the user to remain
on the current field, form, panel, or status view.

Do not use Toast for:

```text
field validation
backend fieldErrors
form-level errors already shown inline
page-level 403 or 404
empty states
normal loading
persistent SSE disconnection
long-running progress
preflight blockers
destructive confirmation before an action
```

Use Toast for:

```text
mutation success when the result is not already obvious
global or background failure without a stable inline area
Export or Import background transitions
copy or download success/failure
one-time realtime disconnect and recovery notices
```

Types:

```text
success
error
warning
information
```

Visual geometry:

```text
width:
  320–400 px desktop

radius:
  8 px

border:
  1 px solid var(--border)

background:
  var(--surface)

shadow:
  approved popover shadow

placement:
  top-right desktop
  top-center on narrow screens
```

Use a neutral surface with a small semantic icon. Do not fill the entire Toast
with a saturated semantic color.

Default duration:

```text
success:
  4 seconds

information:
  5 seconds

warning:
  7 seconds

error:
  8 seconds
```

Rules:

1. Include an accessible close button.
2. Hover or keyboard focus pauses auto-dismiss.
3. Hover-out resumes the remaining time instead of restarting the duration.
4. Stack at most three visible Toasts.
5. Deduplicate repeated messages from reconnect or retry loops.
6. Do not show identical inline and Toast messages.
7. Do not include secrets, tokens, raw headers, raw cURL, or sensitive payloads.
8. Under reduced motion, use opacity only or render immediately.
9. User-facing Toast copy is Vietnamese while approved technical terms remain in English.
10. Do not add Vietnamese translations in parentheses after technical terms.

---

## 24. Permission-denied UX

1. Explain that access is restricted.
2. Keep safe parent navigation available.
3. Hide or disable actions based on verified capabilities.
4. Backend 403 remains authoritative.
5. Support a platform admin with no workspace membership role.
6. Disabled actions with a non-obvious permission reason need supporting text or a tooltip.

Preferred copy:

```text
Bạn có thể xem Workspace này nhưng không thể chỉnh sửa.
Cần quyền Manage để thay đổi thành viên Workspace.
Cần quyền Edit để tạo TestRun.
```

---

## 25. Destructive actions

Destructive actions include:

```text
delete workspace
delete project
delete dataset
delete target
delete evaluator
delete provider
delete secret
discard draft
request cancellation
```

Rules:

1. Use a confirmation dialog.
2. Include the resource name.
3. Explain reversibility.
4. Keep the destructive action separate from Save.
5. Use typed confirmation only for high-impact operations.
6. Do not imply cancellation is immediate or guaranteed.
7. Dialog focus defaults to the safe action, not the destructive action.

Cancellation copy should explain:

```text
Runner có thể hoàn tất trước khi yêu cầu hủy có hiệu lực.
Kết quả một phần vẫn khả dụng nếu backend đã lưu chúng.
```

---

## 26. Dialogs, drawers, and pages

Use a dialog for:

```text
short confirmation
small create form
role update
simple export action
```

Use a drawer for:

```text
result preview
target example preview
JSON inspection
```

Use a page for:

```text
target configuration
dataset editing
evaluator versioning
TestRun creation
multi-step import
```

Dialogs:

```text
maximum common width:
  480–640 px

overlay:
  var(--overlay)

panel:
  var(--surface), 1 px border, 10 px radius
```

Do not place a complex multi-step workflow in a small dialog.

---

## 27. JSON and code viewers

Required features where useful:

```text
expand and collapse
copy safe value
monospace
line-wrap toggle
search for large objects when justified
```

Rules:

1. Render as text, never HTML.
2. Do not expose fields removed by backend masking.
3. Truncate extremely long values with explicit expansion.
4. Copy only the visible safe value.
5. Avoid a heavy syntax-highlighting dependency without approval.
6. Background uses `--surface-subtle`.
7. Code blocks use a 6 px radius and 1 px border.

---

## 28. Chatbot target UX

Recommended flow:

```text
Basic information
Request configuration
Secret bindings
Dataset mappings
Response extraction
Test and readiness
```

cURL UX:

1. Paste raw cURL in a dedicated editor.
2. Send it to backend inspection.
3. Display the sanitized parsed request.
4. Highlight detected secret candidates.
5. Require explicit binding to stored secrets.
6. Never retain raw secret values after inspection.
7. Display readiness blockers from backend.

Target test UX displays:

```text
status
latency
sanitized response
extracted fields
error
```

HTTP 2xx alone does not mean readiness.

---

## 29. Dataset UX

### 29.1 Dataset list

Show:

```text
name
source type
current version
status
row count
updated time
```

Use a table, not a decorative card grid, when the list grows beyond a few items.

### 29.2 Draft editor

1. Mark draft state clearly.
2. Keep active versions read-only.
3. Show required schema columns.
4. Support horizontal scrolling.
5. Do not invent manual schema management.
6. Keep row controls compact.
7. Show save/conflict feedback near the affected row.

### 29.3 Excel import

Wizard:

```text
Upload
Select sheet
Preview
Map columns
Review schema changes
Confirm
```

Display verified limits:

```text
maximum rows:
  500

default preview:
  10

maximum preview:
  20
```

When adding columns, explain that a new project schema version is created.

---

## 30. Evaluator UX

Evaluator identity and version are distinct.

Rules:

1. Existing versions are read-only.
2. Changes create a new version.
3. Load field sources and operators from backend.
4. Validate through backend.
5. Do not expose custom JavaScript.
6. Do not finalize `LLM_JUDGE` or `MIXED` forms until validator contracts are verified.

Rule builder supports:

```text
field source
operator
expected value
weight
required
order
```

Use a compact rule list with clear drag or move controls when ordering is supported. Do not style rules as large marketing cards.

---

## 31. TestRun creation UX

Use a full-page step flow:

```text
Dataset
Targets
Evaluators
Options
Preflight
Review and create
```

Rules:

1. Blockers prevent creation.
2. Warnings remain visible.
3. VIEWER may preflight but cannot create.
4. Display locked versions before create.
5. Show run type explicitly.
6. Show `maxRows` impact.
7. Use a compact step indicator, not a large progress hero.

---

## 32. TestRun detail UX

Show:

```text
status
progress
start and finish time
run type
dataset snapshot
targets
evaluators
summary metrics
connection health
cancel action
```

Realtime state is separate:

```text
Trực tiếp
Đang kết nối lại
Đang cập nhật bằng dữ liệu REST
Đã đóng kết nối
```

Do not show `TestRun thất bại` when only the SSE connection failed.

Progress:

```text
use a thin 6–8 px progress bar
show completed/total text
do not use an animated gradient
```

---

## 33. Results UX

Result list supports:

```text
MACHINE
FINAL
```

Make current mode explicit.

For reviewed results, show:

```text
machine result
QC review
final result
reviewed indicator
```

Do not visually replace the machine result without preserving comparison.

Use drawers or detail pages for verbose result data instead of expanding every table row.

---

## 34. Comparison matrix UX

1. Dataset records are rows.
2. Targets are columns.
3. Keep identity columns sticky.
4. Use compact status cells.
5. Support horizontal scrolling.
6. Show score when applicable.
7. Show reviewed indicator.
8. Open detail in a drawer or page.
9. Do not construct the complete matrix client-side.
10. Preserve backend target ordering.

Selected cells use the accent-soft background. Hover uses only the subtle neutral background.

---

## 35. QC review UX

Fields:

```text
review status
override status
override score
note
```

Rules:

1. Keep machine result visible.
2. Overrides affect FINAL mode only.
3. Clearly label override impact.
4. MEMBER, ADMIN, OWNER, and PLATFORM_ADMIN can review.
5. VIEWER cannot review.
6. Refresh result list, detail, and matrix after save.
7. Use a normal form layout, not a large modal unless the review is short.

---

## 36. Export UX

Flow:

```text
choose result mode
create job
show status
download when complete
```

Rules:

1. VIEWER can export.
2. Default format is XLSX.
3. Default TestRun mode is FINAL.
4. Stop polling terminal jobs.
5. Explain expired jobs.
6. Use backend filename when safe.
7. Do not automatically trigger repeated downloads.

Export status may appear in a compact popover, drawer, or page section. Do not use a full-screen progress animation.

---

## 37. Responsive behavior

### Desktop

Full support.

### Tablet

Required:

```text
collapsible sidebar
stacked forms
horizontal table scrolling
drawer detail
usable comparison matrix
```

### Mobile

Best effort:

```text
login
basic navigation
read-only detail
simple actions
```

Not required for MVP:

```text
full dataset editing
wide comparison workflows
complex target authoring
complex evaluator authoring
```

The desktop density must not be inflated merely to imitate a mobile layout.

---

## 38. Accessibility

Required:

```text
keyboard navigation
visible focus
semantic landmarks
proper labels
aria-live for important async status
dialog focus trap
escape behavior
color-independent statuses
reduced-motion support
```

Rules:

1. Tables use proper headers.
2. Sort controls announce direction.
3. Error messages associate with fields.
4. Progress includes accessible text.
5. Realtime updates do not announce every low-level event.
6. Tooltips do not contain essential information only.
7. Touch targets should be at least 32 px in dense desktop UI and approximately 40 px on touch layouts.
8. Contrast must remain valid in both themes.

---

## 39. Motion

Motion is quiet, functional, and reversible.

### 39.1 Motion principles

1. Motion explains state change; it does not decorate static content.
2. Hover-in and hover-out are equally smooth.
3. Enter and exit animations are paired.
4. Motion must never block input.
5. Layout does not jump after animation completes.
6. Prefer opacity and transform.
7. Avoid animating height for large dynamic regions; use measured transitions only when necessary.
8. Respect `prefers-reduced-motion` globally.

### 39.2 Allowed motion

```text
sidebar collapse
dialog and drawer enter/exit
popover and menu enter/exit
button pressed feedback
chevron rotation
small status color transition
skeleton shimmer
spinner rotation
progress movement
toast enter/exit
row expansion when explicitly supported
```

### 39.3 Forbidden motion

```text
GSAP
scroll pinning
scroll-scrubbed text
continuous marquee
staggered page reveal
background blob animation
parallax
large page transitions
hover zoom on operational rows
bouncing status badges
pulsing failure states
confetti
```

### 39.4 Standard durations

| Interaction | Duration |
|---|---:|
| Color hover in/out | 120 ms |
| Border/background | 140 ms |
| Icon rotation | 140 ms |
| Tooltip | 100–140 ms |
| Popover/menu | 140–160 ms |
| Dialog | 180 ms |
| Drawer | 200–220 ms |
| Toast | 160–200 ms enter; 140–180 ms exit, per document `08` |
| Skeleton replacement | 120–160 ms |
| Sidebar collapse | 180–220 ms |

Canonical easing:

```css
cubic-bezier(0.2, 0, 0, 1)
```

Exit may use a slightly faster easing/duration than enter, but not less than `100 ms` for visible overlays.

### 39.5 Component motion recipes

Button:

```text
hover:
  color/background only

pressed:
  scale(0.98), 80–100 ms

release:
  return to scale(1), 100 ms
```

Popover/menu:

```text
enter:
  opacity 0 -> 1
  scale 0.98 -> 1
  translateY -2 px -> 0

exit:
  reverse over 120–140 ms
```

Dialog:

```text
overlay:
  opacity 0 -> 1

panel:
  opacity 0 -> 1
  scale 0.98 -> 1
  translateY 4 px -> 0
```

Drawer:

```text
opacity 0 -> 1
translateX 12–20 px -> 0
```

Sidebar collapse:

```text
width transition
label opacity transition
icons remain aligned
no content bounce
```

Toast:

```text
enter:
  opacity 0 -> 1
  translateY -6 px -> 0

exit:
  opacity 1 -> 0
  translateX 0 -> 8 px or translateY 0 -> -4 px
```

Document `08` is authoritative for Toast duration, direction, reduced-motion,
pause/resume, and dismissal behavior.

### 39.6 Reduced motion

When `prefers-reduced-motion: reduce`:

```text
disable scale and translation where possible
replace shimmer with static skeleton
replace moving indeterminate bars with static state plus text
keep color changes immediate or <= 80 ms
preserve focus and status visibility
```

Do not remove essential feedback.

### 39.7 Transition implementation

Default interactive transition:

```css
transition-property: background-color, border-color, color, box-shadow, opacity, transform;
transition-duration: 120ms;
transition-timing-function: cubic-bezier(0.2, 0, 0, 1);
```

Do not use `transition: all`.

Use `will-change` only while an element is actively animating.

---

## 40. Content style

Use direct operational language.

Engineering identifiers remain English. User-facing copy is Vietnamese, while the canonical technical terms in document `08` remain in English without parenthetical translations.

Preferred:

```text
Tạo TestRun
Yêu cầu hủy
Publish draft
Chạy preflight
Export kết quả
```

Avoid:

```text
Elevate
Unleash
Seamless
Next-generation
Game-changing
Let’s go
Awesome
Oops
```

Messages explain:

```text
what happened
what the user can do next
```

---

## 41. Shared component policy

Likely shared components:

```text
PageHeader
Breadcrumbs
StatusBadge
PermissionGuard
EmptyState
InlineError
ProblemDetailAlert
DataTable
PaginationControls
JsonViewer
CodeEditorShell
CopyButton
SensitiveValueField
ConfirmDialog
ConnectionStatus
```

Create a shared component after a real repeated pattern exists.

Do not build:

```text
one mega component
dozens of style props
generic abstractions for a single use case
feature-specific behavior inside shared primitives
```

---

## 42. Implementation reference values

The following values are defaults, not optional suggestions:

```text
Sidebar width:
  256 px expanded
  64 px collapsed

Top bar:
  48 px

Navigation item:
  34 px height
  6 px radius

Default button:
  32 px height
  6 px radius

Default input:
  36 px height
  6 px radius

Search input:
  36 px normal
  32 px compact toolbar
  250–350 ms remote debounce

Password reveal button:
  28–32 px hit area
  preserve input focus and caret

Spinner:
  14–16 px inline
  24 px maximum page spinner

Skeleton:
  1.4–1.8 s subtle cycle
  static under reduced motion

Default table row:
  40–44 px

Default card:
  8 px radius
  1 px border
  no shadow

Focus:
  2 px visible ring

Hover transition:
  120–140 ms
```

An implementation may deviate only for a documented accessibility or component-specific reason.

---

## 43. Design review checklist

A page is ready for review when:

```text
it uses the canonical warm-neutral palette
hover and selected states are visibly different
keyboard focus is visible
light and dark themes work
loading, empty, error, and success states exist
permission states exist
primary action is clear
destructive action is safe
status labels are unambiguous
sensitive data is not exposed
URL state is preserved where required
desktop density remains practical
tablet layout remains usable
no marketing-page patterns were introduced
no heavy shadows, gradients, or decorative motion exist
search, password, input, select, and file-upload states follow this document
loading pattern matches the operation scope
skeleton geometry matches final content
pass, fail, error, and running colors are semantically correct
hover-out returns smoothly without delay or layout shift
reduced-motion behavior exists
```

---

## 44. Mandatory UI language

The primary user-interface language is Vietnamese.

Keep established technical terms in English, including:

```text
Workspace
Project
Dataset
Dataset version
Dataset schema
Chatbot Target
Evaluator
LLM Judge
TestRun
Runner
Provider
model
prompt
rubric
API key
endpoint
request
response
payload
header
field
mapping
token
SSE
JSON
cURL
Export
Import
Preview
MACHINE
FINAL
```

Correct:

```text
Tạo Dataset
Cập nhật Chatbot Target
API key không hợp lệ.
Không thể kết nối tới endpoint.
Export đang được xử lý.
```

Incorrect:

```text
Tạo bộ dữ liệu (Dataset)
Mục tiêu chatbot (Chatbot Target)
Khóa API (API key)
```

Do not add Vietnamese translations in parentheses.

Backend enum values remain in English in code. Map them to concise Vietnamese
labels in the user interface.

The full mandatory copy and feedback rules are in:

```text
docs/frontend/08-ui-language-toast-and-agent-rules.md
```
