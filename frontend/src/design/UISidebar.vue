<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { UI } from './tokens'
import UIIcon from './UIIcon.vue'
import { useAuthStore } from '@/stores/authStore'
import { useSidebar } from '@/composables/useSidebar'
import type { DayOfWeek, OpeningHour } from '@/types/User'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const sidebar = useSidebar()

const DAY_MAP: Record<number, DayOfWeek> = {
  0: 'SUNDAY', 1: 'MONDAY', 2: 'TUESDAY', 3: 'WEDNESDAY',
  4: 'THURSDAY', 5: 'FRIDAY', 6: 'SATURDAY',
}

function checkIsOpen(hours: OpeningHour[] | null | undefined): boolean {
  if (!hours || hours.length === 0) return false
  const now = new Date(new Date().toLocaleString('en-US', { timeZone: 'America/Sao_Paulo' }))
  const todayKey = DAY_MAP[now.getDay()]
  const hour = hours.find((h) => h.dayOfWeek === todayKey)
  if (!hour || hour.closed || !hour.openTime || !hour.closeTime) return false
  const [oh = 0, om = 0] = hour.openTime.split(':').map(Number)
  const [ch = 0, cm = 0] = hour.closeTime.split(':').map(Number)
  const nowMin = now.getHours() * 60 + now.getMinutes()
  return nowMin >= oh * 60 + om && nowMin < ch * 60 + cm
}

const storeStatus = computed(() => {
  const hours = auth.currentUser?.openingHours
  if (!hours || hours.length === 0) return null
  return checkIsOpen(hours) ? 'open' : 'closed'
})

const NAV = [
  { id: 'dashboard', to: '/dashboard', ic: 'home', label: 'Dashboard' },
  { id: 'orders', to: '/orders', ic: 'cart', label: 'Pedidos' },
  { id: 'products', to: '/products', ic: 'burger', label: 'Produtos' },
  { id: 'ingredients', to: '/ingredients', ic: 'leaf', label: 'Ingredientes' },
  { id: 'customers', to: '/customers', ic: 'user', label: 'Clientes' },
  { id: 'fees', to: '/fees', ic: 'card', label: 'Taxas' },
  { id: 'settings', to: '/settings', ic: 'gear', label: 'Configurações' },
]

const activeId = computed(() => {
  const path = route.path
  if (path === '/dashboard') return 'dashboard'
  const match = NAV.find((n) => path.startsWith(n.to))
  return match?.id ?? 'dashboard'
})

const initials = computed(() => {
  const name = auth.restaurantName || 'MB'
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || 'MB'
})

function navigate(to: string) {
  sidebar.close()
  router.push(to)
}

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div
    v-if="sidebar.isOpen.value"
    class="ui-sidebar-backdrop"
    data-testid="sidebar-backdrop"
    @click="sidebar.close()"
  />
  <aside class="ui-sidebar" :class="{ 'is-open': sidebar.isOpen.value }">
    <div
      :style="{
        padding: '24px 22px 22px',
        borderBottom: '1px solid #1a2638',
        cursor: 'pointer',
      }"
      @click="navigate('/')"
    >
      <div
        :style="{
          fontSize: '21px',
          fontWeight: 700,
          letterSpacing: '-0.4px',
          color: '#fff',
        }"
      >
        jet<span :style="{ color: UI.emerald }">menu</span>
      </div>
      <div
        :style="{
          fontSize: '11px',
          color: '#64748b',
          marginTop: '3px',
          letterSpacing: '0.2px',
        }"
      >
        gestão · custo · lucro
      </div>
    </div>

    <nav
      style="
        padding: 14px 12px;
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 2px;
        overflow-y: auto;
      "
    >
      <div
        v-for="it in NAV"
        :key="it.id"
        class="ui-nav"
        :class="{ 'is-active': it.id === activeId }"
        :style="{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          padding: '9px 12px',
          borderRadius: '8px',
          fontSize: '13.5px',
          fontWeight: 500,
          background: it.id === activeId ? UI.navActive : 'transparent',
          color: it.id === activeId ? '#fff' : UI.navText,
          cursor: 'pointer',
        }"
        @click="navigate(it.to)"
      >
        <UIIcon :name="it.ic" :size="16" />
        <span style="flex: 1">{{ it.label }}</span>
      </div>
    </nav>

    <div :style="{ padding: '12px', borderTop: '1px solid #1a2638', flexShrink: 0 }">
      <div
        :style="{
          background: '#0a1322',
          padding: '11px 12px',
          borderRadius: '8px',
          display: 'flex',
          alignItems: 'center',
          gap: '11px',
        }"
      >
        <div
          :style="{
            width: '32px',
            height: '32px',
            borderRadius: '8px',
            background: 'linear-gradient(135deg,#10b981,#059669)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '14px',
            fontWeight: 700,
            color: '#fff',
            flexShrink: 0,
          }"
        >
          {{ initials }}
        </div>
        <div :style="{ flex: 1, minWidth: 0 }">
          <div :style="{ fontSize: '12.5px', fontWeight: 600, color: '#fff' }">
            {{ auth.restaurantName || 'JetMenu' }}
          </div>
          <div
            v-if="storeStatus !== null"
            :style="{
              fontSize: '11px',
              color: '#64748b',
              display: 'flex',
              alignItems: 'center',
              gap: '5px',
            }"
          >
            <span
              :style="{
                width: '6px',
                height: '6px',
                borderRadius: '3px',
                background: storeStatus === 'open' ? UI.emerald : UI.rose,
              }"
            />
            {{ storeStatus === 'open' ? 'Loja aberta' : 'Loja fechada' }}
          </div>
        </div>
        <button
          class="ui-logout-btn"
          title="Sair"
          @click="logout"
        >
          <UIIcon name="logout" :size="15" />
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.ui-sidebar {
  width: 232px;
  background: #0c1626;
  color: #cbd5e1;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid #0a1322;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
}

.ui-sidebar-backdrop {
  display: none;
}

@media (max-width: 768px) {
  .ui-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    height: 100dvh;
    z-index: 300;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
    box-shadow: 0 0 40px rgba(0, 0, 0, 0.35);
  }
  .ui-sidebar.is-open {
    transform: translateX(0);
  }
  .ui-sidebar-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, 0.5);
    z-index: 250;
  }
}

.ui-nav {
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.ui-nav:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  transform: translateX(3px);
}
.ui-nav.is-active:hover {
  background: #2563eb !important;
}
.ui-logout-btn {
  background: transparent;
  border: none;
  color: #475569;
  cursor: pointer;
  padding: 5px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.12s, color 0.12s;
}
.ui-logout-btn:hover {
  background: rgba(225, 29, 72, 0.15);
  color: #f87171;
}
</style>
