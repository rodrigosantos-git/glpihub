---
name: GLPI Nexus Identity
colors:
  surface: '#f7f9fc'
  surface-dim: '#d8dadd'
  surface-bright: '#f7f9fc'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f7'
  surface-container: '#eceef1'
  surface-container-high: '#e6e8eb'
  surface-container-highest: '#e0e3e6'
  on-surface: '#191c1e'
  on-surface-variant: '#424752'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f4'
  outline: '#727783'
  outline-variant: '#c2c6d4'
  surface-tint: '#005db5'
  primary: '#00488d'
  on-primary: '#ffffff'
  primary-container: '#005fb8'
  on-primary-container: '#cadcff'
  inverse-primary: '#a8c8ff'
  secondary: '#535f70'
  on-secondary: '#ffffff'
  secondary-container: '#d7e3f8'
  on-secondary-container: '#596576'
  tertiary: '#51435a'
  on-tertiary: '#ffffff'
  tertiary-container: '#6a5b73'
  on-tertiary-container: '#e8d5f1'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d6e3ff'
  primary-fixed-dim: '#a8c8ff'
  on-primary-fixed: '#001b3d'
  on-primary-fixed-variant: '#00468b'
  secondary-fixed: '#d7e3f8'
  secondary-fixed-dim: '#bbc7db'
  on-secondary-fixed: '#101c2b'
  on-secondary-fixed-variant: '#3c4858'
  tertiary-fixed: '#f0dcf8'
  tertiary-fixed-dim: '#d3c0dc'
  on-tertiary-fixed: '#22172b'
  on-tertiary-fixed-variant: '#4f4258'
  background: '#f7f9fc'
  on-background: '#191c1e'
  surface-variant: '#e0e3e6'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 57px
    fontWeight: '400'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: 0px
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: 0px
  title-lg:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
    letterSpacing: 0px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base-unit: 4px
  margin-mobile: 16px
  margin-tablet: 24px
  gutter: 16px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 24px
---

## Brand & Style

This design system is engineered for efficiency, reliability, and technical precision. Designed for IT professionals and support technicians, the UI prioritizes clarity over decoration, ensuring that complex data is navigable and actionable.

The aesthetic follows a **Modern Corporate** style with heavy **Material Design 3 (M3)** influence. It utilizes a systematic approach to depth and hierarchy, employing purposeful whitespace and a rigid grid to evoke a sense of stability and professional trust. The emotional response is one of calm control within high-pressure technical environments.

## Colors

The palette is anchored by a commanding **Corporate Blue**, signifying trust and the authoritative nature of IT infrastructure management. 

- **Primary (#005FB8):** Used for key actions, active states, and brand representation.
- **Secondary (#535F70):** A muted slate blue for less prominent UI elements and supporting icons.
- **Neutral (#F0F2F5):** Provides a clean, modern canvas that differentiates content areas from the background.
- **Surface:** Pure whites are used for cards and elevated containers to ensure maximum legibility of technical text.
- **Functional Colors:** Standardized Red (Error), Amber (Warning), and Green (Success) must follow high-accessibility contrast ratios for status indicators.

## Typography

The design system utilizes **Inter** exclusively to ensure a systematic and utilitarian feel across the platform. Inter’s tall x-height and excellent legibility at small sizes make it ideal for data-dense IT support logs and asset management lists.

- **Headlines:** Semi-bold weights are reserved for page titles and section headers to provide immediate orientation.
- **Body:** Standardized at 16px for primary content to ensure long-term readability for technicians. 
- **Labels:** Used for metadata, timestamps, and ticket IDs. These use a medium weight to differentiate from body text.
- **Numeric Data:** For asset serial numbers or IP addresses, ensure letter-spacing is slightly increased to prevent character confusion.

## Layout & Spacing

This design system follows a **Fluid Grid** model optimized for Android’s diverse screen sizes. It is built on a 4px baseline grid to ensure all components align precisely.

- **Mobile (up to 600dp):** 4-column grid with 16px side margins.
- **Tablet (600dp+):** 8-column grid with 24px side margins. 
- **Component Spacing:** Use the `stack` tokens for vertical rhythm. Internal card padding should default to 16px (`stack-md`) to maintain a clean, professional density.
- **Touch Targets:** All interactive elements must maintain a minimum 48x48dp area, even if the visual representation is smaller.

## Elevation & Depth

Depth is conveyed through **Tonal Layers** and subtle **Ambient Shadows**, following the Material Design 3 philosophy.

- **Level 0 (Background):** Neutral grey surface (#F0F2F5).
- **Level 1 (Cards/Sheet):** Pure white surface with a very soft, diffused shadow (4px blur, 5% opacity black).
- **Level 2 (Active/Hover):** Increased shadow depth (8px blur, 8% opacity) and a slight primary color tint to the shadow to reinforce the brand.
- **Overlays:** Use a 20% opacity black scrim for modals and bottom sheets to maintain focus on the task at hand.

## Shapes

The shape language is **Rounded**, striking a balance between modern friendliness and professional structure. 

- **Small Components (Buttons, Inputs):** 0.5rem (8px) corner radius.
- **Medium Components (Cards, Modals):** 1rem (16px) corner radius.
- **Large Components (Bottom Sheets):** 1.5rem (24px) corner radius for top edges.
- **Selection Controls:** Checkboxes use a 4px radius, while radio buttons remain fully circular.

## Components

### Buttons
- **Primary:** Solid Blue fill, White text, 8px radius.
- **Secondary:** Outlined Blue border (1px), Blue text.
- **Tertiary:** Ghost style, Blue text, for low-priority actions like 'Cancel'.

### Input Fields
- **Style:** Filled background (5% black) with a 1px bottom stroke that thickens and changes to Primary Blue on focus.
- **Labels:** Use "floating labels" to maintain context without sacrificing vertical space.

### Cards
- **Usage:** The primary container for Tickets and Assets. 
- **Structure:** 16px internal padding, Level 1 elevation, and an 8px vertical stack between cards in a list.

### Chips & Tags
- **Status Tags:** Use low-saturation background tints (e.g., light red for "Critical") with high-saturation text of the same hue.
- **Filter Chips:** 32px height, rounded-full (pill-shaped), utilizing the Primary Blue for the active/selected state.

### Lists
- **Density:** High-density lists are permitted for technical logs, using 12px vertical padding and subtle horizontal dividers (1px, 5% black).
- **Icons:** Use 24dp "Material Symbols" in a standard weight, colored with the Secondary palette.