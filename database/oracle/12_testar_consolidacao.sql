-- Execute como MEDISTOCK / FREEPDB1 com F5, apos o script 11.
-- Pause a API durante o teste para evitar alteracoes concorrentes nas contagens.
-- Precisa de pelo menos um item cadastrado. Usa IDs existentes, sem presumir ID=1.
-- Somente os dados criados neste teste sao desfeitos por ROLLBACK TO SAVEPOINT.
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

BEGIN
    IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'MEDISTOCK'
       OR SYS_CONTEXT('USERENV', 'CON_NAME') <> 'FREEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20090, 'Use MEDISTOCK no FREEPDB1.');
    END IF;
END;
/

DECLARE
    v_item NUMBER;
    v_hospital NUMBER;
    v_hospital_inexistente NUMBER;
    v_nome ITENS_ESTOQUE.NOME%TYPE;
    v_nivel VARCHAR2(10);
    v_hist_id NUMBER;
    v_hist_antes NUMBER;
    v_aud_antes NUMBER;
    v_total_antes NUMBER;
    v_total_sql NUMBER;
    v_contagem NUMBER;
    v_rejeitou BOOLEAN;
    v_cursor SYS_REFCURSOR;
    v_testes PLS_INTEGER := 0;

    PROCEDURE exigir(p_condicao BOOLEAN, p_mensagem VARCHAR2) IS
    BEGIN
        IF p_condicao IS NULL OR NOT p_condicao THEN
            RAISE_APPLICATION_ERROR(-20990, p_mensagem);
        END IF;
    END;

    PROCEDURE ok(p_mensagem VARCHAR2) IS
    BEGIN
        v_testes := v_testes + 1;
        DBMS_OUTPUT.PUT_LINE('OK ' || v_testes || ': ' || p_mensagem);
    END;

    PROCEDURE conferir_cursor (
        p_hospital NUMBER DEFAULT NULL,
        p_nome VARCHAR2 DEFAULT NULL,
        p_nivel VARCHAR2 DEFAULT NULL
    ) IS
        v_rc SYS_REFCURSOR;
        v_id NUMBER;
        v_nome_linha VARCHAR2(150 CHAR);
        v_hosp NUMBER;
        v_hosp_nome VARCHAR2(150 CHAR);
        v_atual NUMBER;
        v_minimo NUMBER;
        v_status VARCHAR2(10);
        v_validade DATE;
        v_anterior NUMBER;
        v_lidas NUMBER := 0;
        v_esperadas NUMBER;
    BEGIN
        PKG_MEDISTOCK.PR_CONSULTAR_ESTOQUE(
            v_rc, p_hospital, p_nome, p_nivel, 'QUANTIDADE_ATUAL', 'DESC');
        LOOP
            FETCH v_rc INTO v_id, v_nome_linha, v_hosp, v_hosp_nome,
                            v_atual, v_minimo, v_status, v_validade;
            EXIT WHEN v_rc%NOTFOUND;
            exigir(p_hospital IS NULL OR v_hosp = p_hospital, 'Cursor retornou outro hospital.');
            exigir(p_nome IS NULL OR INSTR(UPPER(v_nome_linha), UPPER(p_nome)) > 0,
                   'Cursor retornou nome fora do filtro.');
            exigir(p_nivel IS NULL OR v_status = p_nivel, 'Cursor retornou outro nivel.');
            exigir(v_anterior IS NULL OR v_anterior >= v_atual, 'Cursor fora da ordem DESC.');
            v_anterior := v_atual;
            v_lidas := v_lidas + 1;
        END LOOP;
        CLOSE v_rc;
        SELECT COUNT(*) INTO v_esperadas FROM ITENS_ESTOQUE e
        WHERE (p_hospital IS NULL OR e.HOSPITAL_ID = p_hospital)
          AND (p_nome IS NULL OR INSTR(UPPER(e.NOME), UPPER(p_nome)) > 0)
          AND (p_nivel IS NULL OR
               CASE WHEN e.QUANTIDADE_ATUAL <= e.QUANTIDADE_MINIMA THEN 'CRITICO'
                    WHEN e.QUANTIDADE_ATUAL <= e.QUANTIDADE_MINIMA * 1.5 THEN 'ATENCAO'
                    ELSE 'NORMAL' END = p_nivel);
        exigir(v_lidas = v_esperadas, 'Cursor omitiu ou acrescentou resultados.');
        ok('SQL dinamico: filtros e ordenacao conferidos; linhas=' || v_lidas || '.');
    EXCEPTION WHEN OTHERS THEN
        IF v_rc%ISOPEN THEN CLOSE v_rc; END IF;
        RAISE;
    END;
BEGIN
    SAVEPOINT teste_consolidacao;
    SELECT MIN(ID) INTO v_item FROM ITENS_ESTOQUE;
    exigir(v_item IS NOT NULL, 'Cadastre um item ou carregue os dados simulados antes do teste.');
    SELECT HOSPITAL_ID, NOME,
           CASE WHEN QUANTIDADE_ATUAL <= QUANTIDADE_MINIMA THEN 'CRITICO'
                WHEN QUANTIDADE_ATUAL <= QUANTIDADE_MINIMA * 1.5 THEN 'ATENCAO'
                ELSE 'NORMAL' END
    INTO v_hospital, v_nome, v_nivel FROM ITENS_ESTOQUE WHERE ID = v_item;
    SELECT NVL(MAX(ID), 0) + 1 INTO v_hospital_inexistente FROM HOSPITAIS;
    SELECT COUNT(*) INTO v_hist_antes FROM HISTORICO_CONSUMO;
    SELECT COUNT(*) INTO v_aud_antes FROM AUDITORIA_CONSUMO;
    SELECT NVL(SUM(QUANTIDADE_CONSUMIDA), 0) INTO v_total_sql
    FROM HISTORICO_CONSUMO WHERE ITEM_ESTOQUE_ID = v_item AND HOSPITAL_ID = v_hospital;
    v_total_antes := PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital);
    exigir(v_total_antes = v_total_sql, 'Function divergiu da soma inicial.');

    PKG_MEDISTOCK.PR_REGISTRAR_CONSUMO(v_item, v_hospital, TRUNC(SYSDATE, 'MM'), 7, v_hist_id);
    SELECT COUNT(*) INTO v_contagem FROM HISTORICO_CONSUMO
    WHERE ID = v_hist_id AND ITEM_ESTOQUE_ID = v_item AND HOSPITAL_ID = v_hospital
      AND MES_REFERENCIA = TRUNC(SYSDATE, 'MM') AND QUANTIDADE_CONSUMIDA = 7;
    exigir(v_contagem = 1, 'Package nao gravou o historico esperado ou nao retornou seu ID.');
    SELECT COUNT(*) INTO v_contagem FROM AUDITORIA_CONSUMO
    WHERE HISTORICO_ID = v_hist_id AND OPERACAO = 'INSERT'
      AND ITEM_ID_ANTES IS NULL AND ITEM_ID_DEPOIS = v_item
      AND HOSPITAL_ID_ANTES IS NULL AND HOSPITAL_ID_DEPOIS = v_hospital
      AND REFERENCIA_ANTES IS NULL AND REFERENCIA_DEPOIS = TRUNC(SYSDATE, 'MM')
      AND QUANTIDADE_ANTES IS NULL AND QUANTIDADE_DEPOIS = 7
      AND USUARIO_BANCO = 'MEDISTOCK';
    exigir(v_contagem = 1, 'Auditoria de INSERT ausente ou incorreta.');
    ok('Package inseriu consumo e trigger registrou INSERT com quantidade 7.');

    exigir(PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital) = v_total_antes + 7,
           'Function nao incluiu o novo consumo.');
    SELECT NVL(SUM(QUANTIDADE_CONSUMIDA), 0) INTO v_total_sql
    FROM HISTORICO_CONSUMO WHERE ITEM_ESTOQUE_ID = v_item AND HOSPITAL_ID = v_hospital
      AND TRUNC(MES_REFERENCIA) = TRUNC(SYSDATE, 'MM');
    exigir(PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital,
           TRUNC(SYSDATE, 'MM'), TRUNC(SYSDATE, 'MM')) = v_total_sql,
           'Function nao respeitou o intervalo inclusivo de um dia.');
    ok('Function calculou o total geral e o total no intervalo de datas.');

    UPDATE HISTORICO_CONSUMO SET QUANTIDADE_CONSUMIDA = 9 WHERE ID = v_hist_id;
    SELECT COUNT(*) INTO v_contagem FROM AUDITORIA_CONSUMO
    WHERE HISTORICO_ID = v_hist_id AND OPERACAO = 'UPDATE'
      AND QUANTIDADE_ANTES = 7 AND QUANTIDADE_DEPOIS = 9;
    exigir(v_contagem = 1, 'Auditoria de UPDATE nao preservou 7 -> 9.');
    exigir(PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital) = v_total_antes + 9,
           'Function nao refletiu UPDATE.');
    ok('Trigger registrou UPDATE, com quantidade anterior 7 e posterior 9.');

    DELETE FROM HISTORICO_CONSUMO WHERE ID = v_hist_id;
    SELECT COUNT(*) INTO v_contagem FROM HISTORICO_CONSUMO WHERE ID = v_hist_id;
    exigir(v_contagem = 0, 'DELETE nao removeu o historico de teste.');
    SELECT COUNT(*) INTO v_contagem FROM AUDITORIA_CONSUMO
    WHERE HISTORICO_ID = v_hist_id AND OPERACAO = 'DELETE'
      AND QUANTIDADE_ANTES = 9 AND QUANTIDADE_DEPOIS IS NULL;
    exigir(v_contagem = 1, 'Auditoria de DELETE ausente ou incorreta.');
    ok('Trigger registrou DELETE; log permanece mesmo sem a linha do historico.');
    FOR r IN (SELECT OPERACAO, QUANTIDADE_ANTES, QUANTIDADE_DEPOIS FROM AUDITORIA_CONSUMO
              WHERE HISTORICO_ID = v_hist_id ORDER BY ID) LOOP
        DBMS_OUTPUT.PUT_LINE('  Evento=' || r.OPERACAO || '; antes=' ||
            NVL(TO_CHAR(r.QUANTIDADE_ANTES), 'NULL') || '; depois=' ||
            NVL(TO_CHAR(r.QUANTIDADE_DEPOIS), 'NULL'));
    END LOOP;

    PR_REGISTRAR_CONSUMO(v_item, v_hospital, TRUNC(SYSDATE, 'MM'), 5);
    SELECT COUNT(*) INTO v_contagem FROM HISTORICO_CONSUMO;
    exigir(v_contagem = v_hist_antes + 1, 'Procedure adaptadora nao inseriu exatamente uma linha.');
    SELECT COUNT(*) INTO v_contagem FROM AUDITORIA_CONSUMO;
    exigir(v_contagem = v_aud_antes + 4, 'Procedure adaptadora nao produziu o quarto evento de auditoria.');
    exigir(PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital) = v_total_antes + 5,
           'Soma divergente apos a chamada de quatro parametros.');
    ok('Procedure antiga de quatro parametros continua funcionando e gerando auditoria.');

    v_rejeitou := FALSE;
    BEGIN
        PR_REGISTRAR_CONSUMO(v_item, v_hospital, SYSDATE, -1);
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE <> -20002 THEN RAISE; END IF;
        v_rejeitou := TRUE;
    END;
    exigir(v_rejeitou, 'Quantidade negativa deveria ser rejeitada.');
    ok('Quantidade negativa rejeitada com ORA-20002.');

    v_rejeitou := FALSE;
    BEGIN
        PR_REGISTRAR_CONSUMO(v_item, v_hospital_inexistente, SYSDATE, 1);
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE <> -20005 THEN RAISE; END IF;
        v_rejeitou := TRUE;
    END;
    exigir(v_rejeitou, 'Hospital inexistente deveria ser rejeitado.');
    SELECT COUNT(*) INTO v_contagem FROM HISTORICO_CONSUMO;
    exigir(v_contagem = v_hist_antes + 1, 'Chamada rejeitada alterou o historico.');
    SELECT COUNT(*) INTO v_contagem FROM AUDITORIA_CONSUMO;
    exigir(v_contagem = v_aud_antes + 4, 'Chamada rejeitada gerou evento de auditoria.');
    ok('Hospital inexistente rejeitado com ORA-20005; falhas nao criaram registros.');

    v_rejeitou := FALSE;
    BEGIN
        v_total_sql := PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital,
                         DATE '2026-10-31', DATE '2026-10-01');
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE <> -20006 THEN RAISE; END IF;
        v_rejeitou := TRUE;
    END;
    exigir(v_rejeitou, 'Intervalo invertido deveria ser rejeitado.');
    ok('Function rejeitou intervalo de datas invertido com ORA-20006.');

    conferir_cursor;
    conferir_cursor(v_hospital, SUBSTR(v_nome, 1, 5), v_nivel);
    conferir_cursor(NULL, q'[x' OR 1=1 --]', NULL);

    v_rejeitou := FALSE;
    BEGIN
        PKG_MEDISTOCK.PR_CONSULTAR_ESTOQUE(p_resultado => v_cursor,
            p_ordenar_por => 'NOME DESC; DELETE FROM HOSPITAIS');
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE <> -20008 THEN RAISE; END IF;
        v_rejeitou := TRUE;
    END;
    exigir(v_rejeitou, 'Coluna fora da lista permitida deveria ser rejeitada.');
    ok('Ordenacao fora da lista permitida rejeitada com ORA-20008.');

    v_rejeitou := FALSE;
    BEGIN
        PKG_MEDISTOCK.PR_CONSULTAR_ESTOQUE(p_resultado => v_cursor, p_direcao => 'INVALIDA');
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE <> -20009 THEN RAISE; END IF;
        v_rejeitou := TRUE;
    END;
    exigir(v_rejeitou, 'Direcao invalida deveria ser rejeitada.');
    ok('Direcao fora de ASC/DESC rejeitada com ORA-20009.');

    ROLLBACK TO teste_consolidacao;
    SELECT COUNT(*) INTO v_contagem FROM HISTORICO_CONSUMO;
    exigir(v_contagem = v_hist_antes, 'ROLLBACK nao restaurou o historico.');
    SELECT COUNT(*) INTO v_contagem FROM AUDITORIA_CONSUMO;
    exigir(v_contagem = v_aud_antes, 'ROLLBACK nao restaurou a auditoria.');
    exigir(PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital) = v_total_antes,
           'ROLLBACK nao restaurou o total de consumo.');
    ok('ROLLBACK restaurou historico, auditoria e total de consumo.');
    DBMS_OUTPUT.PUT_LINE('SUCESSO: ' || v_testes || ' verificacoes concluidas; dados de teste desfeitos.');
EXCEPTION WHEN OTHERS THEN
    IF v_cursor%ISOPEN THEN CLOSE v_cursor; END IF;
    ROLLBACK TO teste_consolidacao;
    RAISE;
END;
/
