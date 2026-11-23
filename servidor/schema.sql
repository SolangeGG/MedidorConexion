-- Tabla de mediciones del TFI (sección 5.6).
-- Los campos específicos de cada tipo de conexión quedan en NULL cuando no corresponden.
CREATE TABLE IF NOT EXISTS mediciones (
    id             SERIAL PRIMARY KEY,
    fecha_hora     TIMESTAMPTZ      NOT NULL,
    marca          TEXT             NOT NULL,
    modelo         TEXT             NOT NULL,
    tipo_conexion  TEXT             NOT NULL CHECK (tipo_conexion IN ('WIFI', 'MOVIL')),
    latitud        DOUBLE PRECISION,
    longitud       DOUBLE PRECISION,
    descarga_mbps  NUMERIC(10, 2)   NOT NULL,
    subida_mbps    NUMERIC(10, 2)   NOT NULL,
    ping_ms        NUMERIC(10, 2)   NOT NULL,
    jitter_ms      NUMERIC(10, 2)   NOT NULL,
    -- Solo Wi-Fi
    ssid           TEXT,
    banda          TEXT,
    proveedor      TEXT,
    rssi_dbm       INTEGER,
    enlace_mbps    INTEGER,
    distancia_m    NUMERIC(6, 1),
    -- Solo datos móviles
    operador       TEXT,
    red_movil      TEXT,
    rsrp_dbm       INTEGER,
    snr_db         INTEGER,
    -- Identificador del celular que hizo la medición (cada celular ve solo las suyas)
    dispositivo_id TEXT
);

-- Para bases creadas antes de existir esta columna
ALTER TABLE mediciones ADD COLUMN IF NOT EXISTS dispositivo_id TEXT;

CREATE INDEX IF NOT EXISTS idx_mediciones_fecha ON mediciones (fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_mediciones_tipo ON mediciones (tipo_conexion);
CREATE INDEX IF NOT EXISTS idx_mediciones_dispositivo ON mediciones (dispositivo_id);
