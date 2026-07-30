<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { peekPendingPlan } from '@/lib/pendingPlan'
import type { LoginRequest } from '@/types/Auth'
import { UI, UIField, UIInput, UIIcon } from '@/design'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = ref<LoginRequest>({ email: '', password: '' })
const showPassword = ref(false)

async function handleSubmit() {
  try {
    await authStore.login(form.value)
    // A plan in the query means the visitor came from the landing page pricing CTA
    // and only logged in to get an account: resume the checkout they asked for.
    const plan = route.query.plan
    if (typeof plan === 'string' && plan) {
      router.push({ path: '/checkout', query: { plan } })
      return
    }
    // Same intent, but the plan was chosen before a sign-up that required email
    // confirmation, so it survives only in storage. CheckoutView consumes it.
    if (peekPendingPlan()) {
      router.push('/checkout')
      return
    }
    router.push('/dashboard')
  } catch {
    // error is handled in the store
  }
}
</script>

<template>
  <div
    :style="{
      minHeight: '100vh',
      display: 'flex',
      background: UI.panel,
      fontFamily: UI.font,
      color: UI.text,
    }"
  >
    <!-- Left brand pane -->
    <div
      :style="{
        width: '600px',
        background: UI.navBg,
        color: '#fff',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        overflow: 'hidden',
      }"
      class="login-brand-pane"
    >
      <svg
        width="100%"
        height="100%"
        style="position: absolute; inset: 0; opacity: 0.06"
      >
        <defs>
          <pattern id="lgrid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#fff" stroke-width="0.5" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#lgrid)" />
      </svg>
      <div
        :style="{
          position: 'absolute',
          top: '-100px',
          right: '-100px',
          width: '320px',
          height: '320px',
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(16,185,129,.35), transparent 70%)',
        }"
      />
      <div
        :style="{
          position: 'absolute',
          bottom: '-40px',
          left: '-80px',
          width: '320px',
          height: '320px',
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(37,99,235,.25), transparent 70%)',
        }"
      />

      <div
        style="
          padding: 48px;
          position: relative;
          display: flex;
          flex-direction: column;
          height: 100%;
          flex: 1;
        "
      >
        <div :style="{ fontSize: '26px', fontWeight: 700, letterSpacing: '-0.5px' }">
          jet<span :style="{ color: UI.emerald }">menu</span>
        </div>

        <div
          style="
            flex: 1;
            display: flex;
            flex-direction: column;
            justify-content: center;
            max-width: 440px;
          "
        >
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
            Sistema de gestão
          </div>
          <h1
            :style="{
              margin: 0,
              fontSize: '44px',
              fontWeight: 700,
              letterSpacing: '-1.2px',
              lineHeight: 1.1,
            }"
          >
            Gestão de cardápio<br />
            com <span :style="{ color: UI.emerald }">lucro real</span>.
          </h1>
          <p
            :style="{
              fontSize: '15px',
              color: '#94a3b8',
              marginTop: '18px',
              lineHeight: 1.6,
            }"
          >
            Cadastre fichas técnicas, controle custos por ingrediente e veja a margem de
            cada pedido em tempo real.
          </p>

          <div
            :style="{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: '20px',
              marginTop: '36px',
              paddingTop: '24px',
              borderTop: '1px solid rgba(255,255,255,.08)',
            }"
          >
            <div v-for="s in [{ v: '287', l: 'Pedidos / mês' }, { v: '63%', l: 'Margem média' }, { v: 'R$ 12k', l: 'Faturamento' }]" :key="s.l">
              <div
                :style="{
                  fontSize: '24px',
                  fontWeight: 700,
                  color: '#fff',
                  letterSpacing: '-0.6px',
                }"
              >
                {{ s.v }}
              </div>
              <div :style="{ fontSize: '11.5px', color: '#64748b', marginTop: '4px' }">
                {{ s.l }}
              </div>
            </div>
          </div>
        </div>

        <div :style="{ fontSize: '11.5px', color: '#64748b' }">© 2026 jetmenu · v 2.4</div>
      </div>
    </div>

    <!-- Right form pane -->
    <div
      style="
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 48px;
      "
    >
      <form style="width: 400px" @submit.prevent="handleSubmit">
        <div
          :style="{
            fontSize: '13px',
            color: UI.emerald2,
            fontWeight: 600,
            marginBottom: '6px',
            letterSpacing: '0.3px',
          }"
        >
          Bem-vindo de volta
        </div>
        <h2
          :style="{
            margin: 0,
            fontSize: '30px',
            fontWeight: 700,
            color: UI.text,
            letterSpacing: '-0.8px',
          }"
        >
          Entrar na sua conta
        </h2>
        <p
          :style="{
            fontSize: '13.5px',
            color: UI.textSub,
            marginTop: '8px',
            lineHeight: 1.6,
          }"
        >
          Acesse o painel para gerenciar pedidos, produtos e ingredientes.
        </p>

        <div
          v-if="authStore.error"
          :style="{
            marginTop: '24px',
            padding: '10px 12px',
            background: UI.roseBg,
            color: UI.rose2,
            borderRadius: '8px',
            fontSize: '13px',
            border: `1px solid ${UI.roseBg}`,
          }"
        >
          {{ authStore.error }}
        </div>

        <div style="margin-top: 32px; display: flex; flex-direction: column; gap: 16px">
          <UIField label="Email">
            <UIInput
              v-model="form.email"
              type="email"
              icon="mail"
              placeholder="seu@email.com"
            />
          </UIField>
          <UIField label="Senha">
            <UIInput
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              icon="lock"
              placeholder="Sua senha"
            >
              <template #rightAddon>
                <span
                  :style="{
                    fontSize: '11.5px',
                    color: UI.blue,
                    fontWeight: 600,
                    cursor: 'pointer',
                  }"
                  @click="showPassword = !showPassword"
                >
                  {{ showPassword ? 'ocultar' : 'mostrar' }}
                </span>
              </template>
            </UIInput>
          </UIField>

          <div style="display: flex; justify-content: flex-end; align-items: center">
            <RouterLink
              to="/esqueci-senha"
              :style="{
                fontSize: '12.5px',
                color: UI.blue,
                fontWeight: 600,
                textDecoration: 'none',
              }"
            >
              Esqueceu a senha?
            </RouterLink>
          </div>

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
              marginTop: '6px',
              opacity: authStore.loading ? 0.7 : 1,
              fontFamily: 'inherit',
            }"
          >
            {{ authStore.loading ? 'Entrando…' : 'Entrar' }}
            <UIIcon v-if="!authStore.loading" name="arrow" :size="15" />
          </button>
        </div>

        <p
          :style="{
            marginTop: '28px',
            fontSize: '12.5px',
            color: UI.textSub,
            textAlign: 'center',
          }"
        >
          Ainda não tem conta?
          <RouterLink
            to="/register"
            :style="{ color: UI.blue, fontWeight: 600, textDecoration: 'none' }"
          >
            Crie uma grátis
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
  .login-brand-pane {
    display: none !important;
  }
}
</style>
