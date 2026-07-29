<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { UI, UIField, UIInput, UIIcon } from '@/design'

const authStore = useAuthStore()

const email = ref('')

async function handleSubmit() {
  if (!email.value) return
  try {
    await authStore.requestPasswordReset(email.value)
  } catch {
    // store has error
  }
}
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
        <pattern id="fpgrid" width="40" height="40" patternUnits="userSpaceOnUse">
          <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#fff" stroke-width="0.5" />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#fpgrid)" />
    </svg>

    <form
      :style="{
        position: 'relative',
        background: UI.panel,
        borderRadius: '18px',
        padding: '40px 38px',
        width: '440px',
        maxWidth: '100%',
        boxShadow: '0 30px 80px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.05)',
      }"
      @submit.prevent="handleSubmit"
    >
      <div
        style="text-align: center"
        :style="{ fontSize: '22px', fontWeight: 700, letterSpacing: '-0.4px', color: UI.text }"
      >
        jet<span :style="{ color: UI.blue }">menu</span>
      </div>

      <h1
        :style="{
          margin: '24px 0 0',
          fontSize: '22px',
          fontWeight: 700,
          letterSpacing: '-0.5px',
          color: UI.text,
          textAlign: 'center',
        }"
      >
        Recuperar senha
      </h1>
      <p
        :style="{
          fontSize: '13.5px',
          color: UI.textSub,
          marginTop: '10px',
          lineHeight: 1.6,
          textAlign: 'center',
        }"
      >
        Informe o email da sua conta e enviaremos um link para redefinir a senha.
      </p>

      <div
        v-if="authStore.error"
        :style="{
          marginTop: '18px',
          padding: '10px 12px',
          background: UI.roseBg,
          color: UI.rose2,
          borderRadius: '8px',
          fontSize: '13px',
        }"
      >
        {{ authStore.error }}
      </div>

      <div
        v-if="authStore.passwordResetEmailSent"
        :style="{
          marginTop: '18px',
          padding: '16px',
          background: 'rgba(16,185,129,0.10)',
          border: `1px solid ${UI.emerald}`,
          borderRadius: '10px',
          fontSize: '13.5px',
          color: UI.text,
          lineHeight: 1.6,
        }"
      >
        <strong>Enviamos um link de recuperação</strong> para
        <strong>{{ email }}</strong>. Abra o email e siga as instruções para
        redefinir sua senha.
      </div>

      <div v-else style="margin-top: 20px; display: flex; flex-direction: column; gap: 16px">
        <UIField label="Email">
          <UIInput v-model="email" type="email" icon="mail" placeholder="seu@email.com" required />
        </UIField>

        <button
          type="submit"
          class="ui-btn"
          :disabled="authStore.loading"
          :style="{
            padding: '12px 18px',
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
            cursor: authStore.loading ? 'not-allowed' : 'pointer',
            opacity: authStore.loading ? 0.7 : 1,
            fontFamily: 'inherit',
          }"
        >
          {{ authStore.loading ? 'Enviando…' : 'Enviar link de recuperação' }}
          <UIIcon v-if="!authStore.loading" name="arrow" :size="15" />
        </button>
      </div>

      <p
        :style="{
          marginTop: '24px',
          fontSize: '12.5px',
          color: UI.textSub,
          textAlign: 'center',
        }"
      >
        Lembrou a senha?
        <RouterLink to="/login" :style="{ color: UI.blue, fontWeight: 600, textDecoration: 'none' }">
          Voltar ao login
        </RouterLink>
      </p>
    </form>
  </div>
</template>

<style scoped>
.ui-btn {
  transition: transform 0.14s ease, box-shadow 0.14s ease, filter 0.12s ease;
}
.ui-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.1);
  filter: brightness(1.04);
}
</style>
