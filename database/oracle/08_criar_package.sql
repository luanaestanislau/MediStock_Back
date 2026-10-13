-- Execute como MEDISTOCK / FREEPDB1, usando F5. Pode ser reexecutado.
SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

BEGIN
    IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'MEDISTOCK'
       OR SYS_CONTEXT('USERENV', 'CON_NAME') <> 'FREEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20090, 'Use MEDISTOCK no FREEPDB1.');
    END IF;
END;
/

CREATE OR REPLACE PACKAGE PKG_MEDISTOCK AUTHID DEFINER AS
    -- Registra a demanda e devolve o ID criado. Nao altera saldo de estoque.
    PROCEDURE PR_REGISTRAR_CONSUMO (
        p_item_estoque_id IN NUMBER,
        p_hospital_id IN NUMBER,
        p_mes_referencia IN DATE,
        p_quantidade_consumida IN NUMBER,
        p_historico_id OUT NUMBER
    );

    -- Soma por item/hospital, com intervalo opcional de dias inclusivos.
    FUNCTION FN_TOTAL_CONSUMO (
        p_item_estoque_id IN NUMBER,
        p_hospital_id IN NUMBER,
        p_data_inicio IN DATE DEFAULT NULL,
        p_data_fim IN DATE DEFAULT NULL
    ) RETURN NUMBER;

    -- O chamador deve consumir e fechar o SYS_REFCURSOR retornado.
    PROCEDURE PR_CONSULTAR_ESTOQUE (
        p_resultado OUT SYS_REFCURSOR,
        p_hospital_id IN NUMBER DEFAULT NULL,
        p_nome IN VARCHAR2 DEFAULT NULL,
        p_nivel IN VARCHAR2 DEFAULT NULL,
        p_ordenar_por IN VARCHAR2 DEFAULT 'NOME',
        p_direcao IN VARCHAR2 DEFAULT 'ASC'
    );
END PKG_MEDISTOCK;
/
SHOW ERRORS PACKAGE PKG_MEDISTOCK

CREATE OR REPLACE PACKAGE BODY PKG_MEDISTOCK AS
    -- Rotinas privadas: reutilizadas pela procedure de consumo e pela function.
    PROCEDURE PR_VALIDAR_IDS (
        p_item_estoque_id IN NUMBER,
        p_hospital_id IN NUMBER
    ) IS BEGIN
        IF p_item_estoque_id IS NULL OR p_item_estoque_id <= 0
           OR p_item_estoque_id <> TRUNC(p_item_estoque_id)
           OR p_hospital_id IS NULL OR p_hospital_id <= 0
           OR p_hospital_id <> TRUNC(p_hospital_id) THEN
            RAISE_APPLICATION_ERROR(-20001, 'Informe IDs inteiros positivos.');
        END IF;
    END PR_VALIDAR_IDS;

    PROCEDURE PR_VALIDAR_REFERENCIAS (
        p_item_estoque_id IN NUMBER,
        p_hospital_id IN NUMBER
    ) IS
        v_existe NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM ITENS_ESTOQUE WHERE ID = p_item_estoque_id;
        IF v_existe = 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'Item de estoque nao encontrado.');
        END IF;
        SELECT COUNT(*) INTO v_existe FROM HOSPITAIS WHERE ID = p_hospital_id;
        IF v_existe = 0 THEN
            RAISE_APPLICATION_ERROR(-20005, 'Hospital nao encontrado.');
        END IF;
    END PR_VALIDAR_REFERENCIAS;

    PROCEDURE PR_REGISTRAR_CONSUMO (
        p_item_estoque_id IN NUMBER,
        p_hospital_id IN NUMBER,
        p_mes_referencia IN DATE,
        p_quantidade_consumida IN NUMBER,
        p_historico_id OUT NUMBER
    ) IS
    BEGIN
        -- Mesmas regras da procedure anterior; o novo OUT facilita o reuso.
        PR_VALIDAR_IDS(p_item_estoque_id, p_hospital_id);
        IF p_quantidade_consumida IS NULL OR p_quantidade_consumida < 0
           OR p_quantidade_consumida > 2147483647
           OR p_quantidade_consumida <> TRUNC(p_quantidade_consumida) THEN
            RAISE_APPLICATION_ERROR(-20002,
                'Quantidade deve ser um inteiro nao negativo compativel com Java Integer.');
        END IF;
        IF p_mes_referencia IS NULL THEN
            RAISE_APPLICATION_ERROR(-20003, 'Informe a data de referencia.');
        END IF;
        PR_VALIDAR_REFERENCIAS(p_item_estoque_id, p_hospital_id);

        INSERT INTO HISTORICO_CONSUMO
            (ITEM_ESTOQUE_ID, HOSPITAL_ID, MES_REFERENCIA, QUANTIDADE_CONSUMIDA)
        VALUES
            (p_item_estoque_id, p_hospital_id, p_mes_referencia, p_quantidade_consumida)
        RETURNING ID INTO p_historico_id;
    END PR_REGISTRAR_CONSUMO;

    FUNCTION FN_TOTAL_CONSUMO (
        p_item_estoque_id IN NUMBER,
        p_hospital_id IN NUMBER,
        p_data_inicio IN DATE DEFAULT NULL,
        p_data_fim IN DATE DEFAULT NULL
    ) RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        PR_VALIDAR_IDS(p_item_estoque_id, p_hospital_id);
        PR_VALIDAR_REFERENCIAS(p_item_estoque_id, p_hospital_id);
        IF p_data_inicio IS NOT NULL AND p_data_fim IS NOT NULL
           AND TRUNC(p_data_fim) < TRUNC(p_data_inicio) THEN
            RAISE_APPLICATION_ERROR(-20006, 'Data final deve ser igual ou posterior a data inicial.');
        END IF;
        SELECT NVL(SUM(h.QUANTIDADE_CONSUMIDA), 0) INTO v_total
        FROM HISTORICO_CONSUMO h
        WHERE h.ITEM_ESTOQUE_ID = p_item_estoque_id
          AND h.HOSPITAL_ID = p_hospital_id
          AND (p_data_inicio IS NULL OR TRUNC(h.MES_REFERENCIA) >= TRUNC(p_data_inicio))
          AND (p_data_fim IS NULL OR TRUNC(h.MES_REFERENCIA) <= TRUNC(p_data_fim));
        RETURN v_total;
    END FN_TOTAL_CONSUMO;

    PROCEDURE PR_CONSULTAR_ESTOQUE (
        p_resultado OUT SYS_REFCURSOR,
        p_hospital_id IN NUMBER DEFAULT NULL,
        p_nome IN VARCHAR2 DEFAULT NULL,
        p_nivel IN VARCHAR2 DEFAULT NULL,
        p_ordenar_por IN VARCHAR2 DEFAULT 'NOME',
        p_direcao IN VARCHAR2 DEFAULT 'ASC'
    ) IS
        v_sql VARCHAR2(32767);
        v_nome VARCHAR2(150 CHAR);
        v_nivel VARCHAR2(10 CHAR);
        v_coluna VARCHAR2(40);
        v_direcao VARCHAR2(4);
    BEGIN
        IF p_hospital_id IS NOT NULL
           AND (p_hospital_id <= 0 OR p_hospital_id <> TRUNC(p_hospital_id)) THEN
            RAISE_APPLICATION_ERROR(-20001, 'Hospital deve ser um ID inteiro positivo.');
        END IF;
        IF LENGTH(TRIM(p_nome)) > 150 THEN
            RAISE_APPLICATION_ERROR(-20010, 'O filtro nome aceita ate 150 caracteres.');
        END IF;
        v_nome := TRIM(p_nome);

        IF TRIM(p_nivel) IS NOT NULL
           AND UPPER(TRIM(p_nivel)) NOT IN ('CRITICO', 'ATENCAO', 'NORMAL') THEN
            RAISE_APPLICATION_ERROR(-20007, 'Nivel permitido: CRITICO, ATENCAO ou NORMAL.');
        END IF;
        v_nivel := UPPER(TRIM(p_nivel));

        -- Identificadores SQL nao sao bind variables: mapear para constantes.
        CASE UPPER(NVL(TRIM(p_ordenar_por), 'NOME'))
            WHEN 'ID' THEN v_coluna := 'e.ID';
            WHEN 'NOME' THEN v_coluna := 'e.NOME';
            WHEN 'QUANTIDADE_ATUAL' THEN v_coluna := 'e.QUANTIDADE_ATUAL';
            WHEN 'VALIDADE' THEN v_coluna := 'e.VALIDADE';
            ELSE RAISE_APPLICATION_ERROR(-20008,
                'Ordenacao permitida: ID, NOME, QUANTIDADE_ATUAL ou VALIDADE.');
        END CASE;
        CASE UPPER(NVL(TRIM(p_direcao), 'ASC'))
            WHEN 'ASC' THEN v_direcao := 'ASC';
            WHEN 'DESC' THEN v_direcao := 'DESC';
            ELSE RAISE_APPLICATION_ERROR(-20009, 'Direcao permitida: ASC ou DESC.');
        END CASE;

        -- Tres placeholders fixos na CTE permitem qualquer combinacao de filtros.
        -- Dados entram exclusivamente pelo USING; a estrutura varia em tempo de execucao.
        v_sql := q'[
            WITH parametros AS (
                SELECT CAST(:b_hospital AS NUMBER) AS hospital_id,
                       CAST(:b_nome AS VARCHAR2(150 CHAR)) AS nome,
                       CAST(:b_nivel AS VARCHAR2(10 CHAR)) AS nivel
                FROM dual
            )
            SELECT e.ID AS ITEM_ESTOQUE_ID, e.NOME, e.HOSPITAL_ID,
                   h.NOME AS HOSPITAL_NOME, e.QUANTIDADE_ATUAL, e.QUANTIDADE_MINIMA,
                   CASE WHEN e.QUANTIDADE_ATUAL <= e.QUANTIDADE_MINIMA THEN 'CRITICO'
                        WHEN e.QUANTIDADE_ATUAL <= e.QUANTIDADE_MINIMA * 1.5 THEN 'ATENCAO'
                        ELSE 'NORMAL' END AS NIVEL_ESTOQUE,
                   e.VALIDADE
            FROM ITENS_ESTOQUE e
            JOIN HOSPITAIS h ON h.ID = e.HOSPITAL_ID
            CROSS JOIN parametros p
            WHERE 1 = 1]';

        IF p_hospital_id IS NOT NULL THEN
            v_sql := v_sql || ' AND e.HOSPITAL_ID = p.hospital_id';
        END IF;
        IF v_nome IS NOT NULL THEN
            v_sql := v_sql || ' AND INSTR(UPPER(e.NOME), UPPER(p.nome)) > 0';
        END IF;
        IF v_nivel IS NOT NULL THEN
            v_sql := v_sql || q'[ AND
                CASE WHEN e.QUANTIDADE_ATUAL <= e.QUANTIDADE_MINIMA THEN 'CRITICO'
                     WHEN e.QUANTIDADE_ATUAL <= e.QUANTIDADE_MINIMA * 1.5 THEN 'ATENCAO'
                     ELSE 'NORMAL' END = p.nivel]';
        END IF;
        v_sql := v_sql || ' ORDER BY ' || v_coluna || ' ' || v_direcao || ' NULLS LAST';
        IF v_coluna <> 'e.ID' THEN
            v_sql := v_sql || ', e.ID ASC';
        END IF;

        OPEN p_resultado FOR v_sql USING p_hospital_id, v_nome, v_nivel;
    END PR_CONSULTAR_ESTOQUE;
END PKG_MEDISTOCK;
/
SHOW ERRORS PACKAGE BODY PKG_MEDISTOCK

DECLARE
    v_validos NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_validos FROM USER_OBJECTS
    WHERE OBJECT_NAME = 'PKG_MEDISTOCK'
      AND OBJECT_TYPE IN ('PACKAGE', 'PACKAGE BODY') AND STATUS = 'VALID';
    IF v_validos <> 2 THEN
        RAISE_APPLICATION_ERROR(-20093, 'Package invalido. Consulte USER_ERRORS ou execute o script 11.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('OK: PACKAGE e PACKAGE BODY estao VALID.');
END;
/
