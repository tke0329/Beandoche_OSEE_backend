package com.beandoche_osee_backend.domain.avatar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.beandoche_osee_backend.domain.avatar.AvatarInputDtos.Measurements;

@Repository
public class AvatarInputStore {
    private final JdbcTemplate jdbc;

    public AvatarInputStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Body> body(long userId) {
        return jdbc.query("select * from body_profile where user_id=?", (rs, row) ->
                new Body(rs.getLong("body_version"), rs.getString("gender"), measurements(rs),
                        rs.getTimestamp("updated_at").toInstant()), userId).stream().findFirst();
    }

    public void saveBody(long userId, Body body) {
        Measurements m = body.measurements();
        jdbc.update("""
                insert into body_profile(user_id,body_version,gender,height_mm,weight_kg,chest_circumference_mm,
                    waist_circumference_mm,hip_circumference_mm,thigh_circumference_mm,inseam_mm,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?) on conflict(user_id) do update set
                    body_version=excluded.body_version,gender=excluded.gender,height_mm=excluded.height_mm,
                    weight_kg=excluded.weight_kg,chest_circumference_mm=excluded.chest_circumference_mm,
                    waist_circumference_mm=excluded.waist_circumference_mm,hip_circumference_mm=excluded.hip_circumference_mm,
                    thigh_circumference_mm=excluded.thigh_circumference_mm,inseam_mm=excluded.inseam_mm,updated_at=excluded.updated_at
                """, userId, body.version(), body.gender(), m.heightMm(), m.weightKg(), m.chestCircumferenceMm(),
                m.waistCircumferenceMm(), m.hipCircumferenceMm(), m.thighCircumferenceMm(), m.inseamMm(), Timestamp.from(body.updatedAt()));
    }

    public void createGeneration(long userId, UUID id, Body body) {
        Measurements m = body.measurements();
        jdbc.update("""
                insert into avatar_generation_request(id,user_id,body_version,input_schema_version,status,
                    gender,height_mm,weight_kg,chest_circumference_mm,waist_circumference_mm,
                    hip_circumference_mm,thigh_circumference_mm,inseam_mm,created_at,updated_at)
                values (?,?,?,1,'PENDING',?,?,?,?,?,?,?,?,?,?)
                """, id, userId, body.version(), body.gender(), m.heightMm(), m.weightKg(), m.chestCircumferenceMm(),
                m.waistCircumferenceMm(), m.hipCircumferenceMm(), m.thighCircumferenceMm(), m.inseamMm(),
                Timestamp.from(body.updatedAt()), Timestamp.from(body.updatedAt()));
    }

    public Optional<Generation> generation(long userId, UUID id) {
        return jdbc.query("select * from avatar_generation_request where user_id=? and id=?",
                (rs, row) -> generation(rs), userId, id).stream().findFirst();
    }

    public Optional<Generation> latestGeneration(long userId) {
        return jdbc.query("select * from avatar_generation_request where user_id=? order by body_version desc limit 1",
                (rs, row) -> generation(rs), userId).stream().findFirst();
    }

    public Optional<Appearance> appearance(long userId) {
        return jdbc.query("select skin_color_hex,appearance_version from avatar_profile where user_id=?",
                (rs, row) -> new Appearance(rs.getString("skin_color_hex"), rs.getLong("appearance_version")), userId)
                .stream().findFirst();
    }

    public void saveAppearance(long userId, String color, Instant now) {
        jdbc.update("""
                insert into avatar_profile(user_id,skin_color_hex,appearance_version,updated_at) values (?,?,1,?)
                on conflict(user_id) do update set skin_color_hex=excluded.skin_color_hex,
                    appearance_version=avatar_profile.appearance_version+1,updated_at=excluded.updated_at
                where avatar_profile.skin_color_hex<>excluded.skin_color_hex
                """, userId, color, Timestamp.from(now));
    }

    public void deleteBody(long userId) {
        jdbc.update("delete from avatar_generation_request where user_id=?", userId);
        jdbc.update("delete from body_profile where user_id=?", userId);
    }

    public void deleteUserData(long userId) {
        deleteBody(userId);
        jdbc.update("delete from avatar_profile where user_id=?", userId);
    }

    private static Measurements measurements(ResultSet rs) throws SQLException {
        return new Measurements(rs.getInt("height_mm"), rs.getBigDecimal("weight_kg").stripTrailingZeros(),
                rs.getInt("chest_circumference_mm"), rs.getInt("waist_circumference_mm"),
                rs.getInt("hip_circumference_mm"), rs.getInt("thigh_circumference_mm"), rs.getInt("inseam_mm"));
    }

    private static Generation generation(ResultSet rs) throws SQLException {
        return new Generation(rs.getObject("id", UUID.class), rs.getLong("body_version"),
                rs.getInt("input_schema_version"), rs.getString("status"), rs.getString("gender"), measurements(rs));
    }

    public record Body(long version, String gender, Measurements measurements, Instant updatedAt) { }
    public record Generation(UUID id, long bodyVersion, int schemaVersion, String status, String gender, Measurements measurements) { }
    public record Appearance(String color, long version) { }
}
