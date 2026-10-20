CREATE TABLE user_profile (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    nickname VARCHAR(20) NOT NULL,
    nickname_key VARCHAR(64) NOT NULL,
    nickname_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    image_data BYTEA,
    CONSTRAINT uk_user_profile_nickname_key UNIQUE (nickname_key),
    CONSTRAINT ck_user_profile_image_size CHECK (image_data IS NULL OR octet_length(image_data) <= 1048576)
);
