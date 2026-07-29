<template>
  <div class="lp-root">

    <!-- NAV -->
    <header class="lp-nav">
      <div class="lp-nav-inner">
        <div class="lp-wordmark">jet<span>menu</span></div>
        <nav>
          <ul class="lp-nav-links">
            <li><a href="#features">Funcionalidades</a></li>
            <li><a href="#how">Como funciona</a></li>
            <li><a href="#pricing">Preços</a></li>
            <li><a href="#faq">FAQ</a></li>
          </ul>
        </nav>
        <div class="lp-spacer"></div>
        <RouterLink to="/planos" class="lp-btn lp-btn-sm">
          Ver planos e preços
          <IconArrow :size="14" />
        </RouterLink>
      </div>
    </header>

    <!-- HERO -->
    <section class="lp-hero">
      <div class="lp-hero-inner">
        <div style="display:flex;justify-content:center;margin-bottom:26px;">
          <div class="lp-hero-pill">
            <span class="lp-hero-pill-badge">NOVO</span>
            Integração nativa com Anota.AI
            <IconArrow :size="13" color="#94a3b8" />
          </div>
        </div>

        <h1 class="lp-hero-h1">
          Quanto sua loja ganha<br><span class="lp-accent"> de verdade</span> em cada pedido?
        </h1>
        <p class="lp-hero-sub">
          JetMenu calcula o custo por ingrediente, importa seus pedidos do Anota.AI / iFood
          e mostra a <strong style="color:#0f172a">margem real</strong> de cada venda — em tempo real, sem planilha.
        </p>

        <div class="lp-hero-ctas">
          <RouterLink to="/planos" class="lp-btn lp-btn-primary">
            Conhecer os planos
            <IconArrow :size="15" />
          </RouterLink>
          <a href="#how" class="lp-btn lp-btn-secondary">Ver como funciona</a>
        </div>

        <div class="lp-hero-bullets">
          <span><IconCheck :size="14" color="#059669" /> Sem fidelidade</span>
          <span><IconCheck :size="14" color="#059669" /> Cancele quando quiser</span>
          <span><IconCheck :size="14" color="#059669" /> Migração assistida</span>
        </div>

        <!-- Dashboard preview -->
        <div class="lp-hero-preview">
          <!-- Floating chip left -->
          <div class="lp-chip lp-float-l" style="left:-10px;top:80px;">
            <div class="lp-chip-icon" style="background:#ecfdf5;color:#059669;">
              <IconChart :size="18" />
            </div>
            <div>
              <div class="lp-chip-label">Lucro estimado</div>
              <div class="lp-chip-value">R$ 7.890,50</div>
            </div>
          </div>

          <!-- Floating chip right -->
          <div class="lp-chip lp-float-r" style="right:-10px;bottom:80px;max-width:240px;">
            <div class="lp-chip-icon" style="background:#fffbeb;color:#f59e0b;">
              <IconBell :size="18" />
            </div>
            <div>
              <div class="lp-chip-label">Alerta automático</div>
              <div class="lp-chip-value" style="font-size:12.5px;">Ingrediente não cadastrado</div>
            </div>
          </div>

          <!-- Browser frame -->
          <div class="lp-browser-frame">
            <div class="lp-browser-chrome">
              <span class="lp-browser-dot" style="background:#ff6058"></span>
              <span class="lp-browser-dot" style="background:#ffbd2e"></span>
              <span class="lp-browser-dot" style="background:#27c940"></span>
              <span class="lp-browser-url">jetmenu.app · Goat Açaí · Dashboard</span>
            </div>
            <!-- Mini dashboard -->
            <div class="lp-mini-dash">
              <aside class="lp-mini-sidebar">
                <div class="lp-mini-logo">
                  <div class="lp-wordmark" style="font-size:14px;">jet<span>menu</span></div>
                  <div style="font-size:7px;color:#64748b;margin-top:2px;letter-spacing:.2px;">gestão · custo · lucro</div>
                </div>
                <nav class="lp-mini-nav">
                  <div class="lp-mini-item active">
                    <svg width="10" height="10" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 10l7-6 7 6v8a1 1 0 01-1 1h-3v-5h-6v5H4a1 1 0 01-1-1v-8z"/></svg>
                    Dashboard
                  </div>
                  <div class="lp-mini-item">
                    <svg width="10" height="10" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 4h2l1.5 9h10l1.5-7H6" stroke-linejoin="round"/><circle cx="8" cy="17" r="1.4"/><circle cx="16" cy="17" r="1.4"/></svg>
                    Pedidos
                    <span style="margin-left:auto;background:#1f2937;color:#fff;font-size:5px;padding:1px 4px;border-radius:6px;font-weight:700;">7</span>
                  </div>
                  <div class="lp-mini-item">
                    <svg width="10" height="10" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 8c0-2 3-4 7-4s7 2 7 4M3 12h14M3 16c0 1 1 2 3 2h8c2 0 3-1 3-2" stroke-linecap="round"/></svg>
                    Produtos
                  </div>
                  <div class="lp-mini-item">
                    <svg width="10" height="10" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 6a1 1 0 011-1h4l2 2h7a1 1 0 011 1v8a1 1 0 01-1 1H4a1 1 0 01-1-1V6z"/></svg>
                    Categorias
                  </div>
                  <div class="lp-mini-item">
                    <svg width="10" height="10" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 16c0-7 5-12 14-12-1 9-6 14-13 14" stroke-linejoin="round"/><path d="M5 16l10-10"/></svg>
                    Ingredientes
                  </div>
                </nav>
                <div class="lp-mini-store">
                  <div class="lp-mini-store-inner">
                    <div class="lp-mini-avatar">GA</div>
                    <div>
                      <div style="font-size:7.5px;font-weight:600;color:#fff;">Goat Açaí</div>
                      <div style="font-size:6px;color:#64748b;display:flex;align-items:center;gap:3px;margin-top:1px;">
                        <span style="width:4px;height:4px;border-radius:50%;background:#10b981;display:inline-block;"></span>
                        Loja aberta
                      </div>
                    </div>
                  </div>
                </div>
              </aside>

              <div class="lp-mini-main">
                <div class="lp-mini-topbar">
                  <div>
                    <div style="font-size:11px;font-weight:700;color:#0f172a;">Dashboard</div>
                    <div style="font-size:7px;color:#94a3b8;margin-top:1px;">Maio 2026 · atualizado agora</div>
                  </div>
                  <div style="display:flex;gap:6px;align-items:center;">
                    <div class="lp-mini-notif">
                      <svg width="10" height="10" viewBox="0 0 20 20" fill="none" stroke="#64748b" stroke-width="1.5"><path d="M5 8a5 5 0 1110 0v3l1.5 3h-13L5 11V8z" stroke-linejoin="round"/><path d="M8 17c.4 1 1.1 1.5 2 1.5s1.6-.5 2-1.5"/></svg>
                      <span class="lp-mini-notif-badge">3</span>
                    </div>
                    <div class="lp-mini-btn">+ Novo pedido</div>
                  </div>
                </div>

                <div class="lp-mini-content">
                  <div class="lp-mini-kpis">
                    <div class="lp-mini-kpi">
                      <div class="lp-mini-kpi-header">
                        <div class="lp-mini-kpi-icon" style="background:#ecfdf5;color:#059669;">
                          <svg width="11" height="11" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2.5" y="5.5" width="15" height="9" rx="1.5"/><circle cx="10" cy="10" r="2"/></svg>
                        </div>
                        <div class="lp-mini-kpi-label">Faturamento</div>
                      </div>
                      <div class="lp-mini-kpi-value">R$ 31.240</div>
                      <div class="lp-mini-kpi-change up">▲ +12,4%</div>
                    </div>
                    <div class="lp-mini-kpi">
                      <div class="lp-mini-kpi-header">
                        <div class="lp-mini-kpi-icon" style="background:#ecfdf5;color:#059669;">
                          <svg width="11" height="11" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13l4-4 3 3 7-7"/><path d="M13 5h4v4"/></svg>
                        </div>
                        <div class="lp-mini-kpi-label">Lucro estimado</div>
                      </div>
                      <div class="lp-mini-kpi-value" style="color:#059669;">R$ 7.890</div>
                      <div class="lp-mini-kpi-change up">▲ 25,3% margem</div>
                    </div>
                    <div class="lp-mini-kpi">
                      <div class="lp-mini-kpi-header">
                        <div class="lp-mini-kpi-icon" style="background:#eff6ff;color:#2563eb;">
                          <svg width="11" height="11" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 4h2l1.5 9h10l1.5-7H6" stroke-linejoin="round"/><circle cx="8" cy="17" r="1.4"/><circle cx="16" cy="17" r="1.4"/></svg>
                        </div>
                        <div class="lp-mini-kpi-label">Pedidos</div>
                      </div>
                      <div class="lp-mini-kpi-value">1.284</div>
                      <div class="lp-mini-kpi-change up">▲ +8,7%</div>
                    </div>
                    <div class="lp-mini-kpi">
                      <div class="lp-mini-kpi-header">
                        <div class="lp-mini-kpi-icon" style="background:#fffbeb;color:#f59e0b;">
                          <svg width="11" height="11" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M10 3l8 14H2L10 3z" stroke-linejoin="round"/><path d="M10 8v4M10 14.5v.5" stroke-linecap="round"/></svg>
                        </div>
                        <div class="lp-mini-kpi-label">Ticket médio</div>
                      </div>
                      <div class="lp-mini-kpi-value">R$ 24,33</div>
                      <div class="lp-mini-kpi-change down">▼ -1,2%</div>
                    </div>
                  </div>

                  <div class="lp-mini-row">
                    <div class="lp-mini-chart-card">
                      <div class="lp-mini-card-title">Vendas por dia — Maio 2026</div>
                      <div class="lp-mini-bars">
                        <div class="lp-mini-bar" style="height:55%;background:#2563eb;opacity:.3;"></div>
                        <div class="lp-mini-bar" style="height:70%;background:#2563eb;opacity:.4;"></div>
                        <div class="lp-mini-bar" style="height:60%;background:#2563eb;opacity:.3;"></div>
                        <div class="lp-mini-bar" style="height:85%;background:#2563eb;opacity:.5;"></div>
                        <div class="lp-mini-bar" style="height:75%;background:#2563eb;opacity:.4;"></div>
                        <div class="lp-mini-bar" style="height:90%;background:#2563eb;"></div>
                        <div class="lp-mini-bar" style="height:80%;background:#059669;"></div>
                        <div class="lp-mini-bar" style="height:65%;background:#2563eb;opacity:.4;"></div>
                        <div class="lp-mini-bar" style="height:72%;background:#2563eb;opacity:.4;"></div>
                        <div class="lp-mini-bar" style="height:88%;background:#2563eb;opacity:.6;"></div>
                        <div class="lp-mini-bar" style="height:95%;background:#2563eb;"></div>
                        <div class="lp-mini-bar" style="height:78%;background:#2563eb;opacity:.5;"></div>
                        <div class="lp-mini-bar" style="height:60%;background:#2563eb;opacity:.3;"></div>
                        <div class="lp-mini-bar" style="height:45%;background:#2563eb;opacity:.25;"></div>
                      </div>
                    </div>
                    <div class="lp-mini-sidebar-card">
                      <div class="lp-mini-card-title">Top produtos</div>
                      <div v-for="(p, i) in topProducts" :key="i" class="lp-mini-product-row">
                        <div class="lp-mini-product-color" :style="{ background: p.color }"></div>
                        <div class="lp-mini-product-name">{{ p.name }}</div>
                        <div class="lp-mini-product-margin" :style="{ color: p.marginColor }">{{ p.margin }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- STATS -->
    <section class="lp-stats">
      <div class="lp-container-md">
        <div class="lp-stats-grid">
          <div v-for="s in stats" :key="s.v" class="lp-stat-item">
            <div class="lp-stat-value">{{ s.v }}</div>
            <div class="lp-stat-label">{{ s.l }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- PROBLEM -->
    <section class="lp-problem">
      <div class="lp-container-md">
        <div class="lp-section-header">
          <div class="lp-eyebrow">O Problema</div>
          <h2 class="lp-section-h2">
            Vender muito não é a mesma coisa que <span style="color:#e11d48">lucrar muito</span>.
          </h2>
        </div>
        <div class="lp-three-col">
          <div v-for="p in pains" :key="p.eyebrow" class="lp-card lp-problem-card">
            <div style="display:flex;align-items:center;gap:10px;">
              <div class="lp-problem-icon" :style="{ background: p.iconBg, color: p.iconColor }">
                <component :is="p.icon" :size="20" />
              </div>
              <div class="lp-problem-eyebrow">{{ p.eyebrow }}</div>
            </div>
            <div class="lp-problem-title">{{ p.title }}</div>
            <div class="lp-problem-body">{{ p.body }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- HOW IT WORKS -->
    <section id="how" class="lp-how">
      <div class="lp-container-md">
        <div class="lp-section-header">
          <div class="lp-eyebrow">Como funciona</div>
          <h2 class="lp-section-h2">
            Três passos. <span style="color:#059669">Quatro minutos.</span>
          </h2>
          <p class="lp-section-sub">Você só precisa configurar uma vez. Depois disso, o jetmenu trabalha sozinho.</p>
        </div>

        <div class="lp-how-steps">
          <!-- Step 01 -->
          <div class="lp-how-step">
            <div>
              <div class="lp-step-badge">Passo 01</div>
              <h3 class="lp-step-title">Cadastre seus ingredientes</h3>
              <p class="lp-step-body">Informe valor da compra e quantidade. O custo por unidade (R$ por grama, mL, un) é calculado automaticamente.</p>
            </div>
            <div class="lp-how-visual">
              <div class="lp-visual-label">Novo ingrediente</div>
              <div style="font-size:16px;font-weight:600;margin-bottom:14px;">Açaí Goat</div>
              <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:14px;">
                <div>
                  <div class="lp-field-label">Valor</div>
                  <div class="lp-field-value">R$ 195,00</div>
                </div>
                <div>
                  <div class="lp-field-label">Quantidade</div>
                  <div class="lp-field-value">9.000 g</div>
                </div>
              </div>
              <div class="lp-result-box">
                <IconChart :size="16" color="#059669" />
                <div style="font-size:12px;">
                  <span style="color:#475569">Custo calculado:</span>
                  <strong style="margin-left:6px;color:#059669;font-size:14px;">R$ 0,0217 / g</strong>
                </div>
              </div>
            </div>
          </div>

          <!-- Step 02 -->
          <div class="lp-how-step lp-how-step--flip">
            <div>
              <div class="lp-step-badge">Passo 02</div>
              <h3 class="lp-step-title">Monte a ficha técnica dos produtos</h3>
              <p class="lp-step-body">Ligue cada produto aos ingredientes que ele usa. O custo total do produto e a margem aparecem na hora.</p>
            </div>
            <div class="lp-how-visual">
              <div class="lp-visual-label">Ficha técnica</div>
              <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:14px;">
                <div style="font-size:16px;font-weight:600;">Açaí 400 ml</div>
                <div style="font-size:13px;color:#059669;font-weight:700;">74,5% margem</div>
              </div>
              <div style="display:flex;flex-direction:column;gap:8px;">
                <div v-for="(it, i) in recipeItems" :key="i" class="lp-recipe-row" :style="{ borderBottom: i < 2 ? '1px solid #eef0f3' : 'none' }">
                  <span>{{ it.n }}</span>
                  <span style="color:#475569">{{ it.q }}</span>
                  <span style="text-align:right;font-weight:600;font-variant-numeric:tabular-nums;">{{ it.c }}</span>
                </div>
              </div>
              <div style="margin-top:14px;padding-top:12px;border-top:1px solid #e8eaee;display:flex;justify-content:space-between;">
                <span style="font-size:13px;font-weight:600;">Preço · Custo</span>
                <span style="font-size:13px;font-weight:700;font-variant-numeric:tabular-nums;">
                  R$ 17,99 · <span style="color:#475569;font-weight:500;">R$ 4,59</span>
                </span>
              </div>
            </div>
          </div>

          <!-- Step 03 -->
          <div class="lp-how-step">
            <div>
              <div class="lp-step-badge">Passo 03</div>
              <h3 class="lp-step-title">Conecte seus canais de venda</h3>
              <p class="lp-step-body">Anota.AI, iFood, balcão. Cada pedido entra com cliente, status e — o que importa — o lucro real estimado.</p>
            </div>
            <div class="lp-how-visual">
              <div class="lp-visual-label">Canais conectados</div>
              <div style="display:flex;flex-direction:column;gap:10px;">
                <div v-for="ch in channels" :key="ch.n" class="lp-channel-row">
                  <div class="lp-channel-icon"><IconPlug :size="15" /></div>
                  <div style="flex:1;">
                    <div style="font-size:13px;font-weight:600;">{{ ch.n }}</div>
                    <div style="font-size:11px;color:#475569;">{{ ch.s }}</div>
                  </div>
                  <div class="lp-channel-badge">
                    <span class="lp-channel-dot"></span>Ativo
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- FEATURES -->
    <section id="features" class="lp-features">
      <div class="lp-container-md">
        <div class="lp-section-header">
          <div class="lp-eyebrow">Funcionalidades</div>
          <h2 class="lp-section-h2">
            Tudo que sua loja precisa para tomar decisão por <span style="color:#059669">dado</span>.
          </h2>
        </div>
        <div class="lp-feat-grid">
          <div v-for="f in features" :key="f.title" class="lp-card">
            <div class="lp-feat-icon" :style="{ background: f.iconBg, color: f.iconColor }">
              <component :is="f.icon" :size="20" />
            </div>
            <div class="lp-feat-title">{{ f.title }}</div>
            <div class="lp-feat-body">{{ f.body }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- QUOTE -->
    <section class="lp-quote">
      <div style="max-width:920px;margin:0 auto;text-align:center;">
        <svg width="32" height="32" viewBox="0 0 24 24"><path d="M9 11H5l3-6h2l-1 6zm10 0h-4l3-6h2l-1 6z" fill="#10b981"/></svg>
        <p class="lp-quote-text">
          "Descobri que dois dos meus combos mais vendidos estavam com margem abaixo de 30%.
          Em duas semanas reajustamos preço e a operação inteira ficou no positivo."
        </p>
        <div class="lp-quote-author">
          <div class="lp-quote-avatar">LA</div>
          <div style="text-align:left;">
            <div style="font-size:14px;font-weight:600;">Lucas Almeda</div>
            <div style="font-size:12px;color:#94a3b8;">Dono do Açaí Goat</div>
          </div>
        </div>
      </div>
    </section>

    <!-- PRICING -->
    <section id="pricing" class="lp-pricing">
      <div class="lp-container-md">
        <div class="lp-section-header">
          <div class="lp-eyebrow">Preços</div>
          <h2 class="lp-section-h2">Plano honesto, sem surpresa.</h2>
          <p class="lp-section-sub">Planos simples, sem fidelidade. Cancele quando quiser.</p>
        </div>
        <div class="lp-pricing-grid">
          <div
            v-for="p in plans"
            :key="p.name"
            class="lp-pricing-card"
            :class="{ 'lp-pricing-highlight': p.highlight }"
          >
            <div v-if="p.highlight" class="lp-pricing-popular">TUDO INCLUÍDO</div>
            <div class="lp-pricing-name">{{ p.name }}</div>
            <div class="lp-pricing-desc">{{ p.desc }}</div>
            <div class="lp-pricing-price">
              <span class="lp-pricing-amount">{{ p.price }}</span>
              <span class="lp-pricing-per">{{ p.sub }}</span>
            </div>
            <hr class="lp-pricing-divider">
            <ul class="lp-pricing-features">
              <li v-for="f in p.features" :key="f">
                <IconCheck :size="16" :color="p.highlight ? '#10b981' : '#059669'" />
                {{ f }}
              </li>
            </ul>
            <RouterLink to="/planos" class="lp-pricing-cta">{{ p.cta }}</RouterLink>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section id="faq" class="lp-faq">
      <div class="lp-container-sm">
        <div class="lp-section-header">
          <div class="lp-eyebrow">FAQ</div>
          <h2 class="lp-section-h2">Perguntas frequentes</h2>
        </div>
        <div class="lp-faq-list">
          <div
            v-for="(item, i) in faqItems"
            :key="i"
            class="lp-faq-item"
            :class="{ open: openFaq === i }"
          >
            <div class="lp-faq-question" @click="toggleFaq(i)">
              <span class="lp-faq-q-text">{{ item.q }}</span>
              <span class="lp-faq-toggle">+</span>
            </div>
            <div class="lp-faq-answer">{{ item.a }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA BAND -->
    <section class="lp-cta-section">
      <div class="lp-cta-wrap">
        <div class="lp-cta-band">
          <div class="lp-cta-glow-1"></div>
          <div class="lp-cta-glow-2"></div>
          <div style="position:relative;">
            <div class="lp-cta-eyebrow">Pronto para começar?</div>
            <h2 class="lp-cta-h2">
              A próxima ordem que sair do seu balcão pode
              <span style="color:#10b981"> mostrar quanto sobrou</span>.
            </h2>
            <p class="lp-cta-sub">Em 4 minutos sua loja está cadastrada, o cardápio importado e o lucro real visível.</p>
            <div style="display:flex;gap:12px;margin-top:32px;flex-wrap:wrap;">
              <RouterLink to="/planos" class="lp-btn lp-btn-emerald">
                Ver planos e preços <IconArrow :size="15" />
              </RouterLink>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- FOOTER -->
    <footer class="lp-footer">
      <div class="lp-footer-inner">
        <div class="lp-footer-grid">
          <div>
            <div class="lp-wordmark">jet<span>menu</span></div>
            <p class="lp-footer-tagline">Sistema de gestão para lojas de delivery. Calcule margem real, custo por ingrediente e lucro estimado em cada pedido.</p>
          </div>
          <div v-for="col in footerCols" :key="col.t">
            <div class="lp-footer-col-title">{{ col.t }}</div>
            <div class="lp-footer-links">
              <a v-for="it in col.items" :key="it" href="#">{{ it }}</a>
            </div>
          </div>
        </div>
        <div class="lp-footer-bottom">
          <div class="lp-footer-copy">© 2026 jetmenu · CNPJ 67.595.605/0001-43 · Brasil</div>
          <div class="lp-footer-copy">Feito para lojas que pensam em lucro, não só em volume.</div>
        </div>
      </div>
    </footer>

  </div>
</template>

<script setup lang="ts">
import { h, ref, type FunctionalComponent } from 'vue'

// ── Inline SVG icon components ──────────────────────────────────────────────
// Render functions instead of template strings: the CSP (script-src 'self',
// no unsafe-eval) blocks Vue's runtime template compilation in the browser.
interface IconProps { size?: number | string; color?: string }

type IconNodes = (color: string) => ReturnType<typeof h>[]

function makeIcon(
  nodes: IconNodes,
  strokeWidth = 1.6,
  svgAttrs: (color: string) => Record<string, unknown> = (color) => ({ fill: 'none', stroke: color }),
): FunctionalComponent<IconProps> {
  return (props) => {
    const color = props.color ?? 'currentColor'
    return h('svg', {
      width: props.size ?? 16, height: props.size ?? 16, viewBox: '0 0 24 24',
      'stroke-width': strokeWidth, 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
      ...svgAttrs(color),
    }, nodes(color))
  }
}

const IconArrow = makeIcon(() => [h('path', { d: 'M5 12h14M13 6l6 6-6 6' })], 2)
const IconCheck = makeIcon(() => [h('path', { d: 'M4 12l5 5L20 6' })], 2.2)
const IconChart = makeIcon(() => [
  h('path', { d: 'M3 3v18h18' }),
  h('path', { d: 'M7 14l4-4 3 3 6-6', 'stroke-width': 1.8 }),
])
const IconBell = makeIcon(() => [
  h('path', { d: 'M6 9a6 6 0 1112 0v4l2 4H4l2-4V9z' }),
  h('path', { d: 'M9 21c.5 1.2 1.6 2 3 2s2.5-.8 3-2' }),
])
const IconCalc = makeIcon((color) => [
  h('rect', { x: 4, y: 3, width: 16, height: 18, rx: 2 }),
  h('rect', { x: 7, y: 6, width: 10, height: 4, rx: 1, fill: color }),
  ...[[8.5, 13.5], [12, 13.5], [15.5, 13.5], [8.5, 17], [12, 17], [15.5, 17]].map(
    ([cx, cy]) => h('circle', { cx, cy, r: 1, fill: color }),
  ),
])
const IconLayers = makeIcon(() => [
  h('path', { d: 'M12 3l9 5-9 5-9-5 9-5z' }),
  h('path', { d: 'M3 13l9 5 9-5M3 18l9 5 9-5' }),
])
const IconSync = makeIcon(() => [
  h('path', { d: 'M4 12a8 8 0 0114-5l2 2' }),
  h('path', { d: 'M20 12a8 8 0 01-14 5l-2-2' }),
  h('path', { d: 'M16 4v4h4M8 20v-4H4' }),
])
const IconStar = makeIcon(
  () => [h('path', { d: 'M12 3l3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1 3-6z' })],
  1.6,
  (color) => ({ fill: color, stroke: color }),
)
const IconPlug = makeIcon(() => [h('path', { d: 'M9 2v6M15 2v6M7 8h10v4a5 5 0 01-10 0V8zM12 17v5' })])

// ── Data ─────────────────────────────────────────────────────────────────────
const topProducts = [
  { name: 'Açaí 400ml',   color: '#059669', margin: '74%', marginColor: '#059669' },
  { name: 'Açaí 700ml',   color: '#2563eb', margin: '68%', marginColor: '#059669' },
  { name: 'Combo Goat',   color: '#f59e0b', margin: '41%', marginColor: '#f59e0b' },
  { name: 'Tapioca Goat', color: '#e11d48', margin: '28%', marginColor: '#e11d48' },
  { name: 'Vitamina Goat',color: '#8b5cf6', margin: '61%', marginColor: '#059669' },
]

const stats = [
  { v: '63%',    l: 'margem média das lojas' },
  { v: '4 min',  l: 'para configurar uma loja' },
  { v: '8h/mês', l: 'economizadas em planilha' },
]

const pains = [
  {
    eyebrow: '01', icon: IconCalc, iconBg: 'rgba(225,29,72,.08)', iconColor: '#e11d48',
    title: 'Você sabe o preço de venda. E o custo?',
    body: 'A maioria das lojas opera no escuro: vende muito mas não sabe o quanto sobra de cada pedido. Combos baratos podem estar comendo o lucro silenciosamente.',
  },
  {
    eyebrow: '02', icon: IconLayers, iconBg: 'rgba(245,158,11,.08)', iconColor: '#f59e0b',
    title: 'Sua planilha não acompanha a operação.',
    body: 'Atualizar custos de ingredientes, recalcular margem por produto, conciliar pedidos do delivery — tudo manualmente. Quando você termina, os preços já mudaram.',
  },
  {
    eyebrow: '03', icon: IconBell, iconBg: 'rgba(37,99,235,.08)', iconColor: '#2563eb',
    title: 'Promoções podem estar matando seu lucro.',
    body: 'Aquele combo de fim de semana que "vende muito" pode estar dando prejuízo. Sem ficha técnica e margem por canal, é loteria.',
  },
]

const recipeItems = [
  { n: 'Açaí Goat',    q: '200 g', c: 'R$ 4,34' },
  { n: 'Copo + tampa', q: '1 un',  c: 'R$ 0,85' },
  { n: 'Granola',      q: '15 g',  c: 'R$ 0,45' },
]

const channels = [
  { n: 'Anota.AI', s: 'Sincronizado há 2 min' },
  { n: 'iFood',    s: 'Conectado' },
  { n: 'Balcão',   s: 'Manual' },
]

const features = [
  { icon: IconCalc,   iconBg: 'rgba(5,150,105,.1)',  iconColor: '#059669', title: 'Custo por ingrediente automático', body: 'Informe valor e quantidade da compra — o custo por unidade é calculado e gravado em cada ficha técnica.' },
  { icon: IconLayers, iconBg: 'rgba(37,99,235,.1)',   iconColor: '#2563eb', title: 'Ficha técnica detalhada', body: 'Ingredientes + embalagens + extras. Cada item entra com sua qtd. e custo, e a margem do produto sai pronta.' },
  { icon: IconSync,   iconBg: 'rgba(245,158,11,.1)',  iconColor: '#f59e0b', title: 'Importação Anota.AI / iFood', body: 'Pedidos chegam automaticamente com cliente, status e canal — sem digitação, sem planilha intermediária.' },
  { icon: IconBell,   iconBg: 'rgba(225,29,72,.1)',   iconColor: '#e11d48', title: 'Alertas de ingrediente ausente', body: 'Apareceu um item novo no pedido que não está cadastrado? O jetmenu te avisa antes de fechar o dia.' },
  { icon: IconChart,  iconBg: 'rgba(5,150,105,.1)',   iconColor: '#059669', title: 'Dashboard com lucro real', body: 'Faturamento, ticket médio, margem, peak hours, top produtos. Tudo num lugar, atualizado em tempo real.' },
  { icon: IconStar,   iconBg: 'rgba(37,99,235,.1)',   iconColor: '#2563eb', title: 'Multi-canal e multi-loja', body: 'Compare a performance entre canais e entre lojas da mesma rede. Veja qual ponto gera mais lucro, não só mais venda.' },
]

const plans = [
  {
    name: 'Básico', price: 'R$ 50', sub: '/mês', highlight: true,
    desc: 'Acesso a todas as funcionalidades.',
    features: ['Pedidos ilimitados', 'Ficha técnica ilimitada', 'Importação Anota.AI', 'Dashboard com lucro real', 'Alertas em tempo real', 'Suporte por email'],
    cta: 'Conhecer os planos',
  },
]

const faqItems = [
  { q: 'Funciona com qual plataforma de delivery?', a: 'Anota.AI tem integração nativa (sincronização automática). iFood e Aiqfome funcionam por importação de planilha (CSV) ou copy/paste estruturado. Estamos adicionando novos canais todo mês.' },
  { q: 'Preciso refazer meu cardápio do zero?', a: 'Não. Você pode importar o cardápio do Anota.AI direto, e cadastrar fichas técnicas só para os produtos que quiser monitorar custo. O dashboard funciona desde o primeiro produto.' },
  { q: 'E se eu não souber o custo dos meus ingredientes hoje?', a: 'Você cadastra os ingredientes com base na sua última compra (valor pago + quantidade). O jetmenu calcula o custo por unidade automaticamente. Não precisa de calculadora.' },
  { q: 'Quanto custa o jetmenu?', a: 'Plano único e transparente, sem fidelidade. Veja os valores atualizados e assine na página de planos.' },
  { q: 'Posso cancelar quando quiser?', a: 'Sim, sem multa e sem fidelidade. Você cancela direto pelo painel e o acesso continua até o fim do mês contratado.' },
  { q: 'Como vocês cuidam dos meus dados?', a: 'Tudo criptografado em trânsito e em repouso. Você é dono dos seus dados — pode exportar tudo em CSV a qualquer momento, e a exclusão da conta apaga o conteúdo em até 30 dias.' },
]

const footerCols = [
  { t: 'Produto',  items: ['Funcionalidades', 'Preços', 'Integrações', 'Novidades', 'Roadmap'] },
  { t: 'Empresa',  items: ['Sobre', 'Blog', 'Carreiras', 'Contato', 'Imprensa'] },
  { t: 'Recursos', items: ['Central de ajuda', 'API · documentação', 'Status', 'Material para lojistas', 'Calculadora de margem'] },
  { t: 'Legal',    items: ['Termos de uso', 'Privacidade', 'Cookies', 'LGPD', 'Segurança'] },
]

const openFaq = ref<number>(0)
function toggleFaq(i: number) {
  openFaq.value = openFaq.value === i ? -1 : i
}
</script>

<style>
/* ── Root ──────────────────────────────────────────────────── */
.lp-root {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  background: #fff;
  color: #0f172a;
  -webkit-font-smoothing: antialiased;
  font-size: 14px;
  line-height: 1.5;
}

/* ── Wordmark ───────────────────────────────────────────────── */
.lp-wordmark {
  font-size: 22px; font-weight: 700; color: #0c1626;
  letter-spacing: -0.55px; line-height: 1;
}
.lp-wordmark span { color: #10b981; }

/* ── Nav ────────────────────────────────────────────────────── */
.lp-nav {
  position: sticky; top: 0; z-index: 50;
  background: rgba(255,255,255,.85); backdrop-filter: blur(12px);
  border-bottom: 1px solid #eef0f3;
}
.lp-nav-inner {
  display: flex; align-items: center; gap: 36px;
  padding: 14px 32px; max-width: 1200px; margin: 0 auto;
}
.lp-nav-links {
  display: flex; gap: 24px; list-style: none; padding: 0; margin: 0;
}
.lp-nav-links a {
  font-size: 13.5px; font-weight: 500; color: #475569;
  text-decoration: none; transition: color .12s;
}
.lp-nav-links a:hover { color: #0f172a; }
.lp-spacer { flex: 1; }
.lp-nav-signin {
  font-size: 13.5px; font-weight: 500; color: #0f172a;
  text-decoration: none; transition: opacity .12s;
}
.lp-nav-signin:hover { opacity: .7; }

/* ── Buttons ────────────────────────────────────────────────── */
.lp-btn {
  display: inline-flex; align-items: center; gap: 8px;
  font-family: inherit; font-weight: 600; text-decoration: none;
  border-radius: 10px; cursor: pointer; border: none;
  transition: transform .18s, box-shadow .18s, filter .12s;
  white-space: nowrap;
}
.lp-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 28px rgba(15,23,42,.14); filter: brightness(1.03); }
.lp-btn:active { transform: translateY(0); }
.lp-btn-primary { background: #0c1626; color: #fff; padding: 14px 24px; font-size: 15px; }
.lp-btn-primary:hover { box-shadow: 0 14px 32px rgba(16,185,129,.3); }
.lp-btn-secondary { background: #fff; color: #0f172a; padding: 14px 22px; font-size: 15px; border: 1px solid #e8eaee; }
.lp-btn-sm { background: #0c1626; color: #fff; padding: 9px 16px; font-size: 13.5px; border-radius: 9px; }
.lp-btn-emerald { background: #10b981; color: #fff; padding: 14px 26px; font-size: 15px; font-weight: 700; }
.lp-btn-ghost-white { background: transparent; color: #fff; padding: 14px 22px; font-size: 15px; border: 1px solid rgba(255,255,255,.2); }

/* ── Cards ──────────────────────────────────────────────────── */
.lp-card {
  background: #fff; border: 1px solid #e8eaee;
  border-radius: 14px; padding: 26px;
  transition: transform .2s, box-shadow .2s, border-color .2s;
}
.lp-card:hover { transform: translateY(-3px); box-shadow: 0 14px 32px rgba(15,23,42,.08); border-color: #d4d8e0; }

/* ── Containers ─────────────────────────────────────────────── */
.lp-container-md { max-width: 1100px; margin: 0 auto; padding: 0 32px; }
.lp-container-sm { max-width: 760px;  margin: 0 auto; padding: 0 32px; }

/* ── Hero ───────────────────────────────────────────────────── */
.lp-hero {
  position: relative; padding: 72px 32px 60px; overflow: hidden;
  background:
    radial-gradient(ellipse at 70% -10%, #ecfdf5 0%, transparent 50%),
    radial-gradient(ellipse at -10% 110%, #eff6ff 0%, transparent 50%),
    #fff;
}
.lp-hero-inner { max-width: 1200px; margin: 0 auto; position: relative; }
.lp-hero-pill {
  display: inline-flex; align-items: center; gap: 10px;
  padding: 6px 14px 6px 6px; background: #fff;
  border: 1px solid #e8eaee; border-radius: 100px;
  font-size: 12.5px; color: #475569; font-weight: 500;
  box-shadow: 0 2px 6px rgba(15,23,42,.04); cursor: pointer;
  transition: opacity .12s;
}
.lp-hero-pill:hover { opacity: .8; }
.lp-hero-pill-badge {
  background: #10b981; color: #fff;
  padding: 2px 9px; border-radius: 100px;
  font-size: 11px; font-weight: 700; letter-spacing: .4px;
}
.lp-hero-h1 {
  font-size: 60px; font-weight: 700; letter-spacing: -2px;
  line-height: 1.05; color: #0f172a;
  text-align: center; max-width: 900px; margin: 0 auto;
}
.lp-accent { color: #059669; }
.lp-hero-sub {
  font-size: 18px; color: #475569; text-align: center;
  max-width: 620px; margin: 22px auto 0; line-height: 1.55;
}
.lp-hero-ctas { display: flex; gap: 12px; justify-content: center; margin-top: 32px; flex-wrap: wrap; }
.lp-hero-bullets {
  display: flex; justify-content: center; gap: 24px; margin-top: 18px;
  font-size: 12.5px; color: #94a3b8; flex-wrap: wrap;
}
.lp-hero-bullets span { display: inline-flex; align-items: center; gap: 6px; }
.lp-hero-preview { margin-top: 60px; position: relative; }

/* ── Floating chips ─────────────────────────────────────────── */
@keyframes lp-float-l { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-8px); } }
@keyframes lp-float-r { 0%,100% { transform: translateY(0); } 50% { transform: translateY(8px); } }
.lp-float-l { animation: lp-float-l 5s ease-in-out infinite; }
.lp-float-r { animation: lp-float-r 5s ease-in-out infinite; animation-delay: .4s; }
.lp-chip {
  position: absolute; z-index: 5;
  background: #fff; border-radius: 12px; padding: 12px 16px;
  border: 1px solid #e8eaee;
  box-shadow: 0 8px 24px rgba(15,23,42,.08);
  display: flex; align-items: center; gap: 10px; max-width: 220px;
}
.lp-chip-icon {
  width: 36px; height: 36px; border-radius: 9px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.lp-chip-label { font-size: 11px; color: #94a3b8; font-weight: 600; }
.lp-chip-value { font-size: 15px; font-weight: 700; color: #0f172a; font-variant-numeric: tabular-nums; }

/* ── Browser frame ──────────────────────────────────────────── */
.lp-browser-frame {
  background: #fff; border-radius: 16px; overflow: hidden;
  border: 1px solid #e8eaee;
  box-shadow: 0 30px 80px rgba(15,23,42,.12), 0 12px 30px rgba(15,23,42,.08);
  max-width: 1080px; margin: 0 auto;
}
.lp-browser-chrome {
  display: flex; align-items: center; gap: 8px;
  padding: 11px 14px; background: #fafbfc;
  border-bottom: 1px solid #e8eaee;
}
.lp-browser-dot { width: 11px; height: 11px; border-radius: 50%; display: inline-block; }
.lp-browser-url {
  margin: 0 auto; padding: 4px 14px;
  background: #fff; border: 1px solid #e8eaee; border-radius: 6px;
  font-size: 11px; color: #475569;
}

/* ── Mini Dashboard ─────────────────────────────────────────── */
.lp-mini-dash {
  display: flex; height: 594px; overflow: hidden; background: #f6f7f9;
  font-size: 10px;
}
.lp-mini-sidebar {
  width: 148px; background: #0c1626; flex-shrink: 0;
  display: flex; flex-direction: column;
}
.lp-mini-logo { padding: 15px 14px 14px; border-bottom: 1px solid #1a2638; }
.lp-mini-nav { padding: 9px 8px; flex: 1; display: flex; flex-direction: column; gap: 1px; }
.lp-mini-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 8px; border-radius: 5px;
  font-size: 8.5px; font-weight: 500; color: #cbd5e1; cursor: default;
}
.lp-mini-item.active { background: #2563eb; color: #fff; }
.lp-mini-store { padding: 8px; border-top: 1px solid #1a2638; }
.lp-mini-store-inner {
  background: #0a1322; padding: 7px 8px; border-radius: 5px;
  display: flex; align-items: center; gap: 7px;
}
.lp-mini-avatar {
  width: 20px; height: 20px; border-radius: 5px;
  background: linear-gradient(135deg,#10b981,#059669);
  font-size: 7px; font-weight: 700; color: #fff;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.lp-mini-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.lp-mini-topbar {
  background: #fff; border-bottom: 1px solid #e8eaee;
  padding: 9px 16px; display: flex; align-items: center; justify-content: space-between;
}
.lp-mini-notif {
  width: 22px; height: 22px; border-radius: 5px; background: #f6f7f9;
  border: 1px solid #e8eaee; display: flex; align-items: center; justify-content: center;
  position: relative;
}
.lp-mini-notif-badge {
  position: absolute; top: -3px; right: -3px;
  width: 9px; height: 9px; border-radius: 50%;
  background: #e11d48; border: 1.5px solid #fff;
  font-size: 5px; color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 700;
}
.lp-mini-btn {
  background: #0c1626; color: #fff; padding: 4px 8px;
  border-radius: 4px; font-size: 7px; font-weight: 600; white-space: nowrap;
}
.lp-mini-content { flex: 1; overflow: hidden; padding: 14px 16px; display: flex; flex-direction: column; gap: 12px; }
.lp-mini-kpis { display: grid; grid-template-columns: repeat(4,1fr); gap: 8px; }
.lp-mini-kpi {
  background: #fff; border: 1px solid #e8eaee; border-radius: 8px; padding: 11px;
  display: flex; flex-direction: column; gap: 8px;
}
.lp-mini-kpi-header { display: flex; align-items: center; gap: 6px; }
.lp-mini-kpi-icon {
  width: 20px; height: 20px; border-radius: 5px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.lp-mini-kpi-label { font-size: 6.5px; color: #94a3b8; font-weight: 500; }
.lp-mini-kpi-value { font-size: 13px; font-weight: 700; color: #0f172a; letter-spacing: -.5px; font-variant-numeric: tabular-nums; }
.lp-mini-kpi-change { font-size: 6px; font-weight: 600; }
.lp-mini-kpi-change.up { color: #059669; }
.lp-mini-kpi-change.down { color: #e11d48; }
.lp-mini-row { display: grid; grid-template-columns: 1.8fr 1fr; gap: 8px; flex: 1; min-height: 0; }
.lp-mini-chart-card { background: #fff; border: 1px solid #e8eaee; border-radius: 8px; padding: 11px; display: flex; flex-direction: column; }
.lp-mini-card-title { font-size: 7.5px; font-weight: 700; color: #0f172a; margin-bottom: 8px; }
.lp-mini-bars { display: flex; align-items: flex-end; gap: 4px; flex: 1; }
.lp-mini-bar { flex: 1; border-radius: 3px 3px 0 0; }
.lp-mini-sidebar-card { background: #fff; border: 1px solid #e8eaee; border-radius: 8px; padding: 11px; }
.lp-mini-product-row { display: flex; align-items: center; gap: 6px; padding: 5px 0; border-bottom: 1px solid #f1f3f5; }
.lp-mini-product-row:last-child { border-bottom: none; }
.lp-mini-product-color { width: 3px; height: 24px; border-radius: 2px; flex-shrink: 0; }
.lp-mini-product-name { flex: 1; font-size: 7px; font-weight: 600; color: #0f172a; }
.lp-mini-product-margin { font-size: 6.5px; font-weight: 700; }

/* ── Stats ──────────────────────────────────────────────────── */
.lp-stats { padding: 40px 0; background: #fff; border-bottom: 1px solid #eef0f3; }
.lp-stats-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 32px; }
.lp-stat-item { text-align: center; padding: 8px 0; }
.lp-stat-item:not(:last-child) { border-right: 1px solid #eef0f3; }
.lp-stat-value { font-size: 32px; font-weight: 700; color: #0f172a; letter-spacing: -1px; line-height: 1; font-variant-numeric: tabular-nums; }
.lp-stat-label { font-size: 13px; color: #475569; margin-top: 8px; line-height: 1.4; }

/* ── Section headers ────────────────────────────────────────── */
.lp-section-header { text-align: center; margin-bottom: 56px; }
.lp-eyebrow { font-size: 11.5px; font-weight: 700; letter-spacing: 1.4px; text-transform: uppercase; color: #059669; margin-bottom: 14px; }
.lp-section-h2 { font-size: 40px; font-weight: 700; color: #0f172a; letter-spacing: -1.2px; line-height: 1.15; margin: 0; max-width: 720px; margin-inline: auto; }
.lp-section-sub { font-size: 16px; color: #475569; margin-top: 16px; max-width: 540px; margin-inline: auto; line-height: 1.6; }

/* ── Problem ────────────────────────────────────────────────── */
.lp-problem { padding: 90px 0 60px; background: #f6f7f9; }
.lp-three-col { display: grid; grid-template-columns: repeat(3,1fr); gap: 18px; }
.lp-problem-card { display: flex; flex-direction: column; gap: 16px; border-radius: 16px; padding: 28px; }
.lp-problem-icon { width: 38px; height: 38px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.lp-problem-eyebrow { font-size: 11.5px; color: #94a3b8; font-weight: 700; letter-spacing: 1.2px; }
.lp-problem-title { font-size: 18px; font-weight: 700; color: #0f172a; letter-spacing: -.4px; line-height: 1.3; }
.lp-problem-body { font-size: 14px; color: #475569; line-height: 1.6; }

/* ── How it works ───────────────────────────────────────────── */
.lp-how { padding: 90px 0; background: #fff; }
.lp-how-steps { display: flex; flex-direction: column; gap: 24px; }
.lp-how-step {
  display: grid; grid-template-columns: 1fr 1.2fr; gap: 40px; align-items: center;
  padding: 32px; background: #f6f7f9; border-radius: 18px;
}
.lp-how-step--flip { grid-template-columns: 1.2fr 1fr; }
.lp-how-step--flip .lp-how-visual { order: -1; }
.lp-step-badge {
  display: inline-flex; align-items: center;
  background: #0c1626; color: #fff;
  padding: 6px 14px; border-radius: 100px;
  font-size: 12px; font-weight: 700; letter-spacing: .4px;
  margin-bottom: 18px;
}
.lp-step-title { font-size: 26px; font-weight: 700; color: #0f172a; letter-spacing: -.6px; line-height: 1.2; }
.lp-step-body { font-size: 15px; color: #475569; margin-top: 12px; line-height: 1.6; max-width: 440px; }
.lp-how-visual { background: #fff; border: 1px solid #e8eaee; border-radius: 14px; padding: 20px; box-shadow: 0 8px 24px rgba(15,23,42,.06); }
.lp-visual-label { font-size: 11px; color: #94a3b8; font-weight: 600; letter-spacing: .5px; text-transform: uppercase; margin-bottom: 14px; }
.lp-field-label { font-size: 10.5px; color: #94a3b8; margin-bottom: 4px; }
.lp-field-value { padding: 8px 10px; background: #f6f7f9; border-radius: 7px; font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }
.lp-result-box { padding: 12px 14px; background: #ecfdf5; border: 1px solid rgba(16,185,129,.3); border-radius: 9px; display: flex; align-items: center; gap: 10px; }
.lp-recipe-row { display: grid; grid-template-columns: 1fr 60px 70px; gap: 8px; padding: 6px 0; font-size: 12px; }
.lp-channel-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: #f6f7f9; border-radius: 9px; }
.lp-channel-icon { width: 32px; height: 32px; border-radius: 8px; background: #ecfdf5; color: #059669; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.lp-channel-badge { padding: 3px 9px; background: #ecfdf5; color: #059669; border-radius: 5px; font-size: 11px; font-weight: 700; display: flex; align-items: center; gap: 5px; }
.lp-channel-dot { width: 6px; height: 6px; border-radius: 50%; background: #10b981; display: inline-block; }

/* ── Features ───────────────────────────────────────────────── */
.lp-features { padding: 90px 0; background: #f6f7f9; }
.lp-feat-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 14px; }
.lp-feat-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; margin-bottom: 16px; }
.lp-feat-title { font-size: 16px; font-weight: 700; color: #0f172a; letter-spacing: -.3px; line-height: 1.3; margin-bottom: 8px; }
.lp-feat-body { font-size: 13.5px; color: #475569; line-height: 1.6; }

/* ── Quote ──────────────────────────────────────────────────── */
.lp-quote { padding: 80px 32px; background: #0c1626; color: #fff; }
.lp-quote-text { font-size: 28px; font-weight: 500; line-height: 1.4; letter-spacing: -.6px; margin: 20px 0 28px; color: #fff; }
.lp-quote-author { display: flex; align-items: center; justify-content: center; gap: 14px; }
.lp-quote-avatar {
  width: 44px; height: 44px; border-radius: 50%;
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff; font-size: 16px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}

/* ── Pricing ────────────────────────────────────────────────── */
.lp-pricing { padding: 90px 0; background: #fff; }
.lp-pricing-grid { display: grid; grid-template-columns: minmax(0, 380px); gap: 16px; align-items: stretch; justify-content: center; }
.lp-pricing-card {
  border-radius: 16px; padding: 32px; display: flex; flex-direction: column;
  position: relative; background: #fff; border: 1px solid #e8eaee;
  transition: transform .2s, box-shadow .2s;
}
.lp-pricing-highlight {
  background: #0c1626; color: #fff;
  border-color: #0c1626;
  box-shadow: 0 20px 40px rgba(12,22,38,.2);
  transform: scale(1.02);
}
.lp-pricing-popular {
  position: absolute; top: -12px; left: 50%; transform: translateX(-50%);
  background: #10b981; color: #fff;
  padding: 5px 12px; border-radius: 100px;
  font-size: 11px; font-weight: 700; letter-spacing: .4px; white-space: nowrap;
}
.lp-pricing-name { font-size: 15px; font-weight: 700; margin-bottom: 6px; }
.lp-pricing-desc { font-size: 13px; color: #475569; margin-bottom: 22px; }
.lp-pricing-highlight .lp-pricing-desc { color: #94a3b8; }
.lp-pricing-price { display: flex; align-items: baseline; gap: 6px; margin-bottom: 22px; padding-bottom: 22px; border-bottom: 1px solid #eef0f3; }
.lp-pricing-highlight .lp-pricing-price { border-color: rgba(255,255,255,.1); }
.lp-pricing-amount { font-size: 42px; font-weight: 800; letter-spacing: -1.5px; font-variant-numeric: tabular-nums; line-height: 1; }
.lp-pricing-per { font-size: 13px; color: #475569; }
.lp-pricing-highlight .lp-pricing-per { color: #94a3b8; }
.lp-pricing-divider { display: none; }
.lp-pricing-features { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; flex: 1; margin-bottom: 28px; }
.lp-pricing-features li { display: flex; align-items: flex-start; gap: 10px; font-size: 13.5px; line-height: 1.4; }
.lp-pricing-cta {
  padding: 12px 18px; border-radius: 10px;
  font-size: 14px; font-weight: 600; text-align: center;
  text-decoration: none; display: block;
  transition: transform .18s, box-shadow .18s;
  background: transparent; color: #0f172a; border: 1px solid #e8eaee;
}
.lp-pricing-highlight .lp-pricing-cta { background: #10b981; color: #fff; border: none; }
.lp-pricing-cta:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(15,23,42,.12); }

/* ── FAQ ────────────────────────────────────────────────────── */
.lp-faq { padding: 90px 0; background: #f6f7f9; }
.lp-faq .lp-section-h2 { font-size: 36px; letter-spacing: -1px; }
.lp-faq-list { background: #fff; border: 1px solid #e8eaee; border-radius: 14px; overflow: hidden; }
.lp-faq-item { border-bottom: 1px solid #eef0f3; }
.lp-faq-item:last-child { border-bottom: none; }
.lp-faq-question {
  padding: 18px 22px; display: flex; align-items: center;
  justify-content: space-between; cursor: pointer; gap: 12px;
  transition: background .15s;
}
.lp-faq-question:hover { background: #f6f7f9; }
.lp-faq-q-text { font-size: 15px; font-weight: 600; color: #0f172a; }
.lp-faq-toggle {
  width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
  background: #f6f7f9; color: #0f172a;
  display: flex; align-items: center; justify-content: center;
  transition: transform .2s; font-size: 18px; line-height: 1;
}
.lp-faq-item.open .lp-faq-toggle { transform: rotate(45deg); }
.lp-faq-answer { display: none; padding: 0 22px 20px; font-size: 14px; color: #475569; line-height: 1.6; }
.lp-faq-item.open .lp-faq-answer { display: block; }

/* ── CTA Band ───────────────────────────────────────────────── */
.lp-cta-section { padding: 90px 0; background: #fff; }
.lp-cta-wrap { padding: 0 32px; }
.lp-cta-band {
  background: linear-gradient(135deg, #0c1626 0%, #0a1322 100%);
  border-radius: 24px; padding: 60px 56px;
  color: #fff; position: relative; overflow: hidden;
  max-width: 1000px; margin: 0 auto;
}
.lp-cta-glow-1 {
  position: absolute; top: -100px; right: -50px;
  width: 380px; height: 380px; border-radius: 50%;
  background: radial-gradient(circle, rgba(16,185,129,.3), transparent 70%);
  pointer-events: none;
}
.lp-cta-glow-2 {
  position: absolute; bottom: -80px; left: -60px;
  width: 320px; height: 320px; border-radius: 50%;
  background: radial-gradient(circle, rgba(37,99,235,.25), transparent 70%);
  pointer-events: none;
}
.lp-cta-eyebrow { font-size: 11.5px; color: #10b981; font-weight: 700; letter-spacing: 1.4px; text-transform: uppercase; margin-bottom: 14px; }
.lp-cta-h2 { font-size: 42px; font-weight: 700; letter-spacing: -1.2px; line-height: 1.1; max-width: 600px; margin: 0; }
.lp-cta-sub { font-size: 16px; color: #94a3b8; margin-top: 18px; max-width: 480px; line-height: 1.6; }

/* ── Footer ─────────────────────────────────────────────────── */
.lp-footer { background: #fff; border-top: 1px solid #e8eaee; }
.lp-footer-inner { max-width: 1200px; margin: 0 auto; padding: 60px 32px 32px; }
.lp-footer-grid { display: grid; grid-template-columns: 1.4fr repeat(4,1fr); gap: 32px; }
.lp-footer-tagline { font-size: 13px; color: #475569; margin-top: 14px; max-width: 260px; line-height: 1.6; }
.lp-footer-col-title { font-size: 11.5px; color: #0f172a; font-weight: 700; letter-spacing: .5px; text-transform: uppercase; margin-bottom: 14px; }
.lp-footer-links { display: flex; flex-direction: column; gap: 10px; }
.lp-footer-links a { font-size: 13px; color: #475569; text-decoration: none; transition: color .12s; }
.lp-footer-links a:hover { color: #0f172a; }
.lp-footer-bottom { margin-top: 48px; padding-top: 24px; border-top: 1px solid #eef0f3; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.lp-footer-copy { font-size: 12px; color: #94a3b8; }

/* ── Scroll reveal ──────────────────────────────────────────── */
@keyframes lp-fadeup { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: none; } }
.lp-root section { animation: lp-fadeup .6s ease both; }

/* ── Responsive ─────────────────────────────────────────────── */
@media (max-width: 1024px) {
  .lp-hero-h1 { font-size: 48px; letter-spacing: -1.4px; }
  .lp-section-h2 { font-size: 34px; }
  .lp-three-col, .lp-feat-grid { grid-template-columns: repeat(2, 1fr); }
  .lp-footer-grid { grid-template-columns: repeat(3, 1fr); }
  .lp-mini-row { grid-template-columns: 1.4fr 1fr; }
}

@media (max-width: 860px) {
  .lp-nav-links { display: none; }
  .lp-nav-inner { gap: 16px; }
  .lp-how-step, .lp-how-step--flip { grid-template-columns: 1fr; gap: 24px; }
  .lp-how-step--flip .lp-how-visual { order: 0; }
  .lp-step-body { max-width: none; }
  .lp-chip { display: none; }
  .lp-mini-sidebar { display: none; }
  .lp-mini-dash { height: 480px; }
  .lp-quote-text { font-size: 22px; }
  .lp-cta-h2 { font-size: 32px; }
}

@media (max-width: 640px) {
  .lp-hero { padding: 48px 20px 40px; }
  .lp-hero-h1 { font-size: 34px; letter-spacing: -1px; }
  .lp-hero-h1 br { display: none; }
  .lp-hero-sub { font-size: 16px; }
  .lp-hero-ctas { flex-direction: column; align-items: stretch; }
  .lp-hero-ctas .lp-btn { justify-content: center; }
  .lp-hero-bullets { gap: 10px 16px; }
  .lp-hero-preview { margin-top: 40px; }
  .lp-container-md, .lp-container-sm { padding: 0 20px; }
  .lp-section-header { margin-bottom: 36px; }
  .lp-section-h2, .lp-faq .lp-section-h2 { font-size: 28px; letter-spacing: -.8px; }
  .lp-stats-grid { grid-template-columns: 1fr; gap: 20px; }
  .lp-stat-item:not(:last-child) { border-right: none; border-bottom: 1px solid #eef0f3; padding-bottom: 20px; }
  .lp-three-col, .lp-feat-grid { grid-template-columns: 1fr; }
  .lp-problem { padding: 56px 0 40px; }
  .lp-how, .lp-features, .lp-pricing, .lp-faq, .lp-cta-section { padding: 56px 0; }
  .lp-how-step { padding: 20px; }
  .lp-mini-dash { height: 420px; }
  .lp-mini-kpis { grid-template-columns: repeat(2, 1fr); }
  .lp-mini-row { grid-template-columns: 1fr; }
  .lp-mini-sidebar-card { display: none; }
  .lp-quote { padding: 56px 20px; }
  .lp-cta-wrap { padding: 0 16px; }
  .lp-cta-band { padding: 40px 24px; border-radius: 18px; }
  .lp-cta-h2 { font-size: 28px; }
  .lp-cta-sub { font-size: 15px; }
  .lp-pricing-card { padding: 24px; }
  .lp-pricing-amount { font-size: 36px; }
  .lp-footer-grid { grid-template-columns: 1fr 1fr; }
  .lp-footer-inner { padding: 40px 20px 24px; }
  .lp-footer-bottom { flex-direction: column; align-items: flex-start; }
}
</style>
