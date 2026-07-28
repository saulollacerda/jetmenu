<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import type { UserRequest } from '@/types/User'
import { UI, UIField, UIInput, UIIcon } from '@/design'

const authStore = useAuthStore()

const validationError = ref<string | null>(null)
const accepted = ref(false)

const form = ref<UserRequest>({
  merchantName: '',
  cnpj: '',
  email: '',
  password: '',
  confirmPassword: '',
  phone: '',
})

function isValidCnpj(value: string) {
  const digits = value.replace(/\D/g, '')
  if (digits.length !== 14) return false
  if (/^(\d)\1{13}$/.test(digits)) return false
  const weightsFirst = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
  const weightsSecond = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
  const calc = (base: string, w: number[]) => {
    const sum = base
      .split('')
      .reduce((t, d, i) => t + Number(d) * (w[i] ?? 0), 0)
    const r = sum % 11
    return r < 2 ? 0 : 11 - r
  }
  const a = calc(digits.slice(0, 12), weightsFirst)
  const b = calc(digits.slice(0, 12) + a, weightsSecond)
  return digits[12] === String(a) && digits[13] === String(b)
}

async function handleSubmit() {
  validationError.value = null
  if (!accepted.value) {
    validationError.value = 'É preciso aceitar os termos para criar a conta'
    return
  }
  if (!isValidCnpj(form.value.cnpj)) {
    validationError.value = 'CNPJ inválido'
    return
  }
  if (form.value.password.length < 6) {
    validationError.value = 'A senha deve ter no mínimo 6 caracteres'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    validationError.value = 'As senhas não conferem'
    return
  }
  try {
    await authStore.register(form.value)
    // Email confirmation is required: stay here and show the confirmation notice
    // (authStore.awaitingEmailConfirmation becomes true).
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
      position: 'relative',
      background: UI.navBg,
      color: UI.text,
      fontFamily: UI.font,
      overflow: 'hidden',
    }"
  >
    <svg width="100%" height="100%" style="position: absolute; inset: 0; opacity: 0.05">
      <defs>
        <pattern id="rgrid" width="40" height="40" patternUnits="userSpaceOnUse">
          <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#fff" stroke-width="0.5" />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#rgrid)" />
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
      class="reg-tag"
      style="
        flex: 1;
        position: relative;
        display: flex;
        flex-direction: column;
        justify-content: center;
        padding: 0 80px;
        color: #fff;
      "
    >
      <div :style="{ fontSize: '28px', fontWeight: 700, letterSpacing: '-0.5px', marginBottom: '36px' }">
        jet<span :style="{ color: UI.emerald }">menu</span>
      </div>
      <div
        :style="{
          fontSize: '12px',
          color: UI.emerald,
          fontWeight: 600,
          letterSpacing: '1.2px',
          textTransform: 'uppercase',
          marginBottom: '18px',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }"
      >
        <span :style="{ width: '28px', height: '1px', background: UI.emerald }" />
        B2B · Para lojas
      </div>
      <h1
        :style="{
          margin: 0,
          fontSize: '42px',
          fontWeight: 700,
          letterSpacing: '-1.2px',
          lineHeight: 1.1,
        }"
      >
        Cadastre sua loja<br />em
        <span :style="{ color: UI.emerald }">menos de 2 min</span>.
      </h1>
      <p
        :style="{
          fontSize: '14px',
          color: '#94a3b8',
          marginTop: '20px',
          lineHeight: 1.6,
          maxWidth: '420px',
        }"
      >
        Em seguida você poderá importar seu cardápio do Anota.AI, cadastrar
        ingredientes e começar a ver o lucro real de cada pedido.
      </p>
      <div style="margin-top: 36px; display: flex; flex-direction: column; gap: 14px; max-width: 380px">
        <div
          v-for="t in ['Importação automática de pedidos', 'Custo calculado por ingrediente', 'Margem em tempo real para cada pedido']"
          :key="t"
          :style="{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '13.5px', color: '#cbd5e1' }"
        >
          <span
            :style="{
              width: '22px',
              height: '22px',
              borderRadius: '11px',
              background: 'rgba(16,185,129,0.15)',
              color: UI.emerald,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }"
          >
            <UIIcon name="check" :size="13" />
          </span>
          {{ t }}
        </div>
      </div>
    </div>

    <div
      style="
        flex: 1;
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 48px;
      "
    >
      <form
        :style="{
          background: UI.panel,
          borderRadius: '18px',
          padding: '34px 36px',
          width: '420px',
          boxShadow: '0 30px 80px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.05)',
        }"
        @submit.prevent="handleSubmit"
      >
        <div style="text-align: center; margin-bottom: 24px">
          <div :style="{ fontSize: '22px', fontWeight: 700, letterSpacing: '-0.4px', color: UI.text }">
            jet<span :style="{ color: UI.blue }">menu</span>
          </div>
          <div :style="{ fontSize: '14px', color: UI.textSub, marginTop: '6px' }">
            Crie sua conta para começar
          </div>
        </div>

        <div
          v-if="validationError || authStore.error"
          :style="{
            marginBottom: '14px',
            padding: '10px 12px',
            background: UI.roseBg,
            color: UI.rose2,
            borderRadius: '8px',
            fontSize: '13px',
          }"
        >
          {{ validationError || authStore.error }}
        </div>

        <div
          v-if="authStore.awaitingEmailConfirmation"
          :style="{
            padding: '16px',
            background: 'rgba(16,185,129,0.10)',
            border: `1px solid ${UI.emerald}`,
            borderRadius: '10px',
            fontSize: '13.5px',
            color: UI.text,
            lineHeight: 1.6,
          }"
        >
          <strong>Quase lá!</strong> Enviamos um email de confirmação para
          <strong>{{ form.email }}</strong>. Confirme seu email e depois
          <RouterLink to="/login" :style="{ color: UI.blue, fontWeight: 600 }">faça login</RouterLink>
          para concluir o cadastro.
        </div>

        <div v-else style="display: flex; flex-direction: column; gap: 14px">
          <UIField label="Nome do Restaurante">
            <UIInput id="merchantName" v-model="form.merchantName" placeholder="Ex: Pizzaria Napoli" required />
          </UIField>
          <UIField label="CNPJ">
            <UIInput id="cnpj" v-model="form.cnpj" placeholder="00.000.000/0000-00" required />
          </UIField>
          <UIField label="Email">
            <UIInput id="email" v-model="form.email" icon="mail" type="email" placeholder="seu@email.com" required />
          </UIField>
          <UIField label="Senha" hint="Mínimo 6 caracteres">
            <UIInput
              id="password"
              v-model="form.password"
              icon="lock"
              type="password"
              placeholder="Mínimo 6 caracteres"
              required
              minlength="6"
            />
          </UIField>
          <UIField label="Confirmar Senha">
            <UIInput
              id="confirmPassword"
              v-model="form.confirmPassword"
              icon="lock"
              type="password"
              placeholder="Repita sua senha"
              required
            />
          </UIField>
          <UIField>
            <template #label>
              Telefone
              <span :style="{ color: UI.textMute, fontWeight: 400 }">(opcional)</span>
            </template>
            <UIInput id="phone" v-model="form.phone" type="tel" placeholder="(11) 99999-9999" />
          </UIField>

          <label
            :style="{
              display: 'flex',
              alignItems: 'flex-start',
              gap: '9px',
              fontSize: '12px',
              color: UI.textSub,
              marginTop: '4px',
              cursor: 'pointer',
            }"
            @click.prevent="accepted = !accepted"
          >
            <span
              :style="{
                width: '16px',
                height: '16px',
                borderRadius: '4px',
                marginTop: '1px',
                background: accepted ? UI.blue : UI.panel,
                border: `1px solid ${accepted ? UI.blue : UI.border}`,
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }"
            >
              <UIIcon v-if="accepted" name="check" :size="10" />
            </span>
            <span>
              Aceito os
              <span :style="{ color: UI.blue, fontWeight: 600 }">termos de uso</span>
              e a
              <span :style="{ color: UI.blue, fontWeight: 600 }">política de privacidade</span>.
            </span>
          </label>

          <button
            type="submit"
            class="ui-btn"
            :disabled="authStore.loading"
            :style="{
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
              cursor: authStore.loading ? 'not-allowed' : 'pointer',
              marginTop: '4px',
              opacity: authStore.loading ? 0.7 : 1,
              fontFamily: 'inherit',
            }"
          >
            {{ authStore.loading ? 'Criando…' : 'Criar Conta' }}
            <UIIcon v-if="!authStore.loading" name="arrow" :size="15" />
          </button>
        </div>

        <p
          :style="{
            marginTop: '20px',
            fontSize: '12.5px',
            color: UI.textSub,
            textAlign: 'center',
          }"
        >
          Já tem uma conta?
          <RouterLink
            to="/login"
            :style="{ color: UI.blue, fontWeight: 600, textDecoration: 'none' }"
          >
            Faça login
          </RouterLink>
        </p>
      </form>
    </div>
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
@media (max-width: 900px) {
  .reg-tag {
    display: none !important;
  }
}
</style>
