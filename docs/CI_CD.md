# Pipeline CI/CD — MediStock (Smart HAS)

Pipeline de integração e entrega contínua em **GitHub Actions**, definido em
[.github/workflows/ci-cd.yml](../.github/workflows/ci-cd.yml).

## Gatilhos

| Evento | O que executa |
| --- | --- |
| `push` na branch `main` | Build, testes, integração Oracle, deploy e relatório |
| `pull_request` para `main` | Build, testes e integração Oracle (sem deploy) |
| Execução manual (`workflow_dispatch`) | Tudo, com as opções **carga inicial** (tabelas e dados em schema novo) e **migração de auditoria** (criação única da `AUDITORIA_CONSUMO`) |

## Jobs

### 1. `build` — Build e testes automatizados
JDK 25 (Temurin) com cache do Maven, `./mvnw verify` sobre a suíte de testes
(SQLite em memória, perfil `test`). Publica o `.jar` como artefato e o log
completo do build junto dos relatórios do Surefire.

### 2. `oracle-it` — Integração com Oracle
Sobe um **Oracle Database Free** real como *service container*
(`gvenzl/oracle-free`, serviço `FREEPDB1`, usuário `MEDISTOCK`) e aplica os
scripts de [database/oracle](../database/oracle) com o Oracle SQLcl, nesta ordem:

| Etapa | Conexão | Scripts |
| --- | --- | --- |
| Permissão da trigger | `SYSTEM` | `06_permissoes_trigger` |
| Modelo e carga | `MEDISTOCK` | `01_criar_tabelas`, `02_dados_simulados` |
| Procedure e Parte 3 | `MEDISTOCK` | `03_procedure_consumo`, `06_parte3_plsql` |
| Consolidação | `MEDISTOCK` | `07_criar_auditoria`, `08_criar_package`, `09_adaptar_procedure`, `10_criar_trigger` |
| Validações | `MEDISTOCK` | `04_validar_banco`, `11_validar_objetos` |
| Testes e exemplos | `MEDISTOCK` | `05_testar_procedure`, `07_parte3_exemplos`, `12_testar_consolidacao`, `13_demo_sql_dinamico` |

Cada script traz `WHENEVER SQLERROR EXIT FAILURE` e as próprias verificações:
`11` exige os seis objetos da consolidação válidos com a trigger habilitada e
`12` roda as verificações do package desfazendo os dados de teste por
`ROLLBACK TO SAVEPOINT`. Em seguida o job roda a suíte Java novamente com as
variáveis `ORACLE_*` preenchidas — o que habilita o teste
`HistoricoConsumoOracleRealDbTest`, anotado com
`@EnabledIfEnvironmentVariable(named = "ORACLE_PASSWORD")`.

Isso valida, a cada commit, que as tabelas, a procedure `PR_REGISTRAR_CONSUMO`,
os objetos PL/SQL da Parte 3 e a consolidação em package, auditoria e trigger
compilam e funcionam contra um banco de verdade.

### 3. `deploy` — Back-end + Oracle
1. Aplica os objetos PL/SQL no **banco Oracle alvo** com o Oracle SQLcl: os
   recriáveis a cada execução (`03_procedure_consumo`, `06_parte3_plsql`,
   `08_criar_package`, `09_adaptar_procedure`, `10_criar_trigger`) e as
   validações `04_validar_banco` e `11_validar_objetos`. `01_criar_tabelas` e
   `02_dados_simulados` só rodam com a opção **carga inicial**, e
   `07_criar_auditoria` só com a opção **migração de auditoria** — os três
   falham de propósito em um schema que já os recebeu.
2. Constrói a imagem a partir do [Dockerfile](../Dockerfile) e a publica no
   **GitHub Container Registry**, com as tags `:<sha>` e `:latest`.
3. Opcionalmente faz o *rollout* por SSH no servidor, substituindo o container
   em execução pela nova imagem.
4. Executa um **smoke test** HTTP contra a API publicada.

O job usa o *environment* `producao`, o que permite exigir aprovação manual
antes do deploy nas configurações do repositório.

### 4. `relatorio` — Logs e relatórios de execução
Roda sempre (`if: always()`), baixa todos os artefatos e gera o
`relatorio-execucao.md` com a situação de cada job, o resumo dos testes e a
lista de logs publicados. O mesmo conteúdo aparece no resumo da execução, na
aba **Actions**.

## Artefatos gerados a cada execução

| Artefato | Conteúdo |
| --- | --- |
| `medistock-jar` | `.jar` executável da aplicação |
| `relatorios-build` | `logs/build.log`, `logs/relatorio-testes.md`, relatórios XML do Surefire |
| `relatorios-oracle` | `logs/integracao-oracle.log`, `logs/banco/*.log` (saída de cada script SQL), relatórios do Surefire |
| `relatorios-deploy` | `logs/banco/*.log` do banco alvo, `logs/rollout.log`, `logs/smoke-test.log` |
| `relatorio-execucao` | Relatório consolidado em Markdown |

## Pré-requisitos no banco alvo

O usuário do deploy precisa de `CREATE TRIGGER` para que `10_criar_trigger`
compile. A concessão é única e exige `SYSTEM`, fora do alcance do pipeline:

```sql
-- conectado como SYSTEM / FREEPDB1
GRANT CREATE TRIGGER TO MEDISTOCK;
```

Em um schema novo, a primeira execução manual deve marcar **carga inicial** e
**migração de auditoria** juntas; nas execuções seguintes, as duas opções ficam
desmarcadas.

## Secrets do repositório

Cadastrar em **Settings → Secrets and variables → Actions → Secrets**.

| Secret | Obrigatório | Exemplo / observação |
| --- | --- | --- |
| `ORACLE_URL` | Sim | `jdbc:oracle:thin:@//host:1521/FREEPDB1` — Oracle alvo do deploy |
| `ORACLE_USER` | Sim | `MEDISTOCK` |
| `ORACLE_PASSWORD` | Sim | Senha do usuário `MEDISTOCK` |
| `JWT_SECRET` | Sim | Chave Base64 usada pela API em produção |
| `GEMINI_API_KEY` | Não | Deixe vazia para desligar a integração de IA |
| `DEPLOY_SSH_HOST` | Só com rollout SSH | IP ou domínio do servidor |
| `DEPLOY_SSH_USER` | Só com rollout SSH | Usuário com acesso ao Docker no servidor |
| `DEPLOY_SSH_KEY` | Só com rollout SSH | Chave privada OpenSSH completa, incluindo as linhas `BEGIN`/`END` |
| `DEPLOY_SSH_PORT` | Não | Padrão `22` |

`GITHUB_TOKEN` é fornecido automaticamente pelo GitHub e é o que autentica a
publicação da imagem no GHCR — não precisa ser cadastrado.

## Variables do repositório

Cadastrar em **Settings → Secrets and variables → Actions → Variables**.

| Variable | Para que serve |
| --- | --- |
| `DEPLOY_SSH_ENABLED` | `true` liga o rollout por SSH; qualquer outro valor (ou ausente) pula a etapa |
| `DEPLOY_HEALTHCHECK_URL` | URL verificada pelo smoke test, ex.: `http://meu-servidor:8080/v3/api-docs`. Sem ela, o smoke test é pulado |

## Executando sem servidor de deploy

Sem `DEPLOY_SSH_ENABLED` e sem `DEPLOY_HEALTHCHECK_URL`, o pipeline continua
completo e demonstrável: build, testes, provisionamento e testes contra um
Oracle real, aplicação dos objetos PL/SQL no banco alvo, publicação da imagem
versionada no GHCR e relatório consolidado.
