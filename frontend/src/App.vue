<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import UISidebar from '@/design/UISidebar.vue'
import UIToast from '@/design/UIToast.vue'
import SubscriptionExpiredOverlay from '@/components/SubscriptionExpiredOverlay.vue'
import IfoodDiagnosticsWindow from '@/components/IfoodDiagnosticsWindow.vue'
import { useIfoodDiagnostics } from '@/composables/useIfoodDiagnostics'

const authStore = useAuthStore()
const route = useRoute()
const diagnostics = useIfoodDiagnostics()

const showSidebar = () => authStore.isAuthenticated && !route.meta.public
</script>

<template>
  <UIToast />
  <div v-if="showSidebar()" class="app-layout">
    <UISidebar />
    <main class="main-content">
      <RouterView />
    </main>
    <SubscriptionExpiredOverlay />
    <IfoodDiagnosticsWindow v-if="diagnostics.isOpen.value" @close="diagnostics.close()" />
  </div>
  <RouterView v-else />
</template>

<style scoped></style>
