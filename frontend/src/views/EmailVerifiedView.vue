<script setup lang="ts">
import { useAuthStore } from '@/stores/authStore'
import { UI, UIIcon } from '@/design'

// The confirmation link signs the user in (detectSessionInUrl); the CTA must not
// send an already-authenticated user to a login page they would bounce off of.
const authStore = useAuthStore()
</script>

<template>
  <div
    :style="{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      position: 'relative',
      background: UI.navBg,
      color: UI.text,
      fontFamily: UI.font,
      overflow: 'hidden',
      padding: '24px',
    }"
  >
    <svg width="100%" height="100%" style="position: absolute; inset: 0; opacity: 0.05">
      <defs>
        <pattern id="evgrid" width="40" height="40" patternUnits="userSpaceOnUse">
          <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#fff" stroke-width="0.5" />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#evgrid)" />
    </svg>
    <div
      :style="{
        position: 'absolute',
        top: '80px',
        left: '120px',
        width: '320px',
        height: '320px',
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(16,185,129,.28), transparent 70%)',
      }"
    />
    <div
      :style="{
        position: 'absolute',
        bottom: '80px',
        right: '120px',
        width: '360px',
        height: '360px',
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(37,99,235,.24), transparent 70%)',
      }"
    />

    <div
      :style="{
        position: 'relative',
        background: UI.panel,
        borderRadius: '18px',
        padding: '40px 38px',
        width: '440px',
        maxWidth: '100%',
        textAlign: 'center',
        boxShadow: '0 30px 80px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.05)',
      }"
    >
      <div :style="{ fontSize: '22px', fontWeight: 700, letterSpacing: '-0.4px', color: UI.text }">
        jet<span :style="{ color: UI.blue }">menu</span>
      </div>

      <div
        :style="{
          margin: '28px auto 22px',
          width: '64px',
          height: '64px',
          borderRadius: '50%',
          background: 'rgba(16,185,129,0.15)',
          color: UI.emerald,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }"
      >
        <UIIcon name="check" :size="30" />
      </div>

      <h1 :style="{ margin: 0, fontSize: '24px', fontWeight: 700, letterSpacing: '-0.6px', color: UI.text }">
        Seu email foi verificado!
      </h1>
      <p
        :style="{
          fontSize: '14px',
          color: UI.textSub,
          marginTop: '12px',
          lineHeight: 1.6,
        }"
      >
        <template v-if="authStore.isAuthenticated">
          Sua conta está confirmada e você já está conectado. Acesse o painel para
          começar a gerenciar sua loja.
        </template>
        <template v-else>
          Sua conta está confirmada e pronta para uso. Entre com seu email e senha
          para acessar o painel e começar a gerenciar sua loja.
        </template>
      </p>

      <RouterLink
        :to="authStore.isAuthenticated ? '/dashboard' : '/login'"
        class="ui-btn"
        :style="{
          marginTop: '28px',
          padding: '13px 18px',
          background: UI.blue,
          color: '#fff',
          border: 'none',
          borderRadius: '9px',
          fontSize: '14px',
          fontWeight: 600,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '8px',
          textDecoration: 'none',
          cursor: 'pointer',
        }"
      >
        {{ authStore.isAuthenticated ? 'Ir para o painel' : 'Ir para o login' }}
        <UIIcon name="arrow" :size="15" />
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.ui-btn {
  transition: transform 0.14s ease, box-shadow 0.14s ease, filter 0.12s ease;
}
.ui-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.1);
  filter: brightness(1.04);
}
</style>
