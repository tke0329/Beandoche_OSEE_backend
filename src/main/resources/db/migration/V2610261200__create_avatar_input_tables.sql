CREATE TABLE body_profile (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    body_version BIGINT NOT NULL CHECK (body_version > 0),
    gender VARCHAR(32) CHECK (gender IS NULL OR gender ~ '^[A-Z][A-Z0-9_-]{0,31}$'),
    height_mm INTEGER NOT NULL CHECK (height_mm > 0),
    weight_kg NUMERIC(8,3) NOT NULL CHECK (weight_kg > 0),
    chest_circumference_mm INTEGER NOT NULL CHECK (chest_circumference_mm > 0),
    waist_circumference_mm INTEGER NOT NULL CHECK (waist_circumference_mm > 0),
    hip_circumference_mm INTEGER NOT NULL CHECK (hip_circumference_mm > 0),
    thigh_circumference_mm INTEGER NOT NULL CHECK (thigh_circumference_mm > 0),
    inseam_mm INTEGER NOT NULL CHECK (inseam_mm > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE avatar_profile (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    skin_color_hex VARCHAR(7) NOT NULL CHECK (skin_color_hex ~ '^#[0-9A-F]{6}$'),
    appearance_version BIGINT NOT NULL CHECK (appearance_version > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE avatar_generation_request (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body_version BIGINT NOT NULL CHECK (body_version > 0),
    input_schema_version INTEGER NOT NULL CHECK (input_schema_version = 1),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    gender VARCHAR(32) CHECK (gender IS NULL OR gender ~ '^[A-Z][A-Z0-9_-]{0,31}$'),
    height_mm INTEGER NOT NULL CHECK (height_mm > 0),
    weight_kg NUMERIC(8,3) NOT NULL CHECK (weight_kg > 0),
    chest_circumference_mm INTEGER NOT NULL CHECK (chest_circumference_mm > 0),
    waist_circumference_mm INTEGER NOT NULL CHECK (waist_circumference_mm > 0),
    hip_circumference_mm INTEGER NOT NULL CHECK (hip_circumference_mm > 0),
    thigh_circumference_mm INTEGER NOT NULL CHECK (thigh_circumference_mm > 0),
    inseam_mm INTEGER NOT NULL CHECK (inseam_mm > 0),
    model_asset_key VARCHAR(300),
    model_version VARCHAR(64),
    model_file_format VARCHAR(8) CHECK (model_file_format IS NULL OR model_file_format IN ('GLB','GLTF')),
    model_size_bytes BIGINT CHECK (model_size_bytes IS NULL OR model_size_bytes > 0),
    failure_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_avatar_generation_body_version UNIQUE (user_id, body_version),
    CONSTRAINT ck_avatar_completed_metadata CHECK (status <> 'COMPLETED' OR (
        model_asset_key IS NOT NULL AND length(trim(model_asset_key)) > 0
        AND model_version IS NOT NULL AND length(trim(model_version)) > 0
        AND model_file_format IS NOT NULL AND model_size_bytes IS NOT NULL)),
    CONSTRAINT ck_avatar_failed_code CHECK (status <> 'FAILED' OR (
        failure_code IS NOT NULL AND length(trim(failure_code)) > 0))
);
