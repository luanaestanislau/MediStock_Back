-- Execute como MEDISTOCK / FREEPDB1 com F5. Somente consultas.
-- As linhas aparecem em Saida do Script, pelo DBMS_OUTPUT.
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

    PROCEDURE mostrar (
        p_titulo VARCHAR2,
        p_hospital NUMBER DEFAULT NULL,
        p_nome VARCHAR2 DEFAULT NULL,
        p_nivel VARCHAR2 DEFAULT NULL,
        p_coluna VARCHAR2 DEFAULT 'NOME',
        p_direcao VARCHAR2 DEFAULT 'ASC'
    ) IS
        v_cursor SYS_REFCURSOR;
        v_id NUMBER;
        v_nome VARCHAR2(150 CHAR);
        v_hospital_id NUMBER;
        v_hospital_nome VARCHAR2(150 CHAR);
        v_atual NUMBER;
        v_minimo NUMBER;
        v_nivel VARCHAR2(10);
        v_validade DATE;
        v_linhas NUMBER := 0;
    BEGIN
        DBMS_OUTPUT.PUT_LINE(CHR(10) || p_titulo);
        PKG_MEDISTOCK.PR_CONSULTAR_ESTOQUE(
            p_resultado => v_cursor, p_hospital_id => p_hospital,
            p_nome => p_nome, p_nivel => p_nivel,
            p_ordenar_por => p_coluna, p_direcao => p_direcao);
        LOOP
            FETCH v_cursor INTO v_id, v_nome, v_hospital_id, v_hospital_nome,
                                v_atual, v_minimo, v_nivel, v_validade;
            EXIT WHEN v_cursor%NOTFOUND;
            DBMS_OUTPUT.PUT_LINE('Item=' || v_id || ' | ' || v_nome ||
                ' | Hospital=' || v_hospital_id || ' (' || v_hospital_nome || ')' ||
                ' | Atual=' || v_atual || ' | Minimo=' || v_minimo ||
                ' | Nivel=' || v_nivel || ' | Validade=' ||
                NVL(TO_CHAR(v_validade, 'YYYY-MM-DD'), 'sem data'));
            v_linhas := v_linhas + 1;
        END LOOP;
        CLOSE v_cursor;
        DBMS_OUTPUT.PUT_LINE('Linhas retornadas: ' || v_linhas);
    EXCEPTION WHEN OTHERS THEN
        IF v_cursor%ISOPEN THEN CLOSE v_cursor; END IF;
        RAISE;
    END;
BEGIN
    SELECT MIN(ID) INTO v_item FROM ITENS_ESTOQUE;
    IF v_item IS NULL THEN
        RAISE_APPLICATION_ERROR(-20094, 'Cadastre um item para a demonstracao.');
    END IF;
    SELECT HOSPITAL_ID INTO v_hospital FROM ITENS_ESTOQUE WHERE ID = v_item;

    mostrar('DEMO 1: todos os itens, ordenados pelo nome (ASC).');
    mostrar('DEMO 2: hospital ' || v_hospital || ', maior quantidade primeiro.',
            p_hospital => v_hospital, p_coluna => 'QUANTIDADE_ATUAL', p_direcao => 'DESC');
    mostrar('DEMO 3: apenas estoque CRITICO, por validade (ASC).',
            p_nivel => 'CRITICO', p_coluna => 'VALIDADE');

    DBMS_OUTPUT.PUT_LINE(CHR(10) || 'FUNCTION: total registrado para item ' || v_item ||
        ', hospital ' || v_hospital || ' = ' || PKG_MEDISTOCK.FN_TOTAL_CONSUMO(v_item, v_hospital));
    DBMS_OUTPUT.PUT_LINE('Consultas concluídas. Nenhum dado foi alterado.');
END;
/
