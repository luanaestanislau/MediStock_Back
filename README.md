# MediStock — Back-end

API REST de gestão de estoque hospitalar: hospitais, insumos, consumo, entregas e transferências entre unidades, com alertas e sugestões de redistribuição por IA (Gemini).

**Stack:** Java 25 · Spring Boot 4.1 · Spring Security + JWT · JPA/Hibernate · Oracle Database Free (PL/SQL) · SQLite (modo simples) · Swagger

## Executar

### 1. Configurar o `.env`

Copie `.env.example` para `.env` na raiz do projeto (ao lado do `pom.xml`). Valores sem aspas.

```properties
JWT_SECRET=chave_base64_de_32_bytes
JWT_EXPIRACAO_MINUTOS=120
GEMINI_API_KEY=                # opcional
ORACLE_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
ORACLE_USER=MEDISTOCK
ORACLE_PASSWORD=sua_senha
```

Gerar o `JWT_SECRET`: `openssl rand -base64 32`

> O `.env` não deve ser versionado. Nunca publique suas chaves.

### 2. Escolher o banco

**Opção A — SQLite (mais rápido, sem instalar nada)**

```bash
./mvnw spring-boot:run
```

Usa o arquivo `medistock.db`. As tabelas são criadas automaticamente.

**Opção B — Oracle (perfil `oracle`)**

Suba o Oracle com Docker (Mac, Linux ou Windows):

```bash
docker run -d --name oracle-medistock -p 1521:1521 \
  -e ORACLE_PASSWORD=SenhaAdmin123 \
  -v oracle-medistock-data:/opt/oracle/oradata \
  gvenzl/oracle-free:slim-faststart
```

Espere aparecer `DATABASE IS READY TO USE!` em `docker logs oracle-medistock`. Depois crie o usuário e as tabelas, na pasta do projeto:

```bash
# 1. Edite database/oracle/00_criar_usuario.sql e defina a senha do MEDISTOCK
docker cp database/oracle oracle-medistock:/tmp/oracle

# 2. Usuário (como SYSTEM)
docker exec -i oracle-medistock sqlplus -s system/SenhaAdmin123@//localhost:1521/FREEPDB1 @/tmp/oracle/00_criar_usuario.sql

# 3. Tabelas, dados e procedure (como MEDISTOCK)
for f in 01_criar_tabelas 02_dados_simulados 03_procedure_consumo 04_validar_banco; do
  docker exec -i oracle-medistock sqlplus -s MEDISTOCK/SUA_SENHA@//localhost:1521/FREEPDB1 @/tmp/oracle/$f.sql
done
```

Inicie a API:

```bash
./mvnw -Dspring-boot.run.profiles=oracle spring-boot:run
```

No Windows, use `.\mvnw.cmd` no lugar de `./mvnw`. Nas próximas vezes basta `docker start oracle-medistock`.

### 3. Acessar

| O quê | Endereço |
| --- | --- |
| API | http://localhost:8080/api |
| Swagger | http://localhost:8080/docs |

Para o app no celular, use o IP do computador (`http://SEU_IP:8080/api`), com os dois na mesma rede Wi-Fi e a porta 8080 liberada no firewall.

## Autenticação

Os endpoints, exceto `/api/auth/**` e o Swagger, exigem o cabeçalho `Authorization: Bearer <token>`.

1. **Cadastro:** `POST /api/auth/registrar`
2. **Login:** `POST /api/auth/login`

```json
{
  "primeiroNome": "Ana",
  "ultimoNome": "Demo",
  "emailInstitucional": "ana.demo@fiap.com.br",
  "senha": "SenhaDemo2026!"
}
```

- O e-mail deve ser de um domínio permitido: `fiap.com.br`, `hc.unicamp.br`, `hc.usp.br`, `einstein.br`, `hospital.gov.br`, `saude.sp.gov.br`.
- A senha deve ter no mínimo 8 caracteres.
- No Swagger, clique em **Authorize** e cole apenas o token.

## Endpoints

| Rota base | Função |
| --- | --- |
| `/api/auth` | Cadastro e login |
| `/api/perfil` | Dados do usuário logado |
| `/api/hospitais` | CRUD de hospitais |
| `/api/estoque` | CRUD de itens e resumo do estoque |
| `/api/alertas` | Resumo de alertas (validade, nível baixo) |
| `/api/historico-consumo` | Registro de consumo (usa a procedure Oracle) |
| `/api/logistica` | Entregas, transferências e mapa |
| `/api/ia` | Análise interna e sugestões de redistribuição |

A lista completa e os formatos de requisição estão no Swagger.

## Banco de dados

Seis tabelas: `USUARIOS`, `HOSPITAIS`, `ITENS_ESTOQUE`, `HISTORICO_CONSUMO`, `ENTREGAS` e `TRANSFERENCIAS`, com chaves estrangeiras e índices. Os scripts ficam em [`database/oracle`](database/oracle):

| Script | Função |
| --- | --- |
| `00_criar_usuario.sql` | Cria o usuário `MEDISTOCK` (rodar como `SYSTEM`) |
| `01_criar_tabelas.sql` | Tabelas, restrições e índices |
| `02_dados_simulados.sql` | Carga de dados de demonstração |
| `03_procedure_consumo.sql` | Procedure `PR_REGISTRAR_CONSUMO` |
| `04_validar_banco.sql` | Conferência de contagens e objetos |
| `05_testar_procedure.sql` | Teste da procedure (com rollback) |

Documentação: [DER](docs/DER_MediStock.png) · [Dicionário de dados](docs/MODELO_E_DICIONARIO.md)

### Procedure `PR_REGISTRAR_CONSUMO`

Valida item, hospital, data e quantidade (erros `-20001` a `-20005`) e insere em `HISTORICO_CONSUMO`. A API a chama por JDBC ao receber `POST /api/historico-consumo`:

```json
{
  "itemEstoqueId": 1,
  "hospitalId": 1,
  "mesReferencia": "2026-09",
  "quantidadeConsumida": 7
}
```

Resposta de sucesso: **HTTP 201**, sem corpo. Cada chamada válida cria um novo registro.

## Testes

```bash
./mvnw test
```

Para validar o Oracle, rode `04_validar_banco.sql` e `05_testar_procedure.sql`. As capturas de tela estão em [`docs/evidencias`](docs/evidencias).

## Problemas comuns

| Erro | Causa provável |
| --- | --- |
| `ORA-01017` | Usuário `MEDISTOCK` não existe ou senha diferente da do `.env` |
| `ORA-12541` / `ORA-12514` | Oracle parado ou `FREEPDB1` fechado — `docker start oracle-medistock` |
| `missing table` ao iniciar | Scripts 01 a 03 ainda não foram executados |
| App não conecta | IP mudou, firewall bloqueando a 8080 ou celular em outra rede |