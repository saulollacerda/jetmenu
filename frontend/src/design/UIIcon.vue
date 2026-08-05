<script setup lang="ts">
// SVG icon set ported from ui.jsx UIIcon. 20x20 viewBox.
defineProps<{ name: string; size?: number }>()
</script>

<template>
  <svg
    :width="size ?? 16"
    :height="size ?? 16"
    viewBox="0 0 20 20"
    style="flex-shrink: 0; display: block"
  >
    <template v-if="name === 'menu'">
      <path
        d="M3 5h14M3 10h14M3 15h14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'home'">
      <path
        d="M3 10l7-6 7 6v8a1 1 0 01-1 1h-3v-5h-6v5H4a1 1 0 01-1-1v-8z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
    </template>
    <template v-else-if="name === 'cart'">
      <path
        d="M3 4h2l1.5 9h10l1.5-7H6"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <circle cx="8" cy="17" r="1.4" fill="currentColor" />
      <circle cx="16" cy="17" r="1.4" fill="currentColor" />
    </template>
    <template v-else-if="name === 'burger'">
      <path
        d="M3 8c0-2 3-4 7-4s7 2 7 4M3 12h14M3 16c0 1 1 2 3 2h8c2 0 3-1 3-2"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'folder'">
      <path
        d="M3 6a1 1 0 011-1h4l2 2h7a1 1 0 011 1v8a1 1 0 01-1 1H4a1 1 0 01-1-1V6z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
    </template>
    <template v-else-if="name === 'leaf'">
      <path
        d="M4 16c0-7 5-12 14-12-1 9-6 14-13 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <path d="M5 16l10-10" stroke="currentColor" stroke-width="1.5" />
    </template>
    <template v-else-if="name === 'user'">
      <circle cx="10" cy="7" r="3" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path
        d="M3 18c0-3.5 3.5-6 7-6s7 2.5 7 6"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
    </template>
    <template v-else-if="name === 'card'">
      <rect
        x="2.5"
        y="5"
        width="15"
        height="10"
        rx="1.5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
      <path d="M2.5 9h15" stroke="currentColor" stroke-width="1.5" />
    </template>
    <template v-else-if="name === 'gear'">
      <circle cx="10" cy="10" r="2.5" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path
        d="M10 2v2M10 16v2M2 10h2M16 10h2M4.5 4.5l1.5 1.5M14 14l1.5 1.5M4.5 15.5L6 14M14 6l1.5-1.5"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'bell'">
      <path
        d="M5 8a5 5 0 1110 0v3l1.5 3h-13L5 11V8z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <path
        d="M8 17c.4 1 1.1 1.5 2 1.5s1.6-.5 2-1.5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
    </template>
    <template v-else-if="name === 'search'">
      <circle cx="9" cy="9" r="5" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path d="M13 13l4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
    </template>
    <template v-else-if="name === 'plus'">
      <path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
    </template>
    <template v-else-if="name === 'minus'">
      <path d="M4 10h12" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
    </template>
    <template v-else-if="name === 'x'">
      <path d="M5 5l10 10M15 5L5 15" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
    </template>
    <template v-else-if="name === 'check'">
      <path
        d="M4 10l4 4 8-8"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'chevDown'">
      <path
        d="M5 8l5 5 5-5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'chevRight'">
      <path
        d="M7 4l6 6-6 6"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'chevLeft'">
      <path
        d="M13 4l-6 6 6 6"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'arrow'">
      <path
        d="M5 10h10M11 6l4 4-4 4"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'download'">
      <path
        d="M10 3v10M6 9l4 4 4-4M3 16h14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'upload'">
      <path
        d="M10 17V7M6 11l4-4 4 4M3 17h14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'sync'">
      <path
        d="M3 10a7 7 0 0112-5l2 2"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
      <path
        d="M17 10a7 7 0 01-12 5l-2-2"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
      <path
        d="M14 4v3h3M6 16v-3H3"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'edit'">
      <path
        d="M3 17l1-4 9-9 3 3-9 9-4 1z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <path d="M11 5l3 3" stroke="currentColor" stroke-width="1.5" />
    </template>
    <template v-else-if="name === 'trash'">
      <path
        d="M4 6h12M8 6V4a1 1 0 011-1h2a1 1 0 011 1v2M6 6l1 11a1 1 0 001 1h4a1 1 0 001-1l1-11"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'eye'">
      <path
        d="M2 10c2-4 5-6 8-6s6 2 8 6c-2 4-5 6-8 6s-6-2-8-6z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
      <circle cx="10" cy="10" r="2.5" fill="none" stroke="currentColor" stroke-width="1.5" />
    </template>
    <template v-else-if="name === 'receipt'">
      <path
        d="M5 3h10v15l-2-1.5L11 18l-2-1.5L7 18l-2-1.5V3z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <path
        d="M7.5 7h5M7.5 10h5M7.5 13h3"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'clock'">
      <circle cx="10" cy="10" r="7" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path
        d="M10 6v4l3 2"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'money'">
      <rect
        x="2.5"
        y="5.5"
        width="15"
        height="9"
        rx="1.5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
      <circle cx="10" cy="10" r="2" fill="none" stroke="currentColor" stroke-width="1.5" />
    </template>
    <template v-else-if="name === 'chart'">
      <path
        d="M3 16V8M8 16V4M13 16v-6M18 16v-3"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'alert'">
      <path
        d="M10 3l8 14H2L10 3z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <path
        d="M10 8v4M10 14.5v.5"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'info'">
      <circle cx="10" cy="10" r="7" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path
        d="M10 9v5M10 6.5v.5"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
      />
    </template>
    <template v-else-if="name === 'lock'">
      <rect
        x="4"
        y="9"
        width="12"
        height="9"
        rx="1.5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
      <path
        d="M7 9V6a3 3 0 016 0v3"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
    </template>
    <template v-else-if="name === 'mail'">
      <rect
        x="2.5"
        y="5"
        width="15"
        height="10"
        rx="1.5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
      <path
        d="M3 6l7 5 7-5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'trend'">
      <path
        d="M3 13l4-4 3 3 7-7"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <path
        d="M13 5h4v4"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'box'">
      <path
        d="M3 7l7 4 7-4M3 7l7-4 7 4M3 7v8l7 4 7-4V7"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'file'">
      <path
        d="M5 3h7l3 3v11a1 1 0 01-1 1H5a1 1 0 01-1-1V4a1 1 0 011-1z"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <path d="M12 3v3h3" fill="none" stroke="currentColor" stroke-width="1.5" />
    </template>
    <template v-else-if="name === 'copy'">
      <rect
        x="7"
        y="7"
        width="10"
        height="10"
        rx="1.5"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      />
      <path
        d="M13 7V4.5A1.5 1.5 0 0011.5 3h-7A1.5 1.5 0 003 4.5v7A1.5 1.5 0 004.5 13H7"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'logout'">
      <path
        d="M7 3H4a1 1 0 00-1 1v12a1 1 0 001 1h3M13 14l4-4-4-4M17 10H8"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'expand'">
      <path
        d="M12 3h5v5M17 3l-6 6M8 17H3v-5M3 17l6-6"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else-if="name === 'collapse'">
      <path
        d="M17 3l-6 6M11 4v5h5M3 17l6-6M9 16v-5H4"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
  </svg>
</template>
