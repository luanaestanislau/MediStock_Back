-- Execute como MEDISTOCK / FREEPDB1, com F5, apos o script 08.
-- Preserva a assinatura de quatro parametros que a API ja utiliza.
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

CREATE OR REPLACE PROCEDURE PR_REGISTRAR_CONSUMO (
    p_item_estoque_id IN NUMBER,
    p_hospital_id IN NUMBER,
    p_mes_referencia IN DATE,
    p_quantidade_consumida IN NUMBER
) AUTHID DEFINER AS
    v_historico_id NUMBER;
BEGIN
    PKG_MEDISTOCK.PR_REGISTRAR_CONSUMO(
        p_item_estoque_id => p_item_estoque_id,
        p_hospital_id => p_hospital_id,
        p_mes_referencia => p_mes_referencia,
        p_quantidade_consumida => p_quantidade_consumida,
        p_historico_id => v_historico_id
    );
END PR_REGISTRAR_CONSUMO;
/
SHOW ERRORS PROCEDURE PR_REGISTRAR_CONSUMO

DECLARE
    v_validos NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_validos FROM USER_OBJECTS
    WHERE OBJECT_NAME = 'PR_REGISTRAR_CONSUMO'
      AND OBJECT_TYPE = 'PROCEDURE' AND STATUS = 'VALID';
    IF v_validos <> 1 THEN
        RAISE_APPLICATION_ERROR(-20093, 'Procedure adaptadora invalida. Consulte USER_ERRORS.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('OK: procedure adaptadora VALID; chamada Java de quatro parametros preservada.');
END;
/
