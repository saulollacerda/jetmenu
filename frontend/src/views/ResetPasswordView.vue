<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { UI, UIField, UIInput, UIIcon } from '@/design'

const authStore = useAuthStore()

const password = ref('')
const confirmPassword = ref('')
const validationError = ref<string | null>(null)
const done = ref(false)

async function handleSubmit() {
  validationError.value = null
  if (password.value.length < 6) {
    validationError.value = 'A senha deve ter no mínimo 6 caracteres'
    return
  }
  if (password.value !== confirmPassword.value) {
    validationError.value = 'As senhas não conferem'
    return
  }
  try {
    await authStore.updatePassword(password.value)
    done.value = true
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
        <pattern id="rpgrid" width="40" height="40" patternUnits="userSpaceOnUse">
          <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#fff" stroke-width="0.5" />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#rpgrid)" />
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

      <template v-if="done">
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
        <h1
          :style="{
            margin: 0,
            fontSize: '22px',
            fontWeight: 700,
            letterSpacing: '-0.5px',
            color: UI.text,
            textAlign: 'center',
          }"
        >
          Senha redefinida!
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
          Sua nova senha já está valendo. Acesse o painel para continuar.
        </p>
        <RouterLink
          to="/dashboard"
          class="ui-btn"
          :style="{
            marginTop: '24px',
            padding: '12px 18px',
            background: UI.blue,
            color: '#fff',
            borderRadius: '9px',
            fontSize: '14px',
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '8px',
            textDecoration: 'none',
          }"
        >
          Ir para o painel
          <UIIcon name="arrow" :size="15" />
        </RouterLink>
      </template>

      <template v-else>
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
          Redefinir senha
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
          Escolha uma nova senha para a sua conta.
        </p>

        <div
          v-if="validationError || authStore.error"
          :style="{
            marginTop: '18px',
            padding: '10px 12px',
            background: UI.roseBg,
            color: UI.rose2,
            borderRadius: '8px',
            fontSize: '13px',
          }"
        >
          {{ validationError || authStore.error }}
        </div>

        <div style="margin-top: 20px; display: flex; flex-direction: column; gap: 16px">
          <UIField label="Nova senha" hint="Mínimo 6 caracteres">
            <UIInput
              id="newPassword"
              v-model="password"
              icon="lock"
              type="password"
              placeholder="Mínimo 6 caracteres"
              required
              minlength="6"
            />
          </UIField>
          <UIField label="Confirmar nova senha">
            <UIInput
              id="confirmNewPassword"
              v-model="confirmPassword"
              icon="lock"
              type="password"
              placeholder="Repita a nova senha"
              required
            />
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
            {{ authStore.loading ? 'Salvando…' : 'Salvar nova senha' }}
            <UIIcon v-if="!authStore.loading" name="arrow" :size="15" />
          </button>
        </div>
      </template>
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
