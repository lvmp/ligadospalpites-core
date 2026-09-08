---
name: ingestao_esportiva_polimorfica
description: Diretrizes para ingestão de dados esportivos (futebol e outros esportes), controle de janela ativa de partidas e formatação de fases/rodadas.
---

# ⚽ Ingestão Esportiva Polimórfica e Janelas de Execução

Esta skill define os padrões para consumo de APIs esportivas externas, controle de execuções periódocas vs force/agenda, e abstração de diferentes modalidades esportivas.

---

## ⏱️ 1. Tipos de Sincronização e Frequência

O backend opera com dois fluxos distintos de sincronização:

1. **Sincronização Periódica de Tempo Real (`force=false`)**:
   - Executada em intervalos curtos (ex: a cada 2 minutos).
   - Processa apenas partidas dentro da **janela ativa** (jogos ao vivo, iniciados recentemente ou prestes a começar).
   - **Não** envia a notificação da agenda do dia.
2. **Sincronização de Carga / Forçada (`force=true`)**:
   - Executada em horários específicos ou via rotina diária/manual.
   - Sincroniza a grade completa do dia/rodada.
   - É o gatilho apropriado para o envio de push notifications de **Agenda do Dia** (quando houver jogos no dia).

---

## 🏆 2. Formatação de Fases e Estrutura Polimórfica

- **Polimorfismo de Esportes**: A arquitetura de domínio deve abstrair entidades de partidas (`Match`), times (`Team`) e competições (`Competition`) permitindo fácil extensão para novos esportes.
- **Normalização de Fases/Rodadas**:
  - Dados externos (ex: APIs de Futebol, Basquete) trazem nomenclaturas variadas de fases (ex: "Regular Season", "Quarter-finals", "Rodada 34").
  - O sistema deve normalizar esses valores em um modelo consistente para exibição amigável no app mobile.

---

## 🛡️ 3. Resiliência e Rate Limiting

- Utilizar Resilience4j para proteger as chamadas HTTP a provedores de dados esportivos.
- Implementar cache com Redis para evitar chamadas redundantes a dados que mudam com baixa frequência (ex: tabela de classificação, informações de estádio/equipes).
