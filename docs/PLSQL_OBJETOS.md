# Documentação dos objetos PL/SQL

## 1. Escopo e organização

A implementação consolida o domínio de estoque hospitalar do MediStock. Usa as tabelas existentes `HOSPITAIS`, `ITENS_ESTOQUE` e `HISTORICO_CONSUMO`, acrescentando `AUDITORIA_CONSUMO`. As demais tabelas da primeira etapa permanecem no modelo.

`PKG_MEDISTOCK` usa `AUTHID DEFINER` e é criado no schema `MEDISTOCK`. Sua especificação é a interface pública; seu corpo concentra a implementação e as validações privadas. Não há estado global mutável no package.

O privilégio `CREATE PROCEDURE` da etapa anterior permite criar o package e a procedure adaptadora. O script `06` acrescenta `CREATE TRIGGER` para a criação da trigger no próprio schema.

## 2. Procedure pública de registro

```sql
PKG_MEDISTOCK.PR_REGISTRAR_CONSUMO(
    p_item_estoque_id       IN NUMBER,
    p_hospital_id           IN NUMBER,
    p_mes_referencia       IN DATE,
    p_quantidade_consumida IN NUMBER,
    p_historico_id         OUT NUMBER
)
```

| Parâmetro | Finalidade |
|---|---|
| `p_item_estoque_id` | ID inteiro positivo de um item existente. |
| `p_hospital_id` | ID inteiro positivo de um hospital existente. |
| `p_mes_referencia` | Data de referência obrigatória; preservada como recebida. |
| `p_quantidade_consumida` | Inteiro de zero a `2147483647`, compatível com o `Integer` usado no Java. |
| `p_historico_id` | ID gerado pelo Oracle, obtido com `INSERT ... RETURNING ID INTO`. |

A procedure verifica IDs, quantidade, data e existência das referências. Insere exatamente um registro por chamada válida. Vários registros do mesmo item, hospital e mês são permitidos, conforme o modelo anterior.

Não exige que o hospital do histórico seja o atual hospital de armazenamento do item, preservando a possibilidade de representar demanda em outros hospitais. Não diminui o saldo de estoque e não confirma a transação internamente.

**Valor agregado:** mantém as regras de consumo em um ponto central e permite que novos consumidores obtenham o ID inserido.

## 3. Function pública de totalização

```sql
PKG_MEDISTOCK.FN_TOTAL_CONSUMO(
    p_item_estoque_id IN NUMBER,
    p_hospital_id    IN NUMBER,
    p_data_inicio    IN DATE DEFAULT NULL,
    p_data_fim       IN DATE DEFAULT NULL
) RETURN NUMBER
```

Retorna a soma de `QUANTIDADE_CONSUMIDA` para o item e hospital informados. Item e hospital precisam existir. Sem datas, considera todo o histórico; um limite nulo deixa aquele lado do intervalo aberto. Os limites são dias inclusivos: a comparação ignora a hora com `TRUNC`. Retorna `0` quando não há consumo no intervalo e rejeita a data final anterior à inicial.

Exemplo para um par de IDs existentes — troque `1` pelos valores do seu banco:

```sql
SELECT PKG_MEDISTOCK.FN_TOTAL_CONSUMO(
    1, 1, DATE '2026-10-01', DATE '2026-10-31'
) AS TOTAL_CONSUMIDO
FROM DUAL;
```

**Valor agregado:** permite reutilizar o cálculo em relatórios SQL e em outras rotinas, evitando reescrever a soma e suas regras de período. O resultado usa `NUMBER`, pois a soma pode ultrapassar o limite de um registro individual.

## 4. Procedure pública de SQL dinâmico

```sql
PKG_MEDISTOCK.PR_CONSULTAR_ESTOQUE(
    p_resultado   OUT SYS_REFCURSOR,
    p_hospital_id IN NUMBER DEFAULT NULL,
    p_nome        IN VARCHAR2 DEFAULT NULL,
    p_nivel       IN VARCHAR2 DEFAULT NULL,
    p_ordenar_por IN VARCHAR2 DEFAULT 'NOME',
    p_direcao     IN VARCHAR2 DEFAULT 'ASC'
)
```

| Entrada | Regra |
|---|---|
| Hospital | Opcional; inteiro positivo. Um ID inexistente retorna zero linhas. |
| Nome | Trecho literal do nome, sem distinguir maiúsculas/minúsculas; até 150 caracteres após `TRIM`. `%` e `_` não funcionam como curingas, pois a busca usa `INSTR`. |
| Nível | Opcional: `CRITICO`, `ATENCAO` ou `NORMAL`. Espaços externos e caixa são normalizados. |
| Ordenar por | `ID`, `NOME`, `QUANTIDADE_ATUAL` ou `VALIDADE`. Nulo ou vazio usa `NOME`. |
| Direção | `ASC` ou `DESC`. Nulo ou vazio usa `ASC`. |

O cursor retorna, nesta ordem:

| Posição | Coluna | Tipo de origem |
|---|---|---|
| 1 | `ITEM_ESTOQUE_ID` | `NUMBER(19,0)` |
| 2 | `NOME` | `VARCHAR2(150 CHAR)` |
| 3 | `HOSPITAL_ID` | `NUMBER(19,0)` |
| 4 | `HOSPITAL_NOME` | `VARCHAR2(150 CHAR)` |
| 5 | `QUANTIDADE_ATUAL` | `NUMBER(10,0)` |
| 6 | `QUANTIDADE_MINIMA` | `NUMBER(10,0)` |
| 7 | `NIVEL_ESTOQUE` | Expressão textual: `CRITICO`, `ATENCAO` ou `NORMAL`. |
| 8 | `VALIDADE` | `DATE`, podendo ser nula. |

Validades nulas ficam por último. Empates na coluna escolhida são resolvidos por `ID ASC`, exceto quando a própria coluna escolhida já é o ID.

### Como a construção dinâmica funciona

O texto inicial usa uma CTE `parametros` com três placeholders fixos. Cada valor aparece uma única vez na declaração dessa CTE. Isso mantém três binds em qualquer combinação de filtros:

```sql
OPEN p_resultado FOR v_sql USING p_hospital_id, v_nome, v_nivel;
```

Os predicados de hospital, nome e nível são adicionados apenas quando o filtro correspondente foi informado. O `ORDER BY` também muda conforme os parâmetros. Os valores não são concatenados ao SQL; os identificadores usados na ordenação são selecionados por `CASE` a partir de constantes permitidas.

Um `SELECT` dinâmico que devolve várias linhas pode ser executado com `OPEN FOR`, seguido de `FETCH` e `CLOSE`. Portanto, o uso de Dynamic SQL não depende de escrever `EXECUTE IMMEDIATE`. O script `13` demonstra as três etapas.

**Valor agregado:** atende consultas operacionais diferentes com uma interface única, usando os dados do estoque disponíveis em cada chamada. O chamador deve consumir e fechar o cursor. Não há cache de resultados, streaming ou novo endpoint Java nesta etapa.

## 5. Validações privadas do package

| Rotina | Parâmetros | Finalidade e reuso |
|---|---|---|
| `PR_VALIDAR_IDS` | Item e hospital, ambos `NUMBER` | Confere preenchimento, valor positivo e ausência de fração. Usada pelo registro e pela totalização. |
| `PR_VALIDAR_REFERENCIAS` | Item e hospital, ambos `NUMBER` | Confere existência nas tabelas. Usada pelo registro e pela totalização depois da validação dos IDs. |

Como essas rotinas só são declaradas no corpo, não fazem parte da API pública do package.

## 6. Procedure adaptadora independente

`PR_REGISTRAR_CONSUMO` mantém os quatro parâmetros `IN NUMBER, IN NUMBER, IN DATE, IN NUMBER` usados anteriormente pelo Java. Ela chama a procedure do package e recebe o novo parâmetro de saída em uma variável local.

**Valor agregado:** permite a migração da lógica para o package mantendo a integração que já está em uso. As regras ficam implementadas no package; a adaptadora apenas encaminha a chamada. O script `11` verifica essa dependência no dicionário Oracle.

## 7. Tabela `AUDITORIA_CONSUMO`

| Coluna | Tipo | Nulo? | Finalidade |
|---|---|---|---|
| `ID` | `NUMBER(19,0)` identity | Não | Chave primária do evento. |
| `HISTORICO_ID` | `NUMBER(19,0)` | Não | ID afetado: novo em INSERT/UPDATE, antigo em DELETE. |
| `OPERACAO` | `VARCHAR2(6 CHAR)` | Não | `INSERT`, `UPDATE` ou `DELETE`. |
| `ITEM_ID_ANTES` | `NUMBER(19,0)` | Sim | Item anterior. |
| `ITEM_ID_DEPOIS` | `NUMBER(19,0)` | Sim | Item posterior. |
| `HOSPITAL_ID_ANTES` | `NUMBER(19,0)` | Sim | Hospital anterior. |
| `HOSPITAL_ID_DEPOIS` | `NUMBER(19,0)` | Sim | Hospital posterior. |
| `REFERENCIA_ANTES` | `DATE` | Sim | Referência anterior. |
| `REFERENCIA_DEPOIS` | `DATE` | Sim | Referência posterior. |
| `QUANTIDADE_ANTES` | `NUMBER(10,0)` | Sim | Quantidade anterior. |
| `QUANTIDADE_DEPOIS` | `NUMBER(10,0)` | Sim | Quantidade posterior. |
| `USUARIO_BANCO` | `VARCHAR2(128 CHAR)` | Não | `SYS_CONTEXT('USERENV','SESSION_USER')`. |
| `CLIENT_IDENTIFIER` | `VARCHAR2(64 CHAR)` | Sim | Identificador da sessão, caso tenha sido definido pela aplicação. |
| `MODULO` | `VARCHAR2(64 CHAR)` | Sim | Módulo informado pela ferramenta ou aplicação. |
| `REGISTRADO_EM` | `TIMESTAMP(6) WITH TIME ZONE` | Não | Data/hora e fuso, com default `SYSTIMESTAMP`. |

Restrições explícitas: `PK_AUDITORIA_CONSUMO`, para a chave primária; `CK_AUD_CONSUMO_OPERACAO`, para limitar as operações; e os `NOT NULL` indicados acima. A identidade e o índice de suporte à chave primária são administrados pelo Oracle.

Os IDs armazenados são referências históricas por valor. Não há FKs para histórico, item ou hospital, permitindo preservar os registros depois de exclusões confirmadas. A tabela é um registro transacional da aplicação, não um mecanismo independente de auditoria administrativa ou de proteção contra alterações pelo dono do schema.

## 8. Índice `IX_AUD_CONSUMO_HISTORICO`

Índice composto sobre `(HISTORICO_ID, ID)`. Apoia a recuperação dos eventos de um histórico e sua ordenação pelo identificador do evento:

```sql
SELECT * FROM AUDITORIA_CONSUMO
WHERE HISTORICO_ID = 221
ORDER BY ID;
```

Substitua `221` por um ID consultado no seu banco. A ordem de IDs indica alocação de identificadores; não deve ser interpretada como ordem de commit entre transações concorrentes.

## 9. Trigger `TRG_AUD_HISTORICO_CONSUMO`

Trigger `AFTER INSERT OR UPDATE OR DELETE ON HISTORICO_CONSUMO FOR EACH ROW`. Usa `INSERTING` e `UPDATING` para identificar a operação e `:OLD`/`:NEW` para os dados. Grava diretamente na tabela de auditoria sem consultar a tabela que está sendo alterada.

| Evento | Campos anteriores | Campos posteriores |
|---|---|---|
| INSERT | Nulos | Dados inseridos. |
| UPDATE | Dados antes da alteração. | Dados após a alteração. |
| DELETE | Dados excluídos. | Nulos. |

Um `UPDATE` que reatribua os mesmos valores também gera um evento. A auditoria captura os campos de negócio anteriores e posteriores; o campo `HISTORICO_ID` usa a chave posterior em uma atualização. A API atual não altera essa chave.

O log e o histórico pertencem à mesma transação. Uma falha na trigger impede a operação de origem; um rollback remove seus logs. O código não usa transação autônoma, não faz commit e não oculta erros de auditoria.

**Valor agregado:** permite rastrear correções e exclusões de consumo hospitalar independentemente do cliente que executou o DML. Não cria logs retroativos e não identifica automaticamente o usuário do JWT. Na integração atual, o usuário do banco normalmente será `MEDISTOCK`; `CLIENT_IDENTIFIER` poderá ser nulo.

## 10. Catálogo de erros

| Código | Significado |
|---|---|
| `-20001` | ID ausente, não positivo ou fracionário; na consulta dinâmica, hospital informado inválido. |
| `-20002` | Quantidade ausente, negativa, fracionária ou superior ao limite de `Integer`. |
| `-20003` | Data de referência ausente no registro. |
| `-20004` | Item inexistente no registro ou na totalização. |
| `-20005` | Hospital inexistente no registro ou na totalização. |
| `-20006` | Intervalo de datas invertido na totalização. |
| `-20007` | Nível de estoque não permitido. |
| `-20008` | Coluna de ordenação não permitida. |
| `-20009` | Direção de ordenação não permitida. |
| `-20010` | Nome do filtro excede 150 caracteres. |
| `-20090` | Conexão ou container incorreto para o script. |
| `-20091` | Tentativa de recriar a tabela de auditoria já existente. |
| `-20093` | Verificação de compilação, habilitação ou dependência falhou. |
| `-20094` | Tabelas ou dados prévios necessários estão ausentes. |
| `-20990` | Uma asserção do roteiro de testes falhou. |

Os códigos a partir de `-20090` acima pertencem aos scripts de instalação/validação, não às rotinas de negócio públicas. Erros de constraints e outros erros Oracle podem ser propagados normalmente.

## 11. Reprodução e comprovação

Consulte o [README](../README.md#execucao) para instalação e [PLSQL_EVIDENCIAS.md](PLSQL_EVIDENCIAS.md) para os resultados a registrar. Os testes usam os dados existentes, validam inserções e rejeições e desfazem suas próprias alterações; o teste pela API produz uma evidência persistida separada.

Documentação oficial: [Packages](https://docs.oracle.com/en/database/oracle/oracle-database/26/lnpls/plsql-packages.html), [Dynamic SQL](https://docs.oracle.com/en/database/oracle/oracle-database/26/lnpls/native-dynamic-sql.html), [CREATE TRIGGER](https://docs.oracle.com/en/database/oracle/oracle-database/26/lnpls/CREATE-TRIGGER-statement.html) e [restrições de triggers](https://docs.oracle.com/en/database/oracle/oracle-database/26/lnpls/trigger-restrictions.html).
