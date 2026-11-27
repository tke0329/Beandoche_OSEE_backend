ALTER TABLE body_profile
    ALTER COLUMN inseam_mm DROP NOT NULL,
    ADD COLUMN underbust_circumference_mm INTEGER CHECK (
        underbust_circumference_mm > 0 AND underbust_circumference_mm < chest_circumference_mm
    );

ALTER TABLE avatar_generation_request
    ALTER COLUMN inseam_mm DROP NOT NULL,
    ADD COLUMN underbust_circumference_mm INTEGER CHECK (
        underbust_circumference_mm > 0 AND underbust_circumference_mm < chest_circumference_mm
    );
