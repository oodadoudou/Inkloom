---
name: Quiet Paper
colors:
  surface: '#f8faf9'
  surface-dim: '#d8dad9'
  surface-bright: '#f8faf9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f3'
  surface-container: '#eceeed'
  surface-container-high: '#e6e9e8'
  surface-container-highest: '#e1e3e2'
  on-surface: '#191c1c'
  on-surface-variant: '#414845'
  inverse-surface: '#2e3131'
  inverse-on-surface: '#eff1f0'
  outline: '#717975'
  outline-variant: '#c1c8c4'
  surface-tint: '#44655a'
  primary: '#426258'
  on-primary: '#ffffff'
  primary-container: '#5a7b70'
  on-primary-container: '#f5fff9'
  inverse-primary: '#abcec1'
  secondary: '#44617d'
  on-secondary: '#ffffff'
  secondary-container: '#bfddfe'
  on-secondary-container: '#45627e'
  tertiary: '#625773'
  on-tertiary: '#ffffff'
  tertiary-container: '#7b6f8d'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#c6eadc'
  primary-fixed-dim: '#abcec1'
  on-primary-fixed: '#002018'
  on-primary-fixed-variant: '#2c4d43'
  secondary-fixed: '#cee5ff'
  secondary-fixed-dim: '#acc9ea'
  on-secondary-fixed: '#001d33'
  on-secondary-fixed-variant: '#2c4964'
  tertiary-fixed: '#ecdcfe'
  tertiary-fixed-dim: '#cfc1e2'
  on-tertiary-fixed: '#20172f'
  on-tertiary-fixed-variant: '#4c425d'
  background: '#f8faf9'
  on-background: '#191c1c'
  surface-variant: '#e1e3e2'
typography:
  display-lg:
    fontFamily: Noto Sans
    fontSize: 32sp
    fontWeight: '600'
    lineHeight: 40sp
  display-md:
    fontFamily: Noto Sans
    fontSize: 24sp
    fontWeight: '600'
    lineHeight: 32sp
  headline-sm:
    fontFamily: Noto Sans
    fontSize: 20sp
    fontWeight: '500'
    lineHeight: 28sp
  body-lg:
    fontFamily: Noto Sans
    fontSize: 16sp
    fontWeight: '400'
    lineHeight: 24sp
  body-md:
    fontFamily: Noto Sans
    fontSize: 14sp
    fontWeight: '400'
    lineHeight: 20sp
  label-lg:
    fontFamily: Noto Sans
    fontSize: 12sp
    fontWeight: '500'
    lineHeight: 16sp
  label-sm:
    fontFamily: Noto Sans
    fontSize: 10sp
    fontWeight: '500'
    lineHeight: 14sp
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  global_app_bar_height: 48dp
  sub_bar_height: 40dp
  side_rail_wide: 160dp
  side_rail_narrow: 140dp
  grid_gutter: 16dp
  grid_margin: 24dp
  container_padding: 16dp
  stack_gap: 12dp
---

## Brand & Style

The design system is centered on a "Quiet, Paper-like" philosophy, specifically optimized for high-resolution professional Android tablets. It prioritizes low visual noise and maximum focus for long-form note-taking and sketching.

The aesthetic blends **Modern Minimalism** with **Tactile / Skeuomorphic** undertones. It avoids the clinical coldness of standard digital tools by utilizing organic color palettes and soft, physical metaphors—specifically the behavior of paper sheets layered on a neutral workspace. 

The emotional goal is to evoke a state of "Flow" through generous whitespace, unobtrusive floating interface elements, and a high degree of predictability. Tools appear only when needed and recede when the user begins to write.

## Colors

The palette is anchored by a primary Sage green, chosen for its low eye strain and organic feel. The system utilizes a tiered surface model:
1.  **Canvas (#FFFFFF):** The primary writing surface. Always pure white or light cream to mimic paper.
2.  **Workspace (#F2F2F2):** The neutral "desk" surface behind the paper.
3.  **Accent Themes:** Users can toggle between six core accent colors (Sage, Sea Salt, Ash Purple, Coral, Amber, Graphite) which recolor primary actions, active states, and selection highlights without altering the content of the notes.

Tonal layers are used for elevation rather than harsh shadows, maintaining a "printed" appearance.

## Typography

This design system uses a unified Sans-Serif stack (Noto Sans / Roboto) to ensure maximum legibility across handwritten and typed content. 

- **Scale:** Utilizes a standard sp (scale-independent pixels) system for Android.
- **Hierarchy:** Uses font weight rather than dramatic size shifts to maintain a quiet interface.
- **Line Height:** Generous leading (1.5x) is applied to body text to prevent visual crowding on large tablet displays.
- **Special Case:** Monospaced fonts may be used for technical metadata (e.g., brush engine parameters) but are excluded from the primary user-facing narrative.

## Layout & Spacing

Designed for 16:10 landscape tablet orientations. The layout follows a **Fixed 3-Column Rail System**:

1.  **Navigation Rail (Left):** Context-aware navigation, typically 140dp-160dp.
2.  **Main Workspace (Center):** The "Page" view, maintaining aspect ratio margins to simulate physical paper sitting on a desk.
3.  **Inspector Rail (Right):** Temporary or pinned tool settings and layer stacks.

**The 8dp Grid:** All spacing between components and within containers follows 8dp increments. Small elements (icons, labels) use a 4dp micro-grid.

**Tool Docks:** The primary drawing toolbar is a floating "pill" dock centered at the top or bottom of the viewport, detached from the system bars to reduce perceived weight.

## Elevation & Depth

Hierarchy is established through **Tonal Stacking** and **Soft Ambient Shadows**:

- **Level 0 (Workspace):** Base background color.
- **Level 1 (Paper):** The canvas sheet. Uses a subtle 2dp blur shadow (alpha 0.05) to distinguish the "sheet" from the "desk."
- **Level 2 (Floating UI):** Toolbars and inspectors. These use a higher elevation (8dp blur) with a semi-transparent backdrop blur (Glassmorphism effect) to maintain context of the underlying note while providing a legible interface layer.
- **Level 3 (Modals/Pickers):** Highest elevation. Uses a light scrim to dim the workspace.

## Shapes

The design system uses a modern, rounded language to feel approachable and organic.
- **Small (8dp):** Used for tool icons, checkboxes, and inner selection states.
- **Medium (12dp):** Used for cards, list items, and input fields.
- **Large (24dp):** Used for primary floating tool docks and main page containers in the gallery view.
- **Full (Pill):** Used for buttons and active tool indicators.

## Components

### Buttons & Chips
- **Primary Action:** Solid fill using the active theme color (Sage by default). White text/icon. Full pill rounding.
- **Secondary Action:** Ghost style. 1dp border in theme color or neutral grey.
- **Filter Chips:** Light tonal fill (5% opacity of theme color) when inactive, solid fill when active.

### Floating Tool Dock
- A centered horizontal pill containing tool icons.
- **Active State:** A vertical "indicator" line or a background shape with 12dp rounding behind the active tool icon.
- **Expansion:** Tapping an active tool opens an "Adaptive Inspector" in the right rail or a floating popover.

### Lists & Navigation
- List items in the sidebar use a subtle highlight with 8dp rounded corners. 
- Active state uses a vertical bar on the leading edge (4dp width) matching the theme color.

### Input Fields
- Soft 12dp rounded rectangles with a subtle 1dp border. 
- Focus state thickens the border and applies the primary theme color.

### Paper Workspace
- The page itself is a component. It includes "Page Rails" on the left for thumbnail navigation and "Adaptive Inspectors" on the right for layers and brush properties.